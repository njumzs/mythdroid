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

/**
 * Intent extras
 */
public enum Extras {
    LIVETV,             // (TVRemote)  We want livetv please
    JUMPCHAN,           // (TVRemote)  The channel we'd like to view
    FILENAME,           // (TVRemote,VideoPlayer)  The filename of the video
    TITLE,              // (TVRemote)  The title of the video
    DONTJUMP,           // (*Remote)   Don't jumpTo()
    GUIDE;              // (NavRemote) jumpTo() the program guide 
}
