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

package org.mythdroid;

import java.util.ArrayList;

import org.mythdroid.Program.ProgramListener;
import org.mythdroid.Program.ProgramXMLParser;
import org.mythdroid.Program.RecStatus;
import org.mythdroid.XMLHandler.Element;
import org.xml.sax.Attributes;

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
         * @param context - activity context, used for toasting errors
         * @param elem - a Channel (XMLHandler) Element
         * @param listener - A ChannelListener to call back with Channels 
         */
        ChannelXMLParser(
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

            final Element progElem = elem.getChild("Program");

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
            chan.callSign = attr.getValue("callSign");
            chan.num = Integer.valueOf(attr.getValue("chanNum"));
            chan.ID = Integer.valueOf(attr.getValue("chanId"));
        }

    }

    public String callSign;
    public int num, ID;
    public ArrayList<Program> programs = new ArrayList<Program>();

    /** Construct an empty Channel */
    public Channel() {}

    @Override
    public int compareTo(Channel other) {
        if (num > other.num) {
            return 1;
        }
        else if (num < other.num) { return -1; }
        return 0;
    }

}
