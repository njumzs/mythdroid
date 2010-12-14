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

import org.mythdroid.remote.MusicRemote;
import org.mythdroid.resource.Messages;

import android.R.layout;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class Media extends MDListActivity implements
    AdapterView.OnItemLongClickListener{

    /** Entries for the menu */
    final private static String[] MenuItems =
        { Messages.getString("Media.0"), Messages.getString("Media.1"), Messages.getString("Media.2") }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    
    @Override
    public void onCreate(Bundle icicle) {
        
        super.onCreate(icicle);

        ArrayAdapter<String> menuAdapter = new ArrayAdapter<String>(
            this, layout.simple_list_item_1, MenuItems
        );
        
        setListAdapter(menuAdapter);
        getListView().setOnItemLongClickListener(this);
    
    }
    
    @Override
    public void onListItemClick(ListView list, View item, int pos, long id) {
        
        final String action = (String)list.getItemAtPosition(pos);
        Class<?> activity = null;

        if      (action.equals(Messages.getString("Media.0")))  activity = Videos.class; //$NON-NLS-1$
        else if (action.equals(Messages.getString("Media.1")))  activity = MusicRemote.class; //$NON-NLS-1$
        else if (action.equals(Messages.getString("Media.2")))  activity = Gallery.class; //$NON-NLS-1$
        
        startActivity(
            new Intent().setClass(this, activity)
        );

    }
    
    @Override
    public boolean onItemLongClick(
        AdapterView<?> adapter, View item, int pos, long itemid
    ) {
        
        final String action = (String)adapter.getItemAtPosition(pos);
      
        if (action.equals(Messages.getString("Media.1"))) { //$NON-NLS-1$
            nextActivity = MusicRemote.class;
            showDialog(FRONTEND_CHOOSER);
            return true;
        }
        
        return false;
        
    }
    
}
