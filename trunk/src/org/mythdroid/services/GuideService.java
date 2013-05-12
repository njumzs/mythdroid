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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

import org.json.JSONException;
import org.mythdroid.Globals;
import org.mythdroid.data.Channel;
import org.mythdroid.data.Program;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/** An implementation of the Guide service */
public class GuideService {
    
    private JSONClient jc = null;
    private GsonBuilder gsonBuilder = null;
    private Gson gson = null;
    
    /**
     * Construct a client for the Guide service
     * @param addr IP address or hostname of server
     */
    public GuideService(String addr) {
        jc = new JSONClient(addr, "Guide"); //$NON-NLS-1$
        gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(
            Channel.class, new Channel.ChannelJsonAdapter()
        );
        gson = gsonBuilder.create();
    }
    
    /**
     * Get ProgramGuide data
     * @param start Date for start of data
     * @param end Date for end of data
     * @return ArrayList of Channels
     */
    public ArrayList<Channel> GetProgramGuide(Date start, Date end) 
        throws IOException {
        
        final Params params = new Params();
        params.put("StartTime", Globals.utcFmt.format(start)); //$NON-NLS-1$
        params.put("EndTime", Globals.utcFmt.format(end)); //$NON-NLS-1$
        params.put("StartChanId", 0); //$NON-NLS-1$
        params.put("NumChannels", -1); //$NON-NLS-1$
        params.put("Details", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        
        InputStream is = jc.GetStream("GetProgramGuide", params); //$NON-NLS-1$
        JsonReader jreader = new JsonReader(
            new BufferedReader(new InputStreamReader(is, "UTF-8")) //$NON-NLS-1$
        );
        
        ArrayList<Channel> channels = new ArrayList<Channel>();
        
        jreader.beginObject();
        skipTo(jreader, JsonToken.BEGIN_OBJECT);
        jreader.beginObject();
        skipTo(jreader, JsonToken.NAME);
        while (jreader.hasNext()) {
            String name = jreader.nextName();
            if (name.equals("NumOfChannels")) { //$NON-NLS-1$
                channels.ensureCapacity(jreader.nextInt());
                break;
            }
            jreader.skipValue();
        }
        skipTo(jreader, JsonToken.BEGIN_ARRAY);
        jreader.beginArray();
        while (jreader.hasNext()) {
            jreader.beginObject();
            channels.add((Channel)gson.fromJson(jreader, Channel.class));
            jreader.endObject();
        }
        jreader.endArray();
        jreader.endObject();
        jreader.endObject();
        jreader.close();
        jc.endStream();
        
        return channels;
        
    }
    
    private void skipTo(JsonReader jr, JsonToken token) throws IOException {
        while (jr.hasNext() && jr.peek() != token)
                jr.skipValue();
    }
    
    /**
     * Get a Program 
     * @param chanId channel id
     * @param start program start time
     * @return a Program object representing the requested program
     * @throws JSONException
     * @throws ParseException
     * @throws IOException
     */
    public Program GetProgram(int chanId, Date start)
        throws JSONException, ParseException, IOException {
        
        final Params params = new Params();
        params.put("ChanId", chanId); //$NON-NLS-1$
        params.put("StartTime", Globals.utcFmt.format(start)); //$NON-NLS-1$
        
        return
            new Program(
                jc.Get("GetProgramDetails", params).getJSONObject("Program") //$NON-NLS-1$ //$NON-NLS-2$
            );
        
    }
    
    
}
    