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
import java.text.ParseException;

import org.json.JSONException;
import org.json.JSONObject;
import org.mythdroid.data.RecordingRule;
import org.mythdroid.util.LogUtil;
import org.mythdroid.util.MemCache;

/** An implementation of the Myth service */
public class DvrService {
    
    private MemCache<Integer, RecordingRule> recRuleCache = null;
    private JSONClient jc = null;
    
    /**
     * Construct a client for the Dvr service
     * @param addr IP address or hostname of server
     */
    public DvrService(String addr) {
        jc = new JSONClient(addr, "Dvr"); //$NON-NLS-1$
        recRuleCache = new MemCache<Integer, RecordingRule>(5, 10);
    }
    
    /**
     * Get a RecordingRule
     * @param id RecID of desired rule
     * @return RecordingRule
     */
    public RecordingRule getRecRule(int id) 
        throws JSONException, ParseException {

       if (recRuleCache.containsKey(id)) {
           return recRuleCache.get(id);
       }
       
       JSONObject jo = null;
       
       try {
           jo = jc.Get(
               "GetRecordSchedule", //$NON-NLS-1$
               new Params("RecordId", String.valueOf(id)) //$NON-NLS-1$
           );
       } catch (IOException e) { return null; }
       
       if (jo == null) return null;
       
       try {
           jo = jo.getJSONObject("RecRule"); //$NON-NLS-1$
       } catch (JSONException e) {
           LogUtil.debug(e.getMessage());
           return null;
       }
       
       final RecordingRule rule = new RecordingRule(jo);
       
       recRuleCache.put(id, rule);
    
       return rule;
        
    }

    /**
     * Delete a recording rule
     * @param recid RecID of the rule to delete
     * @throws IOException 
     */
    public void deleteRecording(int recid) throws IOException {
        jc.Post(
            "RemoveRecordSchedule", //$NON-NLS-1$
            new Params("RecordId", String.valueOf(recid)) //$NON-NLS-1$
        );
        recRuleCache.remove(recid);
    }
    
    /**
     * Create or update a recording rule
     * @param rule RecordingRule specifying parameters
     * @return int representing the RecID of the new/updated recording rule
     * @throws IOException 
     */
    public int updateRecording(RecordingRule rule)
        throws IOException {
        
        if (rule.id != -1)
            recRuleCache.remove(rule.id);
        
        final JSONObject jo = jc.Post("AddRecordSchedule", rule.toParams()); //$NON-NLS-1$
        
        if (jo == null) return -1;
        
        try {
            return jo.getInt("int"); //$NON-NLS-1$
        } catch (JSONException e) {
            LogUtil.debug(e.getMessage());
            return -1;
        } 
        
    }
   
}
