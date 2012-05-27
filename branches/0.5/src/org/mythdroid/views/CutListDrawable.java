package org.mythdroid.views;

import java.util.ArrayList;

import org.mythdroid.data.Program.Commercial;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * Our own SeekBar that can draw cuts on itself
 */
public class CutListDrawable extends Drawable {

    private ArrayList<Commercial> cuts = null;
    private float fps                  = 0;
    private int end                    = 0;
    private Rect rect                  = null;
    private Paint paint                = null;
    private static float[] positions   = new float[] { 0, 0.4f, 1 };
    private static int[]   colors      =
        new int[] { 0xff604000, 0xff402000, 0xfff08000 };
    
    
    /**
     * Create a new cut list drawable
     * @param cuts
     */
    public CutListDrawable(
            ArrayList<Commercial> cuts, float fps, int end, Rect rect
    ) {
        this.cuts = cuts;
        this.fps  = fps;
        this.end  = end;
        this.rect = rect;
        paint     = new Paint();
        setBounds(rect);
    }
    
    @Override
    public void draw(Canvas canvas) {
        
        int top    = rect.top;
        int bottom = rect.bottom;
        
        // Hacky? Honeycomb has a itty bitty progress bar
        if (Integer.parseInt(Build.VERSION.SDK) >= 11) {
            top = 12;
            bottom = 20;
        }
        
        paint.setShader(
            new LinearGradient(
                0, rect.bottom, 0, 0, colors, positions, Shader.TileMode.CLAMP
            )
        );
        
        for (Commercial c : cuts) {
            float left  = rect.left + ((c.start / fps) * rect.right) / end;
            float right = rect.left + ((c.end / fps) * rect.right) / end;
            canvas.drawRect(left, top, right, bottom, paint);
        }
    }

    @Override
    public int getOpacity() { return PixelFormat.OPAQUE; }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter cf) {}
    

}
