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

package org.mythdroid.activities;

import org.mythdroid.fragments.RecEditFragment;

import android.os.Bundle;

/**
 * Simple Activity that houses a RecEditFragment 
 */
public class RecordingEdit extends MDFragmentActivity {
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (icicle != null) return;
        RecEditFragment ref = new RecEditFragment();
        ref.setArguments(getIntent().getExtras());
        getSupportFragmentManager().beginTransaction()
            .add(android.R.id.content, ref).commit();
    }

}
