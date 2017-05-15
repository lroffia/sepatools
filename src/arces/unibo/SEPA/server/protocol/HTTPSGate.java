/* This class implements part of the SPARQL 1.1 SE Protocol using the secure HTTPS protocol
 * 
 * Author: Luca Roffia (luca.roffia@unibo.it)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package arces.unibo.SEPA.server.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import java.util.List;

import org.apache.commons.io.IOUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsServer;

import arces.unibo.SEPA.commons.response.ErrorResponse;
import arces.unibo.SEPA.commons.response.Response;
import arces.unibo.SEPA.server.core.EngineProperties;
import arces.unibo.SEPA.server.scheduling.SchedulerInterface;
import arces.unibo.SEPA.server.security.AuthorizationManager;
import arces.unibo.SEPA.server.security.CORSManager;

public class HTTPSGate extends HTTPGate {
	protected Logger logger = LogManager.getLogger("HTTPSGate");	
	protected static String mBeanName = "arces.unibo.SEPA.server:type=HTTPSGate";
		
	//Authorization manager
	private static AuthorizationManager am = new AuthorizationManager("sepa.jks","*sepa.jks*","SepaKey","*SepaKey*","SepaCertificate");

	/*
	Error Code	Description (RFC 2616 Status codes) 
	
	400			Bad Request
	401			Unauthorized
	403			Forbidden
	404			Not Found
	405			Method Not Allowed
	429			Too Many Requests
	500			Internal Server Error
	503			Service Unavailable
	*/
	
	public HTTPSGate(EngineProperties properties, SchedulerInterface scheduler) {
		super(properties, scheduler);
		
		if (properties == null) {
			logger.fatal("Properties are null");
			System.exit(-1);
		}
	}
	
	class RegistrationHandler implements HttpHandler {

		/*
		 * Registration is done according [RFC7591] and described in the following.
		 * Create a HTTP request with JSON request content as in the following prototype and send it via TLS to the AM.
		 * 
		 * Request 
		 * POST HTTP/1.1
		 * 
		 * Request headers 
		 * Host: <URL> 
		 * Content-Type: application/json 
		 * Accept: application/json
		 * 
		 * Request body 
		 * { c
		 * "client_identity": "IDENTITY", 
		 * "grant_types": ["client_credentials"] 
		 * }
		 */
		
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			logger.info(">> HTTPS request (REGISTRATION)");
			
			if (CORSManager.processCORSPreFlightRequest(exchange)) return;
			
			if(!exchange.getRequestMethod().toUpperCase().equals("POST")) {
				logger.error("Bad request: "+exchange.getRequestMethod().toUpperCase());
				failureResponse(exchange,400,"Bad request: "+exchange.getRequestMethod().toUpperCase()+" Request must be a POST");
				return;
			}
			
			//Parsing and validating request headers
			// Content-Type: application/json 
			// Accept: application/json
			if (!exchange.getRequestHeaders().containsKey("Content-Type")) {
				logger.error("Bad request header: Content-Type is missing");
				failureResponse(exchange,400,"Bad request header: Content-Type is missing");
				return;
			}
			if (!exchange.getRequestHeaders().get("Content-Type").contains("application/json")) {
				logger.error("Bad request header: Content-Type must be \"application/json\"");
				failureResponse(exchange,400,"Bad request header: Content-Type must be \"application/json\"");
				return;	
			}
			
			if (!exchange.getRequestHeaders().containsKey("Accept")) {
				logger.error("Bad request header: Accept is missing");
				failureResponse(exchange,400,"Bad request header: Accept is missing");
				return;
			}
			if (!exchange.getRequestHeaders().get("Accept").contains("application/json")) {
				logger.error("Bad request header: Accept must be \"application/json\"");
				failureResponse(exchange,400,"Bad request header: Accept must be \"/application/json\"");
				return;	
			}
			
			//Parsing and validating request body
			/*{ 
				"client_identity": "IDENTITY", 
				"grant_types": ["client_credentials"] 
			}*/
			String jsonString = null;
			try {
				jsonString = IOUtils.toString(exchange.getRequestBody(),"UTF-8");
			} catch (IOException e) {
				logger.error("Exception on reading POST body: "+e.getMessage());
				failureResponse(exchange,400,"Exception on reading POST body");
				return;
			}
			JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
			if (json == null) {
				logger.error("Request body must be a JSON object");
				failureResponse(exchange,400,"Request body must be a JSON object");
				return;	
			}
			
			JsonArray credentials = json.get("grant_types").getAsJsonArray();
			if (credentials == null) {
				logger.error("Request body must contain: \"grant_types\"");
				failureResponse(exchange,400,"Request body must contain: \"grant_types\"");
				return;		
			}
			if (credentials.size() == 0) {
				logger.error("\"grant_types\" is null");
				failureResponse(exchange,400,"\"grant_types\" is null");
				return;		
			}
			boolean found = false;
			for (JsonElement elem: credentials){
				if (elem.getAsString() != null)
					if (elem.getAsString().equals("client_credentials")) {
						found = true;
						break;
					}
			}
			if(!found) {
				logger.error("\"grant_types\" must contain \"client_credentials\"");
				failureResponse(exchange,400,"\"grant_types\" must contain \"client_credentials\"");
				return;	
			}
			
			String name = json.get("client_identity").getAsString();
			if (name == null) {
				logger.error("JSON request body must contain: \"client_identity\"");
				failureResponse(exchange,400,"JSON request body must contain: \"client_identity\"");
				return;		
			}
			if (name.equals("")) {
				logger.error("\"client_identity\" is null");
				failureResponse(exchange,400,"\"client_identity\" is null");
				return;		
			}
			
			//*****************************************
			//Register client and retrieve credentials
			//*****************************************
			Response cred = am.register(name);
			
			if (cred.getClass().equals(ErrorResponse.class)) {
				logger.error(cred.toString());
				ErrorResponse error = (ErrorResponse) cred;
				failureResponse(exchange,error.getErrorCode(),error.getErrorMessage());
				return;
			}
			
			sendHTTPResponse(exchange,cred.toString());	
		}
		
		private void sendHTTPResponse(HttpExchange exchange,String body) {
			try {
				exchange.sendResponseHeaders(201, body.length());
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
			OutputStream os = exchange.getResponseBody();
			try {
				os.write(body.getBytes());
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
			try {
				os.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
			}	
		}
	}
	
	class TokenHandler implements HttpHandler {
		/*
		 * Token Acquisition
		 * Create a HTTP request as in the following prototype and send it via TLS to the AM.
		 * 
		 * Request 
		 * POST HTTP/1.1
		 * 
		 * Request headers 
		 * Host: <URL> 
		 * Content-Type: application/x-www-form-urlencoded 
		 * Accept: application/json 
		 * Authorization: Basic Base64(<c_id>:<c_secret>)
		 * 
		 * Request body 
		 * 
		 * { 
		 * "client_identity": "68:a8:6d:1a:9c:04", 
		 * "grant_types": ["client_credentials"] 
		 * }
		 * */
		
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			logger.info(">> HTTPS request (TOKEN REQUEST)");
			
			if (CORSManager.processCORSPreFlightRequest(exchange)) return;
			
			if(!exchange.getRequestMethod().toUpperCase().equals("POST")) {
				logger.error("Bad request: "+exchange.getRequestMethod().toUpperCase());
				failureResponse(exchange,400,"Request must be a POST");
				return;
			}	
			
			if (!exchange.getRequestHeaders().containsKey("Content-Type")) {
				logger.error("Content-Type is null");
				failureResponse(exchange,400,"Content-Type is null");
				return;
			}
			if (!exchange.getRequestHeaders().get("Content-Type").contains("application/x-www-form-urlencoded")) {
				logger.error("Content-Type must be \"application/x-www-form-urlencoded\"");
				failureResponse(exchange,400,"Content-Type must be \"application/x-www-form-urlencoded\"");
				return;	
			} 
			
			if (!exchange.getRequestHeaders().containsKey("Accept")) {
				logger.error("Accept is null");
				failureResponse(exchange,400,"Accept is null");
				return;
			}
			if (!exchange.getRequestHeaders().get("Accept").contains("application/json")) {
				logger.error("Accept must be \"application/json\"");
				failureResponse(exchange,400,"Accept must be \"/application/json\"");
				return;	
			}
			
			if (!exchange.getRequestHeaders().containsKey("Authorization")) {
				logger.error("Authorization is null");
				failureResponse(exchange,401,"Authorization is null");
				return;
			}
			
			//Extract Basic64 authorization
			List<String> basic = exchange.getRequestHeaders().get("Authorization");
			if (basic.size()!=1) {
				logger.error("Basic is null");
				failureResponse(exchange,401,"Basic is null");
				return;		
			}
			if (!basic.get(0).startsWith("Basic ")) {
				logger.error("Authorization must be \"Basic Basic64(<client_id>:<client_secret>)\"");
				failureResponse(exchange,401,"Authorization must be \"Basic Basic64(<client_id>:<client_secret>)\"");
				return;		
			}
			
			//*************
			//Get token
			//*************
			Response token = am.getToken(basic.get(0).split(" ")[1]);
			
			if (token.getClass().equals(ErrorResponse.class)) {
				ErrorResponse error = (ErrorResponse) token;
				logger.error(token.toString());
				failureResponse(exchange,error.getErrorCode(),error.getErrorMessage());
			}
			else sendHTTPResponse(exchange,token.toString());
		}
		
		private void sendHTTPResponse(HttpExchange exchange,String body) {
			try {
				exchange.sendResponseHeaders(201, body.length());
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
			OutputStream os = exchange.getResponseBody();
			try {
				os.write(body.getBytes());
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
			try {
				os.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
			}	
		}
	}
	
	class SecureSPARQLHandler extends SPARQLHandler {
		/*
		 * Operation when receiving a HTTP request at a protected endpoint
		 * 
 		 * 1. Check if the request contains an Authorization header.
		 * 2. Check if the request contains an Authorization: Bearer-header with non-null/empty contents
		 * 3. Check if the value of the Authorization: Bearer-header is a JWT object
		 * 4. Check if the JWT object is signed
		 * 5. Check if the signature of the JWT object is valid. This is to be checked with AS public signature verification key
		 * 6. Check the contents of the JWT object
		 * 7. Check if the value of "iss" is https://wot.arces.unibo.it:8443/oauth/token
		 * 8. Check if the value of "aud" contains https://wot.arces.unibo.it:8443/sparql
		 * 9. Accept the request as well as "sub" as the originator of the request and process it as usual
		 * 
		 * *** Respond with 401 if not
		 * */
		@Override
		public void handle(HttpExchange httpExchange) throws IOException {
			logger.info(">> HTTPS request");	
			
			if (CORSManager.processCORSPreFlightRequest(httpExchange)) return;
			
			//Check authorization header
			if (!httpExchange.getRequestHeaders().containsKey("Authorization")) {
				logger.error("Authorization is null");
				failureResponse(httpExchange,401,"Authorization is null");
				return;
			}
			
			//Extract Bearer authorization
			List<String> bearer = httpExchange.getRequestHeaders().get("Authorization");
			if (bearer.size()!=1) {
				logger.error("Bearer is null");
				failureResponse(httpExchange,401,"Bearer is null");
				return;		
			}
			if (!bearer.get(0).startsWith("Bearer ")) {
				logger.error("Authorization must be \"Bearer JWT\"");
				failureResponse(httpExchange,401,"Authorization must be \"Bearer JWT\"");
				return;		
			}
			
			//******************
			//JWT validation
			//******************
			String jwt = bearer.get(0).split(" ")[1];
			
			Response valid = am.validateToken(jwt);
			if(!valid.getClass().equals(ErrorResponse.class)) super.handle(httpExchange);
			else {
				ErrorResponse error = (ErrorResponse) valid;
				failureResponse(httpExchange,error.getErrorCode(),error.getErrorMessage());
			}
		}
	}
	
	@Override
	public void start(){	
		this.setName("SEPA HTTPS Gate");
		
		// create HTTPS server
		try {
			server = HttpsServer.create(new InetSocketAddress(properties.getHttpsPort()), 0);
		} catch (IOException e) {
			logger.fatal(e.getMessage());
			System.exit(1);
		}
		
		// Set security configuration
		((HttpsServer)server).setHttpsConfigurator(am.getHttpsConfigurator());
		
	    server.createContext(properties.getHttpsPath(), new SecureSPARQLHandler());
	    server.createContext("/echo", new EchoHandler());
	    
	    //WoT Authentication
	    server.createContext(properties.getRegisterPath(), new RegistrationHandler());
	    server.createContext(properties.getTokenRequestPath(), new TokenHandler());
	    
	    server.setExecutor(null);
	    server.start();
	    
	    logger.info("Started on port "+properties.getHttpsPort()+properties.getHttpsPath());
	}
}