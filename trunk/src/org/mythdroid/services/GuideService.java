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
import java.util.ArrayList;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mythdroid.Globals;
import org.mythdroid.data.Channel;
import org.mythdroid.util.LogUtil;

/** An implementation of the Guide service */
public class GuideService {
    
    private JSONClient jc = null;
    
    /**
     * Construct a client for the Guide service
     * @param addr IP address or hostname of server
     */
    public GuideService(String addr) {
        jc = new JSONClient(addr, "Guide"); //$NON-NLS-1$
    }
    
    /**
     * Get ProgramGuide data
     * @param start Date for start of data
     * @param end Date for end of data
     * @return ArrayList of Channels
     */
    public ArrayList<Channel> GetProgramGuide(Date start, Date end) 
        throws IOException, JSONException {
        
        final Params params = new Params();
        params.put("StartTime", Globals.utcFmt.format(start)); //$NON-NLS-1$
        params.put("EndTime", Globals.utcFmt.format(end)); //$NON-NLS-1$
        params.put("StartChanId", 0); //$NON-NLS-1$
        params.put("NumChannels", -1); //$NON-NLS-1$
        params.put("Details", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        
        JSONObject jo = jc.Get("GetProgramGuide", params); //$NON-NLS-1$
        jo = jo.getJSONObject("ProgramGuide"); //$NON-NLS-1$
        int num = jo.getInt("NumOfChannels"); //$NON-NLS-1$
        
        final ArrayList<Channel> channels = new ArrayList<Channel>(num);
        final JSONArray ja = jo.getJSONArray("Channels"); //$NON-NLS-1$
        
        for (int i = 0; i < num; i++) {
            try {
                channels.add(new Channel(ja.getJSONObject(i)));
            } catch (ParseException e) {
                LogUtil.error(e.getMessage());
                continue;
            }
        }
        
        return channels;
        
    }
    
    
}
    