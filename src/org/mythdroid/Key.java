/*
    MythDroid: Android MythTV Remote
    
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

public enum Key {

    ZERO    ("0"), ONE  ("1"), TWO  ("2"), THREE    ("3"), FOUR ("4"),
    FIVE    ("5"), SIX  ("6"), SEVEN("7"), EIGHT    ("8"), NINE ("9"),
    
    ESCAPE          ("escape"), 
    GUIDE           ("s"),
    VOL_UP          ("]"),
    VOL_DOWN        ("["),
    VOL_MUTE        ("backslash"), 
    UP              ("up"),
    DOWN            ("down"),
    LEFT            ("left"),
    RIGHT           ("right"),
    ENTER           ("enter"), 
    PAUSE           ("p"),
    RECORD          ("r"),
    EDIT            ("e"),
    SEEK_BACK       ("<"),
    SEEK_FORWARD    (">"),
    SKIP_BACK       ("left"),
    SKIP_FORWARD    ("right"),
    INFO            ("i"),
    MENU            ("m"),
    SKIP_COMMERCIAL ("z"),
    BACKSPACE       ("backspace"),
    SPACE           ("space"),
    TAB             ("tab"),
    NUMPAD          ("numpad");

    private String str = null;

    private Key(String str) {
        this.str = str;
    }

    public String str() {
        return str;
    }

}
