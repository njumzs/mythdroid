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

import org.mythdroid.R;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;

/**
 * PreferenceActivity - the 'Settings' page.
 * Built from res.xml.preferences
 */
public class Prefs extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Context ctx = this;
        addPreferencesFromResource(R.xml.preferences);
        getPreferenceScreen().findPreference("backendAddr") //$NON-NLS-1$
        	.setOnPreferenceChangeListener( 
    			new OnPreferenceChangeListener() {
    				@Override
					public boolean onPreferenceChange(
						Preference pref, Object value
					) {
    					if (value == null) return true;
    					String addr = ((String)value).trim();
    					if (!addr.contains(" ")) return true; //$NON-NLS-1$
    					ErrUtil.err(ctx, Messages.getString("Prefs.1")); //$NON-NLS-1$
    					return false;
					}
    			}
        	);
    }
}
