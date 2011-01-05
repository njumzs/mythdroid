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
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Custom layout for the numpad (a 4x3 grid) */
public class NumPadLayout extends ViewGroup {

    private int rows = 4, columns = 3;

    /**
     * Constructor
     * @param ctx Context
     */
    public NumPadLayout(Context ctx) {
        super(ctx);
    }

    /**
     * Constructor
     * @param ctx Context
     * @param attrs AttributeSet 
     */
    public NumPadLayout(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
    }

    /**
     * Constructor
     * @param ctx Context
     * @param attrs AttributeSet
     * @param defStyle Default style
     */
    public NumPadLayout(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

        final int pad =
            getResources().getConfiguration().orientation == 
                Configuration.ORIENTATION_PORTRAIT 
                    ? 10 : 2;

        final View child0 = getChildAt(0);
        final int yInc = (getHeight() - pad * 2) / rows;
        final int xInc = getWidth() / columns;
        final int childWidth = child0.getMeasuredWidth();
        final int childHeight = child0.getMeasuredHeight();
        final int xOffset = (xInc - childWidth) / 2;
        final int yOffset = (yInc - childHeight) / 2;

        int y = pad;
        for (int row = 0; row < rows; row++) {
            int x = 0;
            for (int col = 0; col < columns; col++) {
                View child = getChildAt(row * columns + col);
                child.layout(
                    x + xOffset, y + yOffset,
                    x + xOffset + childWidth, y + yOffset + childHeight
                );
                x += xInc;
            }
            y += yInc;
        }
        
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        
        int width = 0;
        int height = 0;
        final int pad =
            getResources().getConfiguration().orientation == 
                Configuration.ORIENTATION_PORTRAIT
                    ? 10 : 2;

        View child = getChildAt(0);
        child.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int childWidth = child.getMeasuredWidth();
        int childHeight = child.getMeasuredHeight();

        // All cells are going to be the size of the first child
        width += columns * childWidth;
        height += rows * childHeight + pad * 2;

        width = resolveSize(width, widthMeasureSpec);
        height = resolveSize(height, heightMeasureSpec);
        setMeasuredDimension(width, height);
        
    }

}
