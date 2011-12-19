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
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import org.mythdroid.data.XMLHandler.Element;
import org.mythdroid.mdd.MDDManager;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.Enums.RecDupIn;
import org.mythdroid.Enums.RecEpiFilter;
import org.mythdroid.Enums.RecDupMethod;
import org.mythdroid.Enums.RecStatus;
import org.mythdroid.Enums.RecType;
import org.mythdroid.Globals;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.sax.EndElementListener;
import android.sax.EndTextElementListener;
import android.sax.StartElementListener;

/**
 * A Program - can be constructed from a stringlist (from a backend)
 * or from XML (e.g. in Status or in the Guide).
 */
public class Program implements Comparable<Program> {

    /**
     * Implement this and pass to ProgramXMLParser to receive
     * Program objects as they are parsed from XML
     */
    static public interface ProgramListener {
        /**
         * Called when a Program is parsed from XML
         * @param program the new Program
         */
        public void program(Program program);
    }

    /**
     * An implementation of SAX StartElementListener that parses
     * Program XML elements and their children. Creates Program objects
     * and passes them back to you via a ProgramListener
     */
    static public class ProgramXMLParser implements StartElementListener {

        private Context ctx  = null;
        private Program prog = null;

        /**
         * Constructor
         * @param context activity context, used for toasting errors
         * @param elem a Program (XMLHandler) Element
         * @param listener A ProgramListener to call back with Programs
         */
        ProgramXMLParser(
            Context context, Element elem, final ProgramListener listener
        ) {

            ctx = context;

            elem.setEndElementListener(new EndElementListener() {
                @Override
                public void end() {
                    listener.program(prog);

                }
            });

            elem.getChild("Channel").setStartElementListener( //$NON-NLS-1$
                new StartElementListener() {
                    @Override
                    public void start(Attributes attr) {
                        prog.Channel = attr.getValue("callSign"); //$NON-NLS-1$
                        prog.ChanID = Integer.valueOf(attr.getValue("chanId")); //$NON-NLS-1$
                    }
                }
            );

            elem.getChild("Recording").setStartElementListener( //$NON-NLS-1$
                new StartElementListener() {
                    @Override
                    public void start(Attributes attr) {
                        try {
                            prog.RecStartTime = Globals.dateFmt.parse(
                                attr.getValue("recStartTs") //$NON-NLS-1$
                            );
                            prog.RecEndTime = Globals.dateFmt.parse(
                                attr.getValue("recEndTs") //$NON-NLS-1$
                            );
                        } catch (ParseException e) {}

                        prog.Status = RecStatus.get(
                            Integer.valueOf(attr.getValue("recStatus")) //$NON-NLS-1$
                        );
                        prog.RecPrio = Integer.valueOf(
                            attr.getValue("recPriority") //$NON-NLS-1$
                        );
                        prog.RecGroup = attr.getValue("recGroup"); //$NON-NLS-1$
                        prog.DupMethod = RecDupMethod.get(
                            Integer.valueOf(attr.getValue("dupMethod")) //$NON-NLS-1$
                        );
                        prog.Type = RecType.get(
                            Integer.valueOf(attr.getValue("recType")) //$NON-NLS-1$
                        );
                        prog.RecID = Integer.valueOf(
                            attr.getValue("recordId") //$NON-NLS-1$
                        );

                        int dupInTemp =
                            Integer.valueOf(attr.getValue("dupInType")); //$NON-NLS-1$
                        prog.DupIn = RecDupIn.get(dupInTemp & 0x0f);
                        prog.EpiFilter = RecEpiFilter.get(dupInTemp & 0xf0);

                    }
                }
            );

            elem.setTextElementListener(
                new EndTextElementListener() {
                    @Override
                    public void end(String body) {
                        prog.Description = body;
                    }
                }
            );

        }
        
        @Override
        public void start(Attributes attr) {

            prog = new Program();
            prog.Title = attr.getValue("title"); //$NON-NLS-1$
            prog.SubTitle = attr.getValue("subTitle"); //$NON-NLS-1$
            prog.Category = attr.getValue("category"); //$NON-NLS-1$

            try {
                prog.StartTime = Globals.dateFmt.parse(
                    attr.getValue("startTime") //$NON-NLS-1$
                );
                prog.EndTime = Globals.dateFmt.parse(
                    attr.getValue("endTime") //$NON-NLS-1$
                );
            } catch (ParseException e) {
                ErrUtil.postErr(ctx, e);
            }

        }

    }
    
    /** Represents a commercial break or 'cut' */
    static public class Commercial {
        /** Frame number of commercial start */
        public int start;
        /** Frame number of commercial end */
        public int end;
        
        /**
         * Constructor
         * @param start frame number of commercial start
         * @param end frame number of commercial end
         */
        public Commercial(int start, int end) {
            this.start = start;
            this.end   = end;
        }
        
        /**
         * Constructor
         * @param cut 2 element array, 1st is start frame, 2nd is end frame
         */
        public Commercial(int[] cut) {
            if (cut.length != 2)
                throw new IllegalArgumentException(
                    Messages.getString("Program.47") //$NON-NLS-1$
                );
            start = cut[0];
            end   = cut[1];
        }
                
    }
    
    @SuppressWarnings("all")
    static final public int
        TITLE     = 0,  SUBTITLE  = 1,  DESC     = 2,   CATEGORY  = 3,
        CHANID    = 4,  CHANNEL   = 6,  PATH     = 8,
        START, END, FINDID, RECPRIO, STATUS, RECID, RECTYPE, RECDUPIN,
        DUPMETHOD, RECSTART, RECEND, RECGROUP, SERIESID, PROGID, STORGROUP,
        TOTAL;

    static {
        if (Globals.protoVersion < 57) {
            START     = 11; END       = 12; FINDID    = 15; RECPRIO   = 20;
            STATUS    = 21; RECID     = 22; RECTYPE   = 23; RECDUPIN  = 24;
            DUPMETHOD = 25; RECSTART  = 26; RECEND    = 27; RECGROUP  = 30;
            SERIESID  = 33; PROGID    = 34; STORGROUP = 42;
            TOTAL     = Globals.protoVersion < 50 ? 46 : 47;
        }
        else {
            START     = 10; END       = 11; FINDID    = 12; RECPRIO   = 17;
            STATUS    = 18; RECID     = 19; RECTYPE   = 20; RECDUPIN  = 21;
            DUPMETHOD = 22; RECSTART  = 23; RECEND    = 24; RECGROUP  = 26;
            SERIESID  = 28; PROGID    = 29; STORGROUP = 36; TOTAL     = 41;
        }
    }
    
    @SuppressWarnings("all")
    public String       Title, SubTitle, Category, Description, Channel, Path,
                        RecGroup, StorGroup;
    @SuppressWarnings("all")
    public Date         StartTime, EndTime, RecStartTime, RecEndTime;
    /** Recording status */
    public RecStatus    Status = RecStatus.UNKNOWN;
    /** Recording type */
    public RecType      Type = RecType.NOT;
    /** Recording duplicate search space */
    public RecDupIn     DupIn = RecDupIn.ALL;
    /** Recording episode filter */
    public RecEpiFilter EpiFilter = RecEpiFilter.NONE;
    /** Recording duplicate match method */
    public RecDupMethod DupMethod = RecDupMethod.SUBANDDESC;
    @SuppressWarnings("all")
    public int          ChanID, RecID = -1, RecPrio = 0;

    private String[] list                     = null;
    private ArrayList<Commercial> commercials = new ArrayList<Commercial>();
    private boolean fetchedCutList            = false;
    
    /**
     * Construct a Program from a stringlist
     * @param list a stringlist (e.g. from a backend)
     */
    public Program(String[] list, int off) {

        if (list.length < off + STORGROUP + 1)
            throw new IllegalArgumentException("Program: not enough elements"); //$NON-NLS-1$
        
        int dupInTemp;

        try {
            Title         = list[off+TITLE];
            SubTitle      = list[off+SUBTITLE];
            Description   = list[off+DESC];
            Category      = list[off+CATEGORY];
            ChanID        = Integer.valueOf(list[off+CHANID]);
            Channel       = list[off+CHANNEL];
            Path          = list[off+PATH];
            StartTime     = new Date(Long.valueOf(list[off+START]) * 1000);
            EndTime       = new Date(Long.valueOf(list[off+END]) * 1000);
            RecPrio       = Integer.valueOf(list[off+RECPRIO]);
            Status        = RecStatus.get(Integer.valueOf(list[off+STATUS]));
            RecID         = Integer.valueOf(list[off+RECID]);
            Type          = RecType.get(Integer.valueOf(list[off+RECTYPE]));
            dupInTemp     = Integer.valueOf(list[off+RECDUPIN]);
            DupMethod
                     = RecDupMethod.get(Integer.valueOf(list[off+DUPMETHOD]));
            RecStartTime  = new Date(Long.valueOf(list[off+RECSTART]) * 1000);
            RecEndTime    = new Date(Long.valueOf(list[off+RECEND]) * 1000);
            RecGroup      = list[off+RECGROUP];
            StorGroup     = list[off+STORGROUP];

            DupIn         = RecDupIn.get(dupInTemp & 0x0f);
            EpiFilter     = RecEpiFilter.get(dupInTemp & 0xf0);

        } catch (NumberFormatException e) {}

    }

    /**
     * Construct a Program from an XML (DOM) Node
     * @param item a Program XML (DOM) node
     */
    public Program(Node item) {

        NamedNodeMap attr = item.getAttributes();
        Title = attr.getNamedItem("title").getNodeValue(); //$NON-NLS-1$
        SubTitle = attr.getNamedItem("subTitle").getNodeValue(); //$NON-NLS-1$
        Category = attr.getNamedItem("category").getNodeValue(); //$NON-NLS-1$
        try {
            StartTime = Globals.dateFmt.parse(
                attr.getNamedItem("startTime").getNodeValue() //$NON-NLS-1$
            );
            EndTime = Globals.dateFmt.parse(
                attr.getNamedItem("endTime").getNodeValue() //$NON-NLS-1$
            );
        } catch (ParseException e) {}

        if (!item.hasChildNodes()) return;

        Node ChanNode = null, RecNode = null;
        NodeList nodes = item.getChildNodes();
        String name = null;
        int numNodes = nodes.getLength();

        for (int i = 0; i < numNodes; i++) {
            Node node = nodes.item(i);
            name = node.getNodeName();
            if (name == null) continue;
            if (ChanNode == null && name.equals("Channel")) //$NON-NLS-1$
                ChanNode = node;
            else if (RecNode == null && name.equals("Recording")) //$NON-NLS-1$
                RecNode = node;
            else if (node.getNodeType() == Node.TEXT_NODE) {
                name = node.getNodeValue();
                if (!name.startsWith("\n")) Description = name; //$NON-NLS-1$
            }
        }

        if (ChanNode != null) {
            attr = ChanNode.getAttributes();
            Channel = attr.getNamedItem("callSign").getNodeValue(); //$NON-NLS-1$
            ChanID = Integer.valueOf(
                attr.getNamedItem("chanId").getNodeValue() //$NON-NLS-1$
            );
        }

        if (RecNode != null) {
            attr = RecNode.getAttributes();
            try {
                RecStartTime = Globals.dateFmt.parse(
                    attr.getNamedItem("recStartTs").getNodeValue() //$NON-NLS-1$
                );
                RecEndTime = Globals.dateFmt.parse(
                    attr.getNamedItem("recEndTs").getNodeValue() //$NON-NLS-1$
                );
            } catch (ParseException e) {}

            Status = RecStatus.get(
                Integer.valueOf(attr.getNamedItem("recStatus").getNodeValue()) //$NON-NLS-1$
            );
            RecPrio = Integer.valueOf(
                attr.getNamedItem("recPriority").getNodeValue() //$NON-NLS-1$
            );
            RecGroup = attr.getNamedItem("recGroup").getNodeValue(); //$NON-NLS-1$
            DupMethod = RecDupMethod.get(
                Integer.valueOf(attr.getNamedItem("dupMethod").getNodeValue()) //$NON-NLS-1$
            );
            Type = RecType.get(
                Integer.valueOf(attr.getNamedItem("recType").getNodeValue()) //$NON-NLS-1$
            );
            RecID = Integer.valueOf(
                attr.getNamedItem("recordId").getNodeValue() //$NON-NLS-1$
            );

            int dupInTemp =
                Integer.valueOf(attr.getNamedItem("dupInType").getNodeValue()); //$NON-NLS-1$
            DupIn = RecDupIn.get(dupInTemp & 0x0f);
            EpiFilter = RecEpiFilter.get(dupInTemp & 0xf0);

        }
    }

    /** Construct an empty Program */
    public Program() {}

    /**
     * Get Program's playback ID (as used in frontend play command)
     * @return String of form "ChanID FormattedRecStartTime"
     */
    public String playbackID() {
        return ChanID + " " + Globals.dateFmt.format(RecStartTime); //$NON-NLS-1$
    }

    /**
     * Get a preview image for the recording (will return
     * null unless Status was RECORDED or RECORDING or CURRENT)
     * @return a Bitmap or null if no preview image was found
     */
    public Bitmap previewImage() {

        if (
            Status != RecStatus.RECORDED &&
            Status != RecStatus.RECORDING &&
            Status != RecStatus.CURRENT
        ) return null;

        try {
            URL url =
                new URL(
                    Globals.getBackend().getStatusURL() +
                    "/Myth/GetPreviewImage?ChanId=" + ChanID + //$NON-NLS-1$
                    "&StartTime=" + Globals.dateFmt.format(RecStartTime) //$NON-NLS-1$
                );
            if (Globals.muxConns)
                url = new URL(
                    url.getProtocol() + "://" + url.getHost() +  //$NON-NLS-1$
                    ":16550" + url.getFile()  //$NON-NLS-1$
                );
            return BitmapFactory.decodeStream(url.openStream());
        } catch (Exception e) { 
            ErrUtil.logWarn(e); 
            return null; 
        }
        
    }

    /**
     * Get a formatted representation of StartTime
     * @return String of format "12:00, Mon 1 Jan 09"
     */
    public String startString() {
        return Globals.dispFmt.format(StartTime);
    }

    /**
     * Get a formatted representation of EndTime
     * @return String of format "12:00, Mon 1 Jan 09"
     */
    public String endString() {
        return Globals.dispFmt.format(EndTime);
    }
    
    /**
     * Get a list of Commercials
     * @param addr address of mdd instance
     * @return a list of Commercials, which might be empty
     */
    public ArrayList<Commercial> getCutList(String addr) {
        if (fetchedCutList)
            return commercials;
        try {
            for (int[] cut : MDDManager.getCutList(addr, this)) {
                try {
                    commercials.add(new Commercial(cut));
                } catch (IllegalArgumentException e1) { continue; }
            }
        } catch (IOException e) { return commercials; }
          finally { fetchedCutList = true; }
        return commercials;
    }

    /**
     * Flatten to a stringlist for use in backend commands
     * @return List of Strings, doesn't include stringlist separators
     */
    public String[] stringList() {

        if (list != null) return list;

        list = new String[TOTAL+1];
        Arrays.fill(list, ""); //$NON-NLS-1$

        list[TITLE+1]     =  Title;
        list[SUBTITLE+1]  =  SubTitle;
        list[DESC+1]      =  Description;
        list[CATEGORY+1]  =  Category;
        list[CHANID+1]    =  String.valueOf(ChanID);
        list[CHANNEL+1]   =  Channel;
        list[PATH+1]      =  Path == null ? "" : Path; //$NON-NLS-1$
        list[START+1]     =  String.valueOf(StartTime.getTime() / 1000);
        list[END+1]       =  String.valueOf(EndTime.getTime() / 1000);
        list[RECSTART+1]  =  String.valueOf(RecStartTime.getTime() / 1000);
        list[RECEND+1]    =  String.valueOf(RecEndTime.getTime() / 1000);
        list[STATUS+1]    =  String.valueOf(Status.value());

        return list;

    }

    /**
     * Get index in stringlist of the TYPE field
     * @return int index of TYPE field
     */
    static public int recGroupField() {
        return RECGROUP;
    }

    /**
     * Get total number of fields in stringlist
     * @return int total
     */
    static public int numFields() {
        return TOTAL;
    }

    @Override
    public int compareTo(Program another) {
        if (StartTime.getTime() < another.StartTime.getTime())
            return -1;
        else if (StartTime.getTime() > another.StartTime.getTime())
            return 1;
        return 0;
    }

}
