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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/** ListActivity, displays and lets user manage a list of frontends */
public class FrontendList extends ListActivity implements
    DialogInterface.OnClickListener {

    final static private int ADD_DIALOG  = 0, EDIT_DIALOG = 1;

    final private Context ctx             = this;
    
    private Cursor        c               = null;
    private AlertDialog   feEditor        = null;
    private int           clickedPosition = -1;
    private View          clickedView     = null;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final View hdr = getLayoutInflater().inflate(
            R.layout.frontend_list_item, null
        );

        ((TextView)(hdr.findViewById(R.id.fe_name_text)))
            .setText(R.string.add_fe);
        ((TextView)(hdr.findViewById(R.id.fe_addr_text)))
            .setText(R.string.click_add_fe);

        getListView().addHeaderView(hdr);
        getListView().setPadding(0, 4, 0, 0);

        c = FrontendDB.getFrontends(this);

        setListAdapter(
            new SimpleCursorAdapter(
                this, R.layout.frontend_list_item, c, 
                new String[] { "addr", "name", "hwaddr" },
                new int[] { 
                    R.id.fe_addr_text, R.id.fe_name_text, R.id.fe_hwaddr_text 
                }
            )
        );

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        c.close();
        FrontendDB.close();
    }

    @Override
    public Dialog onCreateDialog(int id) {

        View editor = getLayoutInflater().inflate(
            R.layout.frontend_editor, null
        );

        switch (id) {
            
            case ADD_DIALOG:
                feEditor = new AlertDialog.Builder(this)
                               .setView(editor)
                               .setPositiveButton(R.string.save, this)
                               .setNegativeButton(R.string.cancel, this)
                               .create();
                break;
                
            case EDIT_DIALOG:
                feEditor = new AlertDialog.Builder(this)
                               .setView(editor)
                               .setPositiveButton(R.string.save, this)
                               .setNeutralButton(R.string.delete, this)
                               .setNegativeButton(R.string.delete, this)
                               .create();
                break;
                
        }

        return feEditor;
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog) {
        
        switch (id) {
            
            case EDIT_DIALOG:
                
                CharSequence name = ((TextView)clickedView
                                        .findViewById(R.id.fe_name_text))
                                        .getText();
                CharSequence addr = ((TextView)clickedView
                                        .findViewById(R.id.fe_addr_text))
                                        .getText();
                CharSequence hwaddr = ((TextView)clickedView
                                        .findViewById(R.id.fe_hwaddr_text))
                                        .getText();
                
                ((EditText)dialog.findViewById(R.id.fe_name)).setText(name);
                ((EditText)dialog.findViewById(R.id.fe_addr)).setText(addr);
                ((EditText)dialog.findViewById(R.id.fe_hwaddr)).setText(hwaddr);
                break;
                
            case ADD_DIALOG:
                
                ((EditText)dialog.findViewById(R.id.fe_name)).setText("");
                ((EditText)dialog.findViewById(R.id.fe_addr)).setText("");
                ((EditText)dialog.findViewById(R.id.fe_hwaddr)).setText("");
                break;
                
        }
    }

    @Override
    public void onListItemClick(ListView list, View item, int pos, long itemid) {
        clickedPosition = pos;
        clickedView = item;
        showDialog(pos == 0 ? ADD_DIALOG : EDIT_DIALOG);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

        long rowID = getListAdapter().getItemId(clickedPosition - 1);

        switch (which) {
            
            case AlertDialog.BUTTON_POSITIVE:
                
                String name = ((EditText)feEditor.findViewById(R.id.fe_name))
                                  .getText().toString();
                String addr = ((EditText)feEditor.findViewById(R.id.fe_addr))
                                  .getText().toString();
                String hwaddr = ((EditText)
                    feEditor.findViewById(R.id.fe_hwaddr))
                            .getText().toString();

                if (name.length() == 0 || addr.length() == 0) {
                    Util.err(ctx, Messages.getString("FrontendList.4"));
                    dialog.dismiss();
                    return;
                }

                if (rowID < 1) {
                    if (!FrontendDB.insert(this, name, addr, hwaddr))
                        Util.err(
                            ctx, Messages.getString("FrontendList.5") + name
                        );
                }
                
                else
                    FrontendDB.update(this, rowID, name, addr, hwaddr);

                break;
                
            case AlertDialog.BUTTON_NEUTRAL:
                
                FrontendDB.delete(this, rowID);
                break;
                
        }

        c.requery();
        dialog.dismiss();
    }
}