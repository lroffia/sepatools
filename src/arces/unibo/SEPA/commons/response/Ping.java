/* This class represents an error response message
Copyright (C) 2016-2017 Luca Roffia (luca.roffia@unibo.it)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package arces.unibo.SEPA.commons.response;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * This class represents the ping message sent on every active websocket (see SPARQL 1.1 Subscription Language)
 *
 * The JSON serialization is the following:
 *
 * {"ping" : "yyyy-MM-dd HH:mm:ss.SSS"}
 *
 * @author Luca Roffia (luca.roffia@unibo.it)
 * @version 0.1
 * */

public class Ping {
	protected JsonObject json;
	
	public Ping() {
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		String timestamp = sdf.format(date);

		json = new JsonObject();
		json.add("ping", new JsonPrimitive(timestamp) );
	}
	
	public String toString() {
		return json.toString();
	}
	
	public JsonObject toJson(){
		return json;
	}
}