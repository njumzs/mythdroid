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

import java.util.HashMap;
import java.util.Set;

/**
 * Parameters for a services request
 */
public class Params {

    private HashMap<String, String> map = null;
    
    /**
     * Construct an empty parameters object
     */
    public Params() {
        map = new HashMap<String, String>();
    }

    /**
     * Construct a Params with the supplied parameter
     * @param key name of parameter
     * @param value value of parameter
     */
    public Params(String key, String value) {
        map = new HashMap<String, String>();
        map.put(key, value);
    }
    
    /**
     * Add a parameter
     * @param key name of parameter
     * @param value value of parameter
     */
    public void put(String key, String value) {
        map.put(key, value);
    }
    
    /**
     * Add a parameter
     * @param key name of parameter
     * @param value value of parameter
     */
    public void put(String key, int value) {
        map.put(key, String.valueOf(value));
    }
    
    /**
     * Add a parameter
     * @param key name of parameter
     * @param value value of parameter
     */
    public void put(String key, long value) {
        map.put(key, String.valueOf(value));
    }
    
    /**
     * Add a parameter
     * @param key name of parameter
     * @param value value of parameter
     */
    public void put(String key, boolean value) {
        map.put(key, String.valueOf(value));
    }
    
    /**
     * Retrieve the value of the parameter with the specified key
     * @param key name of the parameter
     * @return String containing value of the specified parameter
     */
    public String get(String key) {
        return map.get(key);
    }
    
    /**
     * Retrieve the current set of keys
     * @return a set of keys
     */ 
    public Set<String> keys() {
        return map.keySet();
    }
    
    /**
     * Returns the number of parameters
     * @return number of stored parameters
     */
    public int size() {
        return map.size();
    }
    
    @Override
    public String toString() {
    
        if (map == null || map.isEmpty())
            return null;
        
        final StringBuilder sb = new StringBuilder();
    
        for (String it : map.keySet()) {
            sb.append(it + "=" + map.get(it) + "&");  //$NON-NLS-1$//$NON-NLS-2$
        }
    
        sb.deleteCharAt(sb.lastIndexOf("&")); //$NON-NLS-1$
    
        return sb.toString();
    }
    
}
