package org.mythdroid.views;

import android.content.Context;
import android.util.AttributeSet;

/**
 * Our own version of VideoView that allows us to change the video scaling
 */
public class MDVideoView extends android.widget.VideoView {

    /** Constructor */
    public MDVideoView(Context context) {
        super(context);
    }
    
    /** Constructor */
    public MDVideoView(Context context, AttributeSet attr) {
        super(context, attr);
    }
    
    /** Constructor */
    public MDVideoView(Context context, AttributeSet attr, int defStyle) {
        super(context, attr, defStyle);
    }
    
    private enum DisplayMode {
        ORIGINAL,   // Original aspect ratio
        FULL        // Scaled to full screen
     };


     private DisplayMode screenMode = DisplayMode.ORIGINAL;
     private int vWidth, vHeight;

     @Override
     protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
         
         int width = getDefaultSize(vWidth, widthMeasureSpec);
         int height = getDefaultSize(vHeight, heightMeasureSpec);
         
         if (screenMode == DisplayMode.ORIGINAL) 
            if (vWidth > 0 && vHeight > 0) 
                if ( vWidth * height  > width * vHeight ) 
                    // video height exceeds screen, shrink it
                    height = width * vHeight / vWidth;
                else if ( vWidth * height  < width * vHeight ) 
                    // video width exceeds screen, shrink it
                    width = height * vWidth / vHeight;

         setMeasuredDimension(width, height);
     }
     
    /**
     * Change the video scaling mode 
     */
    public String nextVideoScale() {
         
         if (screenMode == DisplayMode.ORIGINAL)
             screenMode = DisplayMode.FULL;
         else
             screenMode = DisplayMode.ORIGINAL;
         
         requestLayout();
         invalidate();
         
         return screenMode.toString();
     }

    /**
     * Set the original video size
     * @param width - original width of video
     * @param height - original size of video
     */
    public void setVideoSize(int width, int height) {
        vWidth = width;
        vHeight = height;
        getHolder().setFixedSize(width, height);
        requestLayout();
        invalidate();
    } 

}
