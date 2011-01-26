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

import java.util.ArrayList;

import org.mythdroid.data.Program.ProgramListener;
import org.mythdroid.data.Program.ProgramXMLParser;
import org.mythdroid.Enums.RecStatus;
import org.mythdroid.data.XMLHandler.Element;
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

    /** String representing channel callsign */
    public String callSign;
    /** String representing channel number  */
    public String num;
    /** integer representing channel ID */
    public int ID;
    /** ArrayList of Programs */
    public ArrayList<Program> programs = new ArrayList<Program>();

    /** Construct an empty Channel */
    public Channel() {}

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
