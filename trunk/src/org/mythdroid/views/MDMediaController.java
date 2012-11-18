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

import java.lang.ref.WeakReference;
import java.util.Locale;

import org.mythdroid.R;

import android.content.Context;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import java.util.Formatter;

/**
 * A view containing controls for a MediaPlayer. 
 */
public class MDMediaController extends MediaController {
    
    private MediaPlayerControl   player;
    private Context              ctx;
    private PopupWindow          popupWindow;
    private View                 anchor, rootView;
    private ProgressBar          progress;
    private TextView             endTime, curTime, titleView;
    private String               title;
    private int                  duration;
    private boolean              showing, dragging;
    private ImageButton          pauseButton;
    private Button               moveToButton;
    private AudioManager         audioMgr;
    private StringBuilder        sb;
    private Formatter            formatter;
    private MessageHandler       handler;
    private View.OnClickListener moveToListener;
    
    private static final int    timeout = 3000, FADE_OUT = 1, SHOW_PROGRESS = 2;

    /**
     * Constructor
     * @param context Context
     */
    public MDMediaController(Context context) {
        super(context, null);
        if (initController(context))
            initFloatingWindow();
        handler = new MessageHandler(this);
    }

    @Override
    public void setAnchorView(View view) {
        anchor = view;
        removeAllViews();
        rootView = makeControllerView();
        initControllerView(rootView);
        popupWindow.setContentView(rootView);
        popupWindow.setWidth(LayoutParams.FILL_PARENT);
        popupWindow.setHeight(LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void show() {
        show(timeout);
    }

    @Override
    public void show(int timeout) {
        
        if (!showing && anchor != null && anchor.getWindowToken() != null) {
            
            if (pauseButton != null)
                pauseButton.requestFocus();

            int[] location = new int[2];

            anchor.getLocationOnScreen(location);
            Rect anchorRect =
                new Rect(
                    location[0], location[1],
                    location[0] + anchor.getWidth(),
                    location[1] + anchor.getHeight()
                );

            popupWindow.setAnimationStyle(
                android.R.style.Animation_Translucent
            );
            popupWindow.showAtLocation(
                anchor, Gravity.NO_GRAVITY,
                anchorRect.left, anchorRect.bottom
            );

            showing = true;
            
        }
        
        updatePausePlay();
        handler.sendEmptyMessage(SHOW_PROGRESS);

        if (timeout != 0) {
            handler.removeMessages(FADE_OUT);
            handler.sendMessageDelayed(
                handler.obtainMessage(FADE_OUT), timeout
            );
        }
        
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        show(timeout);
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        show(timeout);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        
        int keyCode = event.getKeyCode();
        
        if (
            event.getRepeatCount() == 0 && 
            (
                keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_SPACE
            )
        ) {
            doPauseResume();
            show(timeout);
            if (pauseButton != null)
                pauseButton.requestFocus();
            return true;
        } 
        
        if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
            if (player.isPlaying()) {
                player.pause();
                updatePausePlay();
            }
            return true;
        }
        
        if (
            keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_MENU
        ) {
            hide();
            return true;
        }
        
        show(timeout);
        
        return super.dispatchKeyEvent(event);
        
    }

    @Override
    public boolean isShowing() {
        return showing;
    }

    @Override
    public void hide() {
        
        if (anchor == null) return;

        if (showing) {
            try {
                handler.removeMessages(SHOW_PROGRESS);
                popupWindow.dismiss();
            } catch (IllegalArgumentException ex) {}
            showing = false;
        }
        
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        if (pauseButton != null)
            pauseButton.setEnabled(enabled);
        if (moveToButton != null)
            moveToButton.setEnabled(enabled);
        if (progress != null)
            progress.setEnabled(enabled);
        super.setEnabled(enabled);
    }
    
    @Override
    public void setMediaPlayer(MediaPlayerControl player) {
        this.player = player;
        updatePausePlay();
    }
    
    /**
     * Set the title of the program / video
     * @param name String containing title of program or video
     */
    public void setTitle(String name) {
        title = name;
        if (titleView != null)
            titleView.setText(title);
    }
    
    /**
     * Set an OnClickListener for the "Move To" button
     * @param listener View.OnLickListener callback
     */
    public void setMoveToListener(View.OnClickListener listener) {
        moveToListener = listener;
    }

    protected View makeControllerView() {
        return (
            (LayoutInflater)
                ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
            ).inflate(R.layout.media_controller, this);
    }

    private static class MessageHandler extends Handler {
        
        private final WeakReference<MDMediaController> target;
        
        MessageHandler(MDMediaController mc) {
            target = new WeakReference<MDMediaController>(mc);
        }
        
        @Override
        public void handleMessage(Message msg) {
            
            MDMediaController mc = target.get();
            
            long pos;
            switch (msg.what) {
                case FADE_OUT:
                    mc.hide();
                    break;
                case SHOW_PROGRESS:
                    pos = mc.setProgress();
                    if (!mc.dragging && mc.showing) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                        mc.updatePausePlay();
                    }
                    break;
            }
        }
        
    }
    
    private void initControllerView(View v) {
        
        pauseButton = (ImageButton)v.findViewById(R.id.mctrlPause);
        if (pauseButton != null) {
            pauseButton.requestFocus();
            pauseButton.setOnClickListener(pauseListener);
        }
        
        if (moveToListener != null)
            moveToButton = (Button)v.findViewById(R.id.mctrlMoveTo);
            if (moveToButton != null)
                moveToButton.setOnClickListener(moveToListener);

        progress = (ProgressBar)v.findViewById(R.id.mctrlProgress);
        if (progress != null) {
            SeekBar seeker = (SeekBar)progress;
            seeker.setOnSeekBarChangeListener(seekListener);
            seeker.setThumbOffset(1);
            progress.setMax(1000);
        }

        endTime  = (TextView)v.findViewById(R.id.mctrlTotalTime);
        curTime  = (TextView)v.findViewById(R.id.mctrlCurTime);
        titleView = (TextView)v.findViewById(R.id.mctrlTitle);
        if (titleView != null)
            titleView.setText(title);
        
    }

    private long setProgress() {
        
        if (player == null || dragging)
            return 0;

        int position = player.getCurrentPosition();
        int duration = player.getDuration();
        if (progress != null) {
            if (duration > 0) {
                int pos = 1000 * position / duration;
                progress.setProgress(pos);
            }
            int percent = player.getBufferPercentage();
            progress.setSecondaryProgress(percent * 10);
        }

        this.duration = duration;

        if (endTime != null)
            endTime.setText(strTime(duration));
        if (curTime != null)
            curTime.setText(strTime(position));

        return position;
    }
    
    private boolean initController(Context context) {
        ctx = context;
        audioMgr = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        return true;
    }
    
    private void initFloatingWindow() {
        popupWindow = new PopupWindow(ctx);
        popupWindow.setFocusable(false);
        popupWindow.setBackgroundDrawable(null);
        popupWindow.setOutsideTouchable(true);
        sb = new StringBuilder();
        formatter = new Formatter(sb, Locale.getDefault());
    }

    private View.OnClickListener pauseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            doPauseResume();
            show(timeout);
        }
    };

    private void updatePausePlay() {
        if (rootView == null || pauseButton == null)
            return;
        if (player.isPlaying())
            pauseButton.setImageResource(R.drawable.pause);
        else
            pauseButton.setImageResource(R.drawable.play);
    }

    private void doPauseResume() {
        if (player.isPlaying())
            player.pause();
        else
            player.start();
        updatePausePlay();
    }
    
    private String strTime(int time) {
        
        int totalSeconds = time / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours   = totalSeconds / 3600;

        sb.setLength(0);
        if (hours > 0)
            return formatter.format(
                "%d:%02d:%02d", hours, minutes, seconds //$NON-NLS-1$
            ).toString(); 
        return formatter.format("%02d:%02d", minutes, seconds).toString(); //$NON-NLS-1$
        
    }

    private OnSeekBarChangeListener seekListener =
        new OnSeekBarChangeListener() {
        
        @Override
        public void onStartTrackingTouch(SeekBar bar) {
            dragging = true;
            show(3600000);
            handler.removeMessages(SHOW_PROGRESS);
        }

        @Override
        public void onProgressChanged(
            SeekBar bar, int progress, boolean fromuser
        ) {
            if (!fromuser) return;
            int newposition = (duration * progress) / 1000;
            String time = strTime(newposition);
            if (curTime != null)
                curTime.setText(time);
        }

        @Override
        public void onStopTrackingTouch(SeekBar bar) {
            show(timeout);
            handler.removeMessages(SHOW_PROGRESS);
            audioMgr.setStreamMute(AudioManager.STREAM_MUSIC, false);
            dragging = false;
            handler.sendEmptyMessageDelayed(SHOW_PROGRESS, 1000);
            player.seekTo((bar.getProgress() * duration) / 1000);
        }
        
    };

}
