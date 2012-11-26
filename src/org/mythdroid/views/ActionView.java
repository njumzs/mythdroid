package org.mythdroid.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/** Custom view for action bar items */
public class ActionView extends LinearLayout {

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
	@SuppressWarnings("javadoc")
    public ActionView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @SuppressWarnings("javadoc")
    public ActionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    @SuppressWarnings("javadoc")
    public ActionView(Context context) {
        super(context);
    }
    
    @Override
    protected void onMeasure(int width, int height) {
        setMinimumHeight(getRootView().getHeight());
        super.onMeasure(width, height);
    }

}
