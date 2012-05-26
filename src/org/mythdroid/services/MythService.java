package org.mythdroid.services;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mythdroid.util.LogUtil;

/**
 * An implementation of the Myth service
 */
public class MythService {
      
        private JSONClient jc = null;
        private String hostName = null;
        
        /**
         * Construct a client for the Myth service
         * @param addr IP address or hostname of server
         */
        public MythService(String addr) {
            jc = new JSONClient(addr, "Myth"); //$NON-NLS-1$
            hostName = getHostName();
        }
        
        /**
         * Get the hostname of the backend server
         * @return String containing hostname
         */
        public String getHostName() {
            
            JSONObject jo;
            try {
                jo = jc.Get("GetHostName", null);  //$NON-NLS-1$
            } catch (IOException e) {
                return null;
            }
            
            if (jo == null) return null;
            
            try {
                return jo.getString("String"); //$NON-NLS-1$
            } catch (JSONException e) {
                LogUtil.debug(e.getMessage());
                return null;
            }
            
        }
        
        /**
         * Get the protocol version
         * @return protocol version number
         * @throws IOException 
         */
        public int getVersion() throws IOException {
            
            if (hostName == null && (hostName = getHostName()) == null)
                return 0;
            
            String pin = getSetting("SecurityPin"); //$NON-NLS-1$
            
            if (pin == null || pin.length() == 0) {
                Params params = new Params("HostName", hostName); //$NON-NLS-1$
                params.put("Key", "SecurityPin"); //$NON-NLS-1$ //$NON-NLS-2$
                params.put("Value", "0000"); //$NON-NLS-1$ //$NON-NLS-2$
                jc.Post("PutSetting", params); //$NON-NLS-1$
            }
            
            JSONObject jo = jc.Get("GetConnectionInfo", null); //$NON-NLS-1$
            if (jo == null) return 0;
            
            try {
                jo = jo.getJSONObject("ConnectionInfo"); //$NON-NLS-1$
                jo = jo.getJSONObject("Version"); //$NON-NLS-1$
                return Integer.parseInt(jo.getString("Protocol")); //$NON-NLS-1$
            } catch (JSONException e) {
                LogUtil.debug(e.getMessage());
                return 0;
            }
            
        }
        
        /**
         * Retrieve a setting
         * @param key name of the setting
         * @return String containing value of the setting
         * @throws IOException 
         */
        public String getSetting(String key) throws IOException {
            
            if (hostName == null && (hostName = getHostName()) == null)
                return null;
            
            Params params = new Params("HostName", hostName); //$NON-NLS-1$
            params.put("Key", key); //$NON-NLS-1$
            JSONObject jo = jc.Get("GetSetting", params); //$NON-NLS-1$
            if (jo == null) return null;
            
            try {
                jo = jo.getJSONObject("SettingList"); //$NON-NLS-1$
                jo = jo.getJSONObject("Settings"); //$NON-NLS-1$
                return jo.getString("SecurityPin"); //$NON-NLS-1$
            } catch (JSONException e) {
                LogUtil.debug(e.getMessage());
                return null;
            }
        }
        
        /**
         * Get a list of storage groups
         * @return an array of Strings containing storage group names
         * @throws IOException 
         */
        public String[] getStorageGroups() throws IOException {
            
           ArrayList<String> dirs = new ArrayList<String>();
           
           if (hostName == null && (hostName = getHostName()) == null)
               return null;
            
           JSONObject jo = jc.Get(
               "GetStorageGroupDirs", new Params("hostname", hostName) //$NON-NLS-1$ //$NON-NLS-2$
           );
           if (jo == null) return null;
           
           JSONArray ja = null;
           
           try {
               jo = jo.getJSONObject("StorageGroupDirList"); //$NON-NLS-1$
               ja = jo.getJSONArray("StorageGroupDirs"); //$NON-NLS-1$
           } catch (JSONException e) {
               LogUtil.debug(e.getMessage());
               return null;
           }
           
           int num = ja.length();
           
           for (int i = 0; i < num; i++) {
               JSONObject sgd = null;
               try {
                   sgd = ja.getJSONObject(i);
                   dirs.add(sgd.getString("GroupName")); //$NON-NLS-1$
               } catch (JSONException e) {
                   LogUtil.debug(e.getMessage());
                   return null;
               }
           }
           
           return dirs.toArray(new String[dirs.size()]);
        
        }
   
}
