/* This class represents a unsubscribe request
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
package arces.unibo.SEPA.commons.request;

import arces.unibo.SEPA.commons.request.Request;

public class UnsubscribeRequest extends Request {
	
	public UnsubscribeRequest(Integer token, String subId) {
		super(token, subId);
	}
	
	public UnsubscribeRequest(String subID) {
		super(subID);
	}

	public String getSubscribeUUID(){
		return super.getSPARQL();
	}

	public String toString() {
		if (token != -1) return "UNSUBSCRIBE #"+token+" "+getSubscribeUUID();
		return "UNSUBSCRIBE "+getSubscribeUUID();
	}
}
