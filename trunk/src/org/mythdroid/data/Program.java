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

import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.mythdroid.data.XMLHandler.Element;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.activities.MythDroid;
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
 * or from XML (e.g. in Status or in the Guide). Has mostly public
 * members and a few convenience methods
 */
public class Program {
    
    /**
     * Implement this and pass to ProgramXMLParser to receive 
     * Program objects as they are parsed from XML
     */
    static public interface ProgramListener {
        /**
         * Called when a Program is parsed from XML
         * @param program - Program
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
         * @param context - activity context, used for toasting errors
         * @param elem - a Program (XMLHandler) Element
         * @param listener - A ProgramListener to call back with Programs 
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
                            prog.RecStartTime = MythDroid.dateFmt.parse(
                                attr.getValue("recStartTs") //$NON-NLS-1$
                            );
                            prog.RecEndTime = MythDroid.dateFmt.parse(
                                attr.getValue("recEndTs") //$NON-NLS-1$
                            );
                        } catch (ParseException e) {}

                        prog.Status = RecStatus.get(
                            Integer.valueOf(attr.getValue("recStatus")) //$NON-NLS-1$
                        );
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
                prog.StartTime = MythDroid.dateFmt.parse(
                    attr.getValue("startTime") //$NON-NLS-1$
                );
                prog.EndTime = MythDroid.dateFmt.parse(
                    attr.getValue("endTime") //$NON-NLS-1$
                );
            } catch (ParseException e) { 
                ErrUtil.postErr(ctx, e);
            }

        }

    }

    
    /**
     * Enum of recording statuses, with reverse lookup by code
     */
    public enum RecStatus {
        FAILED      (-9,    Messages.getString("Program.0")),  //$NON-NLS-1$
        TUNERBUSY   (-8,    Messages.getString("Program.13")), //$NON-NLS-1$
        LOWSPACE    (-7,    Messages.getString("Program.14")), //$NON-NLS-1$
        CANCELLED   (-6,    Messages.getString("Program.15")), //$NON-NLS-1$
        MISSED      (-5,    Messages.getString("Program.16")), //$NON-NLS-1$
        ABORTED     (-4,    Messages.getString("Program.17")), //$NON-NLS-1$
        RECORDED    (-3,    Messages.getString("Program.18")), //$NON-NLS-1$
        RECORDING   (-2,    Messages.getString("Program.19")), //$NON-NLS-1$
        WILLRECORD  (-1,    Messages.getString("Program.20")), //$NON-NLS-1$
        UNKNOWN     (0,     Messages.getString("Program.21")), //$NON-NLS-1$
        DONTRECORD  (1,     Messages.getString("Program.22")), //$NON-NLS-1$
        PREVIOUS    (2,     Messages.getString("Program.23")), //$NON-NLS-1$
        CURRENT     (3,     Messages.getString("Program.24")), //$NON-NLS-1$
        EARLIER     (4,     Messages.getString("Program.25")), //$NON-NLS-1$
        TOOMANY     (5,     Messages.getString("Program.26")), //$NON-NLS-1$
        NOTLISTED   (6,     Messages.getString("Program.27")), //$NON-NLS-1$
        CONFLICT    (7,     Messages.getString("Program.28")), //$NON-NLS-1$
        LATER       (8,     Messages.getString("Program.29")), //$NON-NLS-1$
        REPEAT      (9,     Messages.getString("Program.30")), //$NON-NLS-1$
        INACTIVE    (10,    Messages.getString("Program.31")), //$NON-NLS-1$
        NEVERRECORD (11,    Messages.getString("Program.32")), //$NON-NLS-1$
        OFFLINE     (12,    Messages.getString("Program.33")), //$NON-NLS-1$
        OTHER       (13,    Messages.getString("Program.34")); //$NON-NLS-1$

        private int     code;
        private String  msg;
        static final private Map<Integer, RecStatus> revMap =
            new HashMap<Integer, RecStatus>(24);

        static {
            for (RecStatus s : EnumSet.allOf(RecStatus.class))
                revMap.put(s.value(), s);
        }

        private RecStatus(int code, String str) {
            this.code = code;
            this.msg = str;
        }

        
        /**
         * Get human readable description of status
         * @return String containing description
         */
        public String msg() {
            return msg;
        }

        /**
         * Get integer code
         * @return code
         */
        public int value() {
            return code;
        }

        /**
         * Reverse lookup by integer code
         * @return RecStatus corresponding to code
         */
        public static RecStatus get(int value) {
            return revMap.get(value);
        }
    }

    static final private int  
        TITLE = 0,      SUBTITLE = 1,   DESC = 2,   CATEGORY = 3,   CHANID = 4, 
        CHANNEL = 6,    PATH = 8,       START = 11, END = 12,       STATUS = 21,
        RECSTART = 26,  RECEND = 27,    TYPE = 30,  
        TOTAL = MythDroid.protoVersion < 50 ? 46 : 47;

    /** Strings representing the relevant field */
    public String    Title, SubTitle, Category, Description, Channel, Path;
    /** Dates representing the relevant field */
    public Date      StartTime, EndTime, RecStartTime, RecEndTime;
    /** The recording status */
    public RecStatus Status;
    /** The channel ID */
    public int       ChanID;

    private String[] list  = null;

    /**
     * Construct a Program from a stringlist
     * @param list - a stringlist (e.g. from a backend)
     */
    public Program(String[] list, int off) {
        Title = list[off+TITLE];
        SubTitle = list[off+SUBTITLE];
        Category = list[off+CATEGORY];
        ChanID = Integer.valueOf(list[off+CHANID]);
        Description = list[off+DESC];
        Channel = list[off+CHANNEL];
        Path = list[off+PATH];
        StartTime = new Date(Long.valueOf(list[off+START]) * 1000);
        EndTime = new Date(Long.valueOf(list[off+END]) * 1000);
        RecStartTime = new Date(Long.valueOf(list[off+RECSTART]) * 1000);
        RecEndTime = new Date(Long.valueOf(list[off+RECEND]) * 1000);
        Status = RecStatus.get(Integer.valueOf(list[off+STATUS]));
    }

    /**
     * Construct a Program from an XML (DOM) Node 
     * @param item - a Program XML (DOM) node 
     */
    public Program(Node item) {

        NamedNodeMap attr = item.getAttributes();
        Title = attr.getNamedItem("title").getNodeValue(); //$NON-NLS-1$
        SubTitle = attr.getNamedItem("subTitle").getNodeValue(); //$NON-NLS-1$
        Category = attr.getNamedItem("category").getNodeValue(); //$NON-NLS-1$

        try {
            StartTime = MythDroid.dateFmt.parse(
                attr.getNamedItem("startTime").getNodeValue() //$NON-NLS-1$
            );
            EndTime = MythDroid.dateFmt.parse(
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
                RecStartTime = MythDroid.dateFmt.parse(
                    attr.getNamedItem("recStartTs").getNodeValue() //$NON-NLS-1$
                );
                RecEndTime = MythDroid.dateFmt.parse(
                    attr.getNamedItem("recEndTs").getNodeValue() //$NON-NLS-1$
                );
            } catch (ParseException e) {}

            Status =
                     RecStatus.get(Integer.valueOf(attr.getNamedItem(
                         "recStatus").getNodeValue())); //$NON-NLS-1$
        }
    }

    /** Construct an empty Program */
    public Program() {}

    /**
     * Get Program's playback ID (as used in frontend play command)
     * @return String of form "ChanID FormattedRecStartTime"
     */
    public String playbackID() {
        return ChanID + " " + MythDroid.dateFmt.format(RecStartTime); //$NON-NLS-1$
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
            final URL url =
                new URL(
                    MythDroid.beMgr.getStatusURL() +
                    "/Myth/GetPreviewImage?ChanId=" + ChanID + //$NON-NLS-1$
                    "&StartTime=" + MythDroid.dateFmt.format(RecStartTime) //$NON-NLS-1$
                );

            final URLConnection conn = url.openConnection();
            conn.connect();
            return BitmapFactory.decodeStream(conn.getInputStream());
        } catch (Exception e) { return null; }
    }

    /**
     * Get a formatted representation of StartTime
     * @return String of format "12:00, Mon 1 Jan 09"
     */
    public String startString() {
        return MythDroid.dispFmt.format(StartTime);
    }

    /**
     * Get a formatted representation of EndTime
     * @return String of format "12:00, Mon 1 Jan 09"
     */
    public String endString() {
        return MythDroid.dispFmt.format(EndTime);
    }

    /**
     * Flatten to a stringlist for use in backend commands
     * @return List of Strings, doesn't include stringlist separators
     */
    public String[] stringList() {
        
        if (list != null) return list;

        list = new String[TOTAL+1];
        Arrays.fill(list, ""); //$NON-NLS-1$

        list[TITLE+1] = Title;
        list[SUBTITLE+1] = SubTitle;
        list[DESC+1] = Description;
        list[CATEGORY+1] = Category;
        list[CHANID+1] = String.valueOf(ChanID);
        list[CHANNEL+1] = Channel;
        list[PATH+1] = Path == null ? "" : Path; //$NON-NLS-1$
        list[START+1] = String.valueOf(StartTime.getTime() / 1000);
        list[END+1] = String.valueOf(EndTime.getTime() / 1000);
        list[RECSTART+1] = String.valueOf(RecStartTime.getTime() / 1000);
        list[RECEND+1] = String.valueOf(RecEndTime.getTime() / 1000);
        list[STATUS+1] = String.valueOf(Status.value());
        return list;
        
    }

    /**
     * Get index in stringlist of the TYPE field 
     * @return int index of TYPE field 
     */
    static public int typeField() {
        return TYPE;
    }

    /**
     * Get total number of fields in stringlist
     * @return int total
     */
    static public int numFields() {
        return TOTAL;
    }

}