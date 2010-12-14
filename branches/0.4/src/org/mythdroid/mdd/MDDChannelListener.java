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

/**
 * Callbacks for MDD CHANNEL and related events
 */
public interface MDDChannelListener {
    /**
     * Called on receipt of CHANNEL events
     * @param channel - String containing the name of the channel
     * @param title - String containing the title of the program
     * @param subtitle - String containing the subtitle of the program
     */
    public void onChannel(String channel, String title, String subtitle);
    /**
     * Called on receipt of PROGRESS events
     * @param pos - int representing current position
     */
    public void onProgress(int pos);
    /**
     * Called on receipt of EXIT event 
     */
    public void onExit();
}