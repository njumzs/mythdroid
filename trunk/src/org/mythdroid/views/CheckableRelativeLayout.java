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

package org.mythdroid.views;

import org.mythdroid.R;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.CheckedTextView;
import android.widget.RelativeLayout;

/**
 * A RelativeLayout that implements checkable and passes on the checked state
 * to a CheckedTextView with id title
 */
public class CheckableRelativeLayout extends RelativeLayout 
    implements Checkable {

    private boolean checked = false;
    private CheckedTextView ctv = null;
    
    /**
     * Constructor
     */
    public CheckableRelativeLayout(
        Context context, AttributeSet attrs, int defStyle
    ) {
        super(context, attrs, defStyle);
    }
    
    /**
     * Constructor
     */
    public CheckableRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Constructor
     */
    public CheckableRelativeLayout(Context context) {
        super(context);
    }

    @Override
    public boolean isChecked() {
        return checked;
    }
    
    @Override
    public void setChecked(boolean check) {
        checked = check;
        if (ctv != null)
            ctv.setChecked(check);
    }
    
    @Override
    public void toggle() {
        setChecked(!checked);
    }
    
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ctv = (CheckedTextView)findViewById(R.id.title);
    }
    
}
