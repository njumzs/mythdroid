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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

/** 
 * An ImageView that can grow to match a desired dimension, preserving aspect
 */
public class PreviewImageView extends android.widget.ImageView {
    
    private int sWidth = 0, sHeight = 0;
    
    @SuppressWarnings("javadoc")
    public PreviewImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    @SuppressWarnings("javadoc")
    public PreviewImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    @SuppressWarnings("javadoc")
    public PreviewImageView(Context context) {
        super(context);
    }
    
    @Override
    protected void onMeasure(int x, int y) {
        
        if (sWidth == 0 && sHeight == 0) {
            super.setMeasuredDimension(x, y);
            return;
        }
        
        Drawable d  = getDrawable();
        
        if (d == null) {
            setMeasuredDimension(0, 0);
            return;
        }
        
        int dWidth  = d.getIntrinsicWidth();
        int dHeight = d.getIntrinsicHeight();
        
        int w = 0, h = 0;
        
        float daspect = (float)dWidth/(float)dHeight;
        
        if (sWidth != 0) {
            w = sWidth;
            h = (int)(sWidth / daspect);
        }
        else {
            h = sHeight;
            w = (int)(sHeight * daspect);
        }
        
        setMeasuredDimension(w, h);
            
    }
    
    /**
     * Set desired width, view will be scaled to this width
     * @param w width in pixels
     */
    public void setWidth(int w) {
        sWidth = w;
        sHeight = 0;
    }
    
    /**
     * Set desired height, view will be scaled to this height
     * @param h height in pixels
     */
    public void setHeight(int h) {
        sHeight = h;
        sWidth = 0;
    }

}
