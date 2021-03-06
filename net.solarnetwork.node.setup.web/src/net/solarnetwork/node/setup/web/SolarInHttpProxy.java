/* ==================================================================
 * SolarInHttpProxy.java - Nov 19, 2013 4:09:04 PM
 * 
 * Copyright 2007-2013 SolarNetwork.net Dev Team
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 * 02111-1307 USA
 * ==================================================================
 */

package net.solarnetwork.node.setup.web;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.solarnetwork.node.IdentityService;
import net.solarnetwork.node.SSLService;
import net.solarnetwork.node.support.HttpClientSupport;
import net.solarnetwork.util.OptionalServiceTracker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Proxy HTTP requests to SolarIn.
 * 
 * <p>
 * This is designed to be used by the Settings app, to support calling SolarIn
 * web services without relying on the user's browser be configured to support
 * the SolarIn X.509 certificate.
 * </p>
 * 
 * @author matt
 * @version 1.0
 */
@Controller
public class SolarInHttpProxy extends HttpClientSupport {

	@Autowired
	public SolarInHttpProxy(@Qualifier("identityService") IdentityService identityService,
			@Qualifier("sslService") OptionalServiceTracker<SSLService> sslService) {
		super();
		setIdentityService(identityService);
		setSslService(sslService);
	}

	/**
	 * Proxy an HTTP request to SolarIn and return the result on a given HTTP
	 * response.
	 * 
	 * @param request
	 *        the request to proxy
	 * @param response
	 *        the response to return the proxy response to
	 * @throws IOException
	 *         if an IO error occurs
	 */
	@RequestMapping(value = { "/api/v1/sec/location", "/api/v1/sec/location/price",
			"/api/v1/sec/location/weather" }, method = RequestMethod.GET)
	public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String context = request.getContextPath();
		String path = request.getRequestURI();
		if ( path.startsWith(context) ) {
			path = path.substring(context.length());
		}
		String query = request.getQueryString();
		String url = getIdentityService().getSolarInBaseUrl() + path;
		if ( query != null ) {
			url += '?' + query;
		}
		String accept = request.getHeader("Accept");
		if ( accept == null ) {
			accept = ACCEPT_JSON;
		}
		try {
			URLConnection conn = getURLConnection(url, request.getMethod(), accept);
			if ( conn instanceof HttpURLConnection ) {
				final HttpURLConnection httpConn = (HttpURLConnection) conn;
				for ( Map.Entry<String, List<String>> me : httpConn.getHeaderFields().entrySet() ) {
					final String headerName = me.getKey();
					if ( headerName == null ) {
						continue;
					}
					for ( String val : me.getValue() ) {
						response.addHeader(headerName, val);
					}
				}
				final String msg = httpConn.getResponseMessage();
				if ( msg != null && !msg.equalsIgnoreCase("OK") ) {
					response.sendError(httpConn.getResponseCode(), msg);
				} else {
					response.setStatus(httpConn.getResponseCode());
				}
			}
			FileCopyUtils.copy(conn.getInputStream(), response.getOutputStream());
			response.flushBuffer();
		} catch ( IOException e ) {
			response.sendError(502, "Problem communicating with SolarIn: " + e.getMessage());
		}
	}
}
