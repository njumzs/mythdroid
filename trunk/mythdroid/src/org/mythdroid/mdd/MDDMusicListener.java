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

package org.mythdroid.mdd;

public interface MDDMusicListener {
    /**
     * Called on receipt of MUSIC events
     * @param artist - String containing the name of the artist
     * @param album - String containing the name of the album
     * @param track - String containing the name of the track
     * @param artid - int representing the albumart id
     */
    public void onMusic(
        String artist, String album, String track, int artid
    );
    /**
     * Called on receipt of MUSIC PROGRESS events
     * @param pos - int representing the new position
     */
    public void onProgress(int pos);
    /**
     * Called on MUSIC PLAYER PROPERTY events
     * @param prop - String containing the name of the property
     * @param value - int representing the property value
     */
    public void onPlayerProp(String prop, int value);
}
