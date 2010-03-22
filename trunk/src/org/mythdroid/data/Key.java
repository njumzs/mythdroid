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

/**
 * Enum of keys
 */
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

    /**
     * Get the associated String
     * @return String 
     */
    public String str() {
        return str;
    }

}
