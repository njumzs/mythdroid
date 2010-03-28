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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.mythdroid.resource.Messages;

public class Enums { 

    /**
     * Intent extras
     *
     */
    public enum Extras {
        LIVETV,             // (TVRemote)  We want livetv please
        JUMPCHAN,           // (TVRemote)  The channel we'd like to view
        FILENAME,           // (TVRemote)  The filename of the video
        TITLE,              // (TVRemote)  The title of the video
        DONTJUMP,           // (*Remote)   Don't jumpTo()
        GUIDE;              // (NavRemote) jumpTo() the program guide 
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

    /**
     * Enum of recording types, with reverse lookup by code
     */
    public enum RecType {
        NOT         (0,     "Don't record this program"), //$NON-NLS-1$
        SINGLE      (1,     "This showing only"), //$NON-NLS-1$
        CHANNEL     (3,     "At any time on this channel"), //$NON-NLS-1$
        ALL         (4,     "At any time on any channel"), //$NON-NLS-1$
        TIMESLOT    (2,     "At this time every day"), //$NON-NLS-1$
        WEEKSLOT    (5,     "At this time every week"), //$NON-NLS-1$
        FINDONE     (6,     "Find one showing"), //$NON-NLS-1$
        FINDDAILY   (9,     "Find one showing daily"), //$NON-NLS-1$
        FINDWEEKLY  (10,    "Find one showing weekly"), //$NON-NLS-1$
        OVERRIDE    (7,     "Manual override"), //$NON-NLS-1$
        DONT        (8,     "Don't record this showing"); //$NON-NLS-1$

        private int     code;
        private String  msg;
        static final private Map<Integer, RecType> revMap =
            new HashMap<Integer, RecType>(11);

        static {
            for (RecType s : EnumSet.allOf(RecType.class))
                revMap.put(s.value(), s);
        }

        private RecType(int code, String str) {
            this.code = code;
            this.msg = str;
        }

        /**
         * Get human readable description of recording type
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
        public static RecType get(int value) {
            return revMap.get(value);
        }
    }

    /**
     * Enum of recording duplicate searches, with reverse lookup by code
     */
    public enum RecDupIn {
        RECORDED    (0x01,     "In current recordings only"), //$NON-NLS-1$
        OLDRECORDED (0x02,     "In old recordings only"), //$NON-NLS-1$
        ALL         (0x0f,     "In current and old recordings"); //$NON-NLS-1$

        private int     code;
        private String  msg;
        static final private Map<Integer, RecDupIn> revMap =
            new HashMap<Integer, RecDupIn>(3);

        static {
            for (RecDupIn s : EnumSet.allOf(RecDupIn.class))
                revMap.put(s.value(), s);
        }

        private RecDupIn(int code, String str) {
            this.code = code;
            this.msg = str;
        }

        /**
         * Get human readable description of duplicate search
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
        public static RecDupIn get(int value) {
            return revMap.get(value);
        }
    }
    
    /**
     * Enum of recording epsidode filters, with reverse lookup by code
     */
    public enum RecEpiFilter {
        NONE        (0x00,     "Record all episodes"), //$NON-NLS-1$
        NEW         (0x10,     "Record new episodes only"), //$NON-NLS-1$
        EXREPEATS   (0x20,     "Exclude old episodes"), //$NON-NLS-1$
        EXGENERIC   (0x40,     "Exclude unidentified episodes"), //$NON-NLS-1$
        FIRSTNEW    (0x80,     "Record 1st showings of new episodes"); //$NON-NLS-1$

        private int     code;
        private String  msg;
        static final private Map<Integer, RecEpiFilter> revMap =
            new HashMap<Integer, RecEpiFilter>(4);

        static {
            for (RecEpiFilter s : EnumSet.allOf(RecEpiFilter.class))
                revMap.put(s.value(), s);
        }

        private RecEpiFilter(int code, String str) {
            this.code = code;
            this.msg = str;
        }

        /**
         * Get human readable description of duplicate search
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
        public static RecEpiFilter get(int value) {
            return revMap.get(value);
        }
    }

    /**
     * Enum of recording duplicate matching methods, with reverse lookup by code
     */
    public enum RecDupMethod {
        NONE        (1,     "Nothing"), //$NON-NLS-1$
        SUB         (2,     "Subtitle"), //$NON-NLS-1$
        DESC        (4,     "Description"), //$NON-NLS-1$
        SUBANDDESC  (6,     "Subtitle and description"), //$NON-NLS-1$
        SUBTHENDESC (8,     "Subtitle then description"); //$NON-NLS-1$

        private int     code;
        private String  msg;
        static final private Map<Integer, RecDupMethod> revMap =
            new HashMap<Integer, RecDupMethod>(5);

        static {
            for (RecDupMethod s : EnumSet.allOf(RecDupMethod.class))
                revMap.put(s.value(), s);
        }

        private RecDupMethod(int code, String str) {
            this.code = code;
            this.msg = str;
        }

        /**
         * Get human readable description of duplicate matching method
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
        public static RecDupMethod get(int value) {
            return revMap.get(value);
        }
    }
    
    /** Program categories and their colours */
    public enum Category {
        unknown         (0xff404040),   shopping        (0xff301040),
        educational     (0xff202080),   musical         (0xffa00040),
        news            (0xff00aa00),   reality         (0xff500020),
        cooking         (0xff003080),   documentary     (0xff5000a0),
        doc             (0xff5000a0),   sport           (0xff204040),
        sports          (0xff204040),   sportsevent     (0xff204040),
        sportstalk      (0xff202040),   music           (0xff6020a0),
        musicandarts    (0xff6020a0),   movies          (0xff108000),
        movie           (0xff108000),   film            (0xff108000),
        drama           (0xff0020c0),   crime           (0xff300080),
        animals         (0xff00c040),   nature          (0xff00c040),
        sciencenature   (0xff00c040),   comedy          (0xff808000),
        comedydrama     (0xff808010),   romancecomedy   (0xff808020),
        sitcom          (0xff804000),   scifi           (0xff106060),
        sciencefiction  (0xff106060),   scififantasy    (0xff106060),
        fantasy         (0xff106060),   horror          (0xffc03030),
        suspense        (0xffc03030),   action          (0xffa00060),
        actionadv       (0xffa04060),   adventure       (0xffa04000),
        romance         (0xff800020),   health          (0xff2000a0),
        homehowto       (0xff804000),   homeimprovement (0xff804000),
        howto           (0xff804000),   housegarden     (0xff804000),
        foodtravel      (0xff808000),   children        (0xff108040),
        kids            (0xff108040),   animated        (0xff108060),
        gameshow        (0xff703000),   interests       (0xff703030),
        talkshow        (0xff007070),   biography       (0xff500080),
        fashion         (0xff0060a0),   docudrama       (0xff8000c0),
        selfimprovement (0xff800040),   exercise        (0xff002080),
        auto            (0xffa03000),   soap            (0xff408020),
        soaps           (0xff408020);

        private int color;

        private Category(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }

    public enum Key {

        ZERO    ("0"), ONE  ("1"), TWO  ("2"), THREE    ("3"), FOUR ("4"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        FIVE    ("5"), SIX  ("6"), SEVEN("7"), EIGHT    ("8"), NINE ("9"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        
        ESCAPE              ("escape"),  //$NON-NLS-1$
        GUIDE               ("s"), //$NON-NLS-1$
        VOL_UP              ("]"), //$NON-NLS-1$
        VOL_DOWN            ("["), //$NON-NLS-1$
        VOL_MUTE            ("backslash"),  //$NON-NLS-1$
        UP                  ("up"), //$NON-NLS-1$
        DOWN                ("down"), //$NON-NLS-1$
        LEFT                ("left"), //$NON-NLS-1$
        RIGHT               ("right"), //$NON-NLS-1$
        ENTER               ("enter"),  //$NON-NLS-1$
        PAUSE               ("p"), //$NON-NLS-1$
        RECORD              ("r"), //$NON-NLS-1$
        EDIT                ("e"), //$NON-NLS-1$
        SEEK_BACK           ("<"), //$NON-NLS-1$
        SEEK_FORWARD        (">"), //$NON-NLS-1$
        SKIP_BACK           ("left"), //$NON-NLS-1$
        SKIP_FORWARD        ("right"), //$NON-NLS-1$
        INFO                ("i"), //$NON-NLS-1$
        MENU                ("m"), //$NON-NLS-1$
        SKIP_COMMERCIAL     ("z"), //$NON-NLS-1$
        BACKSPACE           ("backspace"), //$NON-NLS-1$
        SPACE               ("space"), //$NON-NLS-1$
        TAB                 ("tab"), //$NON-NLS-1$
        MUSIC_NEXT          (">"), //$NON-NLS-1$
        MUSIC_PREV          ("<"), //$NON-NLS-1$
        MUSIC_FFWD          ("pagedown"), //$NON-NLS-1$
        MUSIC_REWIND        ("pageup"), //$NON-NLS-1$
        MUSIC_SHUFFLE       ("1"), //$NON-NLS-1$
        MUSIC_REPEAT        ("2"), //$NON-NLS-1$
        MUSIC_EDIT          ("3"), //$NON-NLS-1$
        MUSIC_VISUALISE     ("4"), //$NON-NLS-1$
        MUSIC_CHANGE_VISUAL ("6"), //$NON-NLS-1$
        NUMPAD              ("numpad"); //$NON-NLS-1$

        private String str = null;

        private Key(String str) {
            this.str = str;
        }

        public String str() {
            return str;
        }

    }
    
}