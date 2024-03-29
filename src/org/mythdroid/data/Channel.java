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

package org.mythdroid.data;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mythdroid.data.Program.ProgramListener;
import org.mythdroid.data.Program.ProgramXMLParser;
import org.mythdroid.Enums.RecStatus;
import org.mythdroid.data.XMLHandler.Element;
import org.xml.sax.Attributes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import android.content.Context;
import android.sax.EndElementListener;
import android.sax.StartElementListener;

/**
 * Represents a channel and a list of programs (used in Guide)
 * can only be usefully constructed from XML (SAX), implements
 * Comparable for sorting on channel number
 */
public class Channel implements Comparable<Channel> {

    /**
     * Implement this and pass to ChannelXMLParser to receive
     * Channel objects as they are parsed from XML
     */
    static public interface ChannelListener {
        /**
         * Called when a Channel has been parsed from XML
         * @param channel Channel object
         */
        public void channel(Channel channel);
    }

    /**
     * An implementation of SAX StartElementListener that parses
     * Channel XML elements and their children. Creates Channel objects
     * and passes them back to you via a ChannelListener
     */
    static public class ChannelXMLParser implements StartElementListener {

        private Channel chan = null;

        /**
         * Constructor
         * @param ctx activity context, used for toasting errors
         * @param elem a Channel (XMLHandler) Element
         * @param listener A ChannelListener to call back with Channels
         */
        public ChannelXMLParser(
            Context ctx, Element elem, final ChannelListener listener
        ) {

            elem.setEndElementListener(
                new EndElementListener() {
                    @Override
                    public void end() {
                        listener.channel(chan);

                    }
                }
            );

            final Element progElem = elem.getChild("Program"); //$NON-NLS-1$

            progElem.setStartElementListener(
                new ProgramXMLParser(ctx, progElem,
                    new ProgramListener() {
                        @Override
                        public void program(Program prog) {
                            prog.ChanID = chan.ID;
                            prog.Channel = chan.callSign;
                            if (prog.Status == null)
                                prog.Status = RecStatus.UNKNOWN;
                            chan.programs.add(prog);
                        }
                    }
                )
            );

        }

        @Override
        public void start(Attributes attr) {
            chan = new Channel();
            chan.callSign = attr.getValue("callSign"); //$NON-NLS-1$
            chan.num = attr.getValue("chanNum"); //$NON-NLS-1$
            chan.ID = Integer.valueOf(attr.getValue("chanId")); //$NON-NLS-1$
        }

    }
    
    /** A gson TypeAdapter for the Channel class */
    public static class ChannelJsonAdapter extends TypeAdapter<Channel> {
        
        static GsonBuilder gsonBuilder = new GsonBuilder();
        static {
            gsonBuilder.registerTypeAdapter(Program.class, new Program.ProgramJsonAdapter());
        }
        static Gson gson = gsonBuilder.create();
        
        
        @Override
        public Channel read(JsonReader jr) throws IOException {
            
            Channel chan = new Channel();
            
            while (jr.hasNext()) {
                String name = jr.nextName();
                if (name.equals("ChanId")) //$NON-NLS-1$
                    chan.ID = jr.nextInt();
                else if (name.equals("ChanNum")) //$NON-NLS-1$
                    chan.num = jr.nextString();
                else if (name.equals("CallSign")) //$NON-NLS-1$
                    chan.callSign = jr.nextString();
                else if (name.equals("Programs")) { //$NON-NLS-1$
                    jr.beginArray();
                    while (jr.hasNext()) {
                        jr.beginObject();
                        Program prog = gson.fromJson(jr, Program.class);
                        prog.ChanID = chan.ID;
                        prog.Channel = chan.callSign;
                        chan.programs.add(prog);
                        jr.endObject();
                    }
                    jr.endArray();
                }
                else
                    jr.skipValue();
            }
            
            return chan;
            
        }

        @Override
        public void write(JsonWriter arg0, Channel arg1) throws IOException {}

    }

    /** String representing channel callsign */
    public String callSign;
    /** String representing channel number  */
    public String num;
    /** integer representing channel ID */
    public int ID;
    /** ArrayList of Programs */
    final public ArrayList<Program> programs = new ArrayList<Program>();

    /** Construct an empty Channel */
    public Channel() {}
    
    /** 
     * Construct a Channel from a "Channel" JSONObject
     * @param jo Channel JSONObject 
     * @throws JSONException 
     * @throws ParseException
     */ 
    public Channel(JSONObject jo) throws JSONException, ParseException {
        callSign = jo.getString("CallSign"); //$NON-NLS-1$
        num = jo.getString("ChanNum"); //$NON-NLS-1$
        ID = jo.getInt("ChanId"); //$NON-NLS-1$
        JSONArray ja = jo.getJSONArray("Programs"); //$NON-NLS-1$
        int numProgs = ja.length();
        for (int i = 0; i < numProgs; i++) {
            Program prog = new Program(ja.getJSONObject(i));
            prog.ChanID = ID;
            prog.Channel = callSign;
            programs.add(prog);
        }
        
    }

    @Override
    public int compareTo(Channel other) {
        int l = num.length();
        int ol = other.num.length();
        if (l > ol) return 1;
        else if (l < ol) return -1;
        int c = num.compareTo(other.num);
        if (c > 0) return 1;
        else if (c < 0) return -1;
        else return 0;
    }

}
