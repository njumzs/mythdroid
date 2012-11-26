/*
    MythDroid: Android MythTV Remote
    Copyright (C) 2009-2010 foobum@gmail.com

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

package org.mythdroid.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.json.JSONException;
import org.json.JSONObject;
import org.mythdroid.Globals;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.HttpFetcher;
import org.mythdroid.util.LogUtil;

/** A JSON web service client */
public class JSONClient {
    
    private HttpFetcher fetcher = null;
    private String addr = null;
    private String serv = null;
    private int port    = 0;
    
    /**
     * Construct a new JSONClient
     * @param address IP address or hostname of server
     */
    public JSONClient(String address, String service) {
        fetcher = new HttpFetcher(Globals.muxConns);
        addr = address;
        serv = service;
    }
    
    /**
     * Construct a new JSONClient, specifying the port to connect to
     * @param address IP address or hostname of server
     */
    public JSONClient(String address, int port, String service) {
        fetcher = new HttpFetcher(Globals.muxConns);
        addr = address;
        serv = service;
        this.port = port;
    }
    
    /**
     * Perform a GET request of the specified path
     * @param path relative path of the query
     * @param query HashMap of query parameters
     * @return a JSONObject with the results
     * @throws IOException 
     */
    public JSONObject Get(String path, Params query) throws IOException {
        try {
            return Request(new HttpGet(CreateURI(path, query)));
        } catch (URISyntaxException e) {
            ErrUtil.logErr(e);
            return null;
        } catch (IllegalStateException e) {
            ErrUtil.logErr(e);
            ErrUtil.report(e);
            return null;
        }
    }
    
    /**
     * Perform a POST request to the specified path
     * @param path relative path of the query
     * @param query HashMap of query parameters
     * @return a JSONObject with the results
     * @throws IOException 
     */
    public JSONObject Post(String path, Params query) throws IOException {
        try {
            return Request(new HttpPost(CreateURI(path, query)));
        } catch (URISyntaxException e) {
            ErrUtil.logErr(e);
            return null;
        } catch (IllegalStateException e) {
            ErrUtil.logErr(e);
            ErrUtil.report(e);
            return null;
        }
    }
    
    private URI CreateURI(String method, Params query) 
        throws URISyntaxException {

        String params = null;
        
        if (query != null)
            params = query.toString();
        
        return new URI(
            "http", null, addr, port == 0 ? 6544 : port, //$NON-NLS-1$
            "/" + serv + "/" + method, params, null//$NON-NLS-1$ //$NON-NLS-2$
        );
        
    }
    
    private JSONObject Request(HttpUriRequest req) throws IOException {

        req.setHeader("Accept", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
        LogUtil.debug("JSON request: " + req.getURI().toString()); //$NON-NLS-1$
        
        try {
            fetcher.request(req);
        } catch (ClientProtocolException e) {
            ErrUtil.logErr(e);
            return null;
        }
        
        final String res = fetcher.getContent();
        LogUtil.debug("JSON response: " + res); //$NON-NLS-1$
        
        try {
            return new JSONObject(res);
        } catch (JSONException e) {
            ErrUtil.logErr(e);
            return null;
        }
    
    }
            
}
