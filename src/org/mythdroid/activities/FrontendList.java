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

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.DatabaseUtil;
import org.mythdroid.util.ErrUtil;

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

import android.R.drawable;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import java.util.ArrayList;


/** ListActivity, displays, and lets the user manage, a list of frontends */
public class FrontendList extends ListActivity implements
    DialogInterface.OnClickListener {

    final static private int
        ADD_DIALOG  = 0, DEFAULT_DIALOG  = 1, EDIT_DIALOG = 2;

    final private Context ctx             = this;

    private AlertDialog   feEditor        = null;
    private int           clickedPosition = 0;
    private View          ftr             = null;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final View hdr = getLayoutInflater().inflate(
            R.layout.frontend_list_item, null
        );

        ((TextView)(hdr.findViewById(R.id.name))).setText(R.string.addFe);
        ((TextView)(hdr.findViewById(R.id.addr))).setText(R.string.clickAddFe);
        
        ListView lv = getListView();

        lv.addHeaderView(hdr);

        ftr = getLayoutInflater().inflate(
            R.layout.frontend_list_item, null
        );

        ((TextView)(ftr.findViewById(R.id.name))).setText(R.string.defFe);

        String defFE = DatabaseUtil.getDefault(this);
        
        if (defFE != null)
            ((TextView)(ftr.findViewById(R.id.addr)))
                .setText(DatabaseUtil.getDefault(this));
        
        lv.addHeaderView(ftr);
        lv.setPadding(0, 4, 0, 0);

        Cursor c = DatabaseUtil.getFrontends(this);

        setListAdapter(
            new SimpleCursorAdapter(
                this, R.layout.frontend_list_item, c,
                new String[] { "addr", "name", "hwaddr" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                new int[] { R.id.addr, R.id.name, R.id.hwaddr }
            )
        );
        
        if (icicle != null)
            clickedPosition = icicle.getInt("clickedPosition"); //$NON-NLS-1$

    }
    
    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putInt("clickedPosition", clickedPosition); //$NON-NLS-1$
    }

    @Override
    public Dialog onCreateDialog(int id) {

        View editor = null;
        
        switch (id) {

            case ADD_DIALOG:
                editor = getLayoutInflater().inflate(
                    R.layout.frontend_editor, null
                );
                feEditor = new AlertDialog.Builder(this)
                               .setView(editor)
                               .setPositiveButton(R.string.save, this)
                               .setNegativeButton(R.string.cancel, this)
                               .create();
                break;

            case EDIT_DIALOG:
                editor = getLayoutInflater().inflate(
                    R.layout.frontend_editor, null
                );
                feEditor = new AlertDialog.Builder(this)
                               .setView(editor)
                               .setPositiveButton(R.string.save, this)
                               .setNeutralButton(R.string.delete, this)
                               .setNegativeButton(R.string.cancel, this)
                               .create();
                break;

            case DEFAULT_DIALOG:
                return createDefaultFrontendDialog();
                
        }

        return feEditor;
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog) {

        switch (id) {

            case EDIT_DIALOG:

                View v = this.getListView().getChildAt(clickedPosition);
                if (v == null) return;
                
                CharSequence name =
                    ((TextView)v.findViewById(R.id.name)).getText();
                CharSequence addr =
                    ((TextView)v.findViewById(R.id.addr)).getText();
                CharSequence hwaddr =
                    ((TextView)v.findViewById(R.id.hwaddr)).getText();

                ((EditText)dialog.findViewById(R.id.name)).setText(name);
                ((EditText)dialog.findViewById(R.id.addr)).setText(addr);
                ((EditText)dialog.findViewById(R.id.hwaddr)).setText(hwaddr);
                break;

            case ADD_DIALOG:

                ((EditText)dialog.findViewById(R.id.name)).setText(""); //$NON-NLS-1$
                ((EditText)dialog.findViewById(R.id.addr)).setText(""); //$NON-NLS-1$
                ((EditText)dialog.findViewById(R.id.hwaddr)).setText(""); //$NON-NLS-1$
                break;

            case DEFAULT_DIALOG:
                prepareDefaultFrontendDialog(dialog);
                break;
                
        }
    }

    @Override
    public void onListItemClick(ListView list, View item, int pos, long itemid)
    {
        clickedPosition = pos;
        switch (pos) {
            case ADD_DIALOG:
                showDialog(ADD_DIALOG);
                break;
            case DEFAULT_DIALOG:
                showDialog(DEFAULT_DIALOG);
                break;
            default:
                showDialog(EDIT_DIALOG);
                break;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

        long rowID = getListAdapter().getItemId(clickedPosition - 2);

        switch (which) {

            case AlertDialog.BUTTON_POSITIVE:

                String name   = ((EditText)feEditor.findViewById(R.id.name))
                                  .getText().toString();
                String addr   = ((EditText)feEditor.findViewById(R.id.addr))
                                  .getText().toString();
                String hwaddr =
                    ((EditText)feEditor.findViewById(R.id.hwaddr))
                        .getText().toString();

                if (name.length() == 0 || addr.length() == 0) {
                    ErrUtil.err(ctx, Messages.getString("FrontendList.4")); //$NON-NLS-1$
                    dialog.dismiss();
                    return;
                }

                if (rowID < 1) {
                    if (!DatabaseUtil.insert(this, name, addr, hwaddr, false))
                        ErrUtil.err(
                            ctx, Messages.getString("FrontendList.5") + name //$NON-NLS-1$
                        );
                    if (DatabaseUtil.getFrontendNames(ctx).size() == 1)
                        setDefaultFrontend(name);
                }

                else
                    DatabaseUtil.update(this, rowID, name, addr, hwaddr);

                break;

            case AlertDialog.BUTTON_NEUTRAL:

                DatabaseUtil.delete(this, rowID);
                String n = ((EditText)feEditor.findViewById(R.id.name))
                             .getText().toString();
                if (Globals.curFe != null && Globals.curFe.equals(n))
                    Globals.curFe = DatabaseUtil.getDefault(ctx);
                
                break;

        }

        dialog.dismiss();
    }

    /** Create a dialog allowing user to choose default frontend */
    private Dialog createDefaultFrontendDialog() {

        final AlertDialog d = new AlertDialog.Builder(ctx)
            .setItems(new String[] {}, null)
            .setIcon(drawable.ic_menu_upload_you_tube)
            .setTitle(R.string.chFe)
            .create();

        d.getListView().setOnItemClickListener(
            new OnItemClickListener() {
                @Override
                public void onItemClick(
                    AdapterView<?> av, View v, int pos, long id
                ) {
                    setDefaultFrontend((String)av.getAdapter().getItem(pos));
                    d.dismiss();
                }
            }
        );

        return d;
    }

    private void prepareDefaultFrontendDialog(final Dialog dialog) {

        ArrayList<String> list = DatabaseUtil.getFrontendNames(this);

        list.add(Messages.getString("MDActivity.0")); // Here //$NON-NLS-1$

        ((AlertDialog)dialog).getListView().setAdapter(
            new ArrayAdapter<String>(
                ctx, R.layout.simple_list_item_1, list
            )
        );

    }
    
    private void setDefaultFrontend(String name) {
        DatabaseUtil.updateDefault(ctx, name);
        Globals.curFe = name;
        ((TextView)(ftr.findViewById(R.id.addr))).setText(name);
    }

}
