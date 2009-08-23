package org.mythdroid;

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
        { "Videos", "Music", "Pictures" };
    
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

        if      (action.equals("Videos"))      activity = Videos.class;
        else if (action.equals("Music"))       activity = MusicRemote.class;
        else if (action.equals("Pictures"))    activity = Gallery.class;
        
        startActivity(
            new Intent().setClass(this, activity)
        );

    }
    
    @Override
    public boolean onItemLongClick(
        AdapterView<?> adapter, View item, int pos, long itemid
    ) {
        
        final String action = (String)adapter.getItemAtPosition(pos);
      
        if (action.equals("Music")) {
            nextActivity = MusicRemote.class;
            showDialog(FRONTEND_CHOOSER);
            return true;
        }
        
        return false;
        
    }
    
}
