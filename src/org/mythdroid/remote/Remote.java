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

package org.mythdroid.remote;

import java.io.IOException;

import org.mythdroid.Enums.Key;
import org.mythdroid.frontend.FrontendManager;
import org.mythdroid.util.ErrUtil;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector.SimpleOnGestureListener;

/** Base class for remotes */
public abstract class Remote extends Activity implements View.OnClickListener {

    /** Your FrontendManager */
    protected FrontendManager feMgr  = null;

    final private static KeyCharacterMap keyMap =
        KeyCharacterMap.load(KeyCharacterMap.BUILT_IN_KEYBOARD);

    /** Result codes for when a remote is startActivityForResult()'d */
    final protected int REMOTE_RESULT_FINISH = RESULT_FIRST_USER;
    
    /** Scale factor for pixel values for different display densities */
    private float scale = 1;
    
    private boolean alt = false, shift = false;
    
    private GestureDetector gDetector;
    
    private class RemoteGestureListener extends SimpleOnGestureListener {

        public float scrollIntX = (int)(50 * scale), 
                     scrollIntY = (int)(50 * scale);

        final private float maxScrollSpeed = (float) (0.30 * scale);
        final private float minFlingSpeed  = 360  * scale;
        final private float wobble         = (int)  (40   * scale);
        
        final private static int   SCROLL_LOCK_UNLOCKED = 0;
        final private static int   SCROLL_LOCK_X        = 1, SCROLL_LOCK_Y = 2;
        
        private MotionEvent        lastStart            = null;
        private float              scrollMul            = 1;
        private float              scrolledX            = 0, scrolledY = 0;
        private int                scrollLock           = SCROLL_LOCK_UNLOCKED;
        private boolean            fling                = false;

        @Override
        public boolean onSingleTapConfirmed(MotionEvent me) {
            onTap();
            return true;
        }

        @Override
        public boolean onFling(
            MotionEvent start, MotionEvent end, float vX, float vY
        ) {
            
            if (!fling) return true;

            float absVX = Math.abs(vX);
            float absVY = Math.abs(vY);
            float absDX = Math.abs(end.getX() - start.getX());
            float absDY = Math.abs(end.getY() - start.getY());

            if (absVX > minFlingSpeed && absDY < absDX / 2)
                if (vX > 0)
                    onFlingRight();
                else
                    onFlingLeft();

            else if (absVY > minFlingSpeed && absDX < absDY / 2) 
                if (vY > 0)
                    onFlingDown();
                else
                    onFlingUp();

            return true;
        }

        @Override
        public boolean onScroll(
            MotionEvent start, MotionEvent end, float dX, float dY
        ) {

            if (start != lastStart) {
                resetScroll(start, dX, dY, SCROLL_LOCK_UNLOCKED);
                return true;
            }

            if (fling) return true;

            scrolledX += dX;
            scrolledY += dY;

            float absX = Math.abs(scrolledX);
            float absY = Math.abs(scrolledY);
            long elapsed = end.getEventTime() - start.getEventTime();

            // fast = fling
            if (absX / elapsed > maxScrollSpeed) {
                fling = true;
                return true;
            }
            if (absY / elapsed > maxScrollSpeed) {
                fling = true;
                return true;
            }

            // about turn?
            if (
                absX > wobble &&
                ((scrolledX > 0.0 && dX < 0.0) || (scrolledX < 0.0 && dX > 0.0))
            ) {
                resetScroll(null, dX, 0, SCROLL_LOCK_X);
                absX = Math.abs(scrolledX);
                absY = 0;
            }

            else if (
                absY > wobble &&
                ((scrolledY > 0.0 && dY < 0.0) || (scrolledY < 0.0 && dY > 0.0))
            ) {
                resetScroll(null, 0, dY, SCROLL_LOCK_Y);
                absX = 0;
                absY = Math.abs(scrolledY);
            }

            // Triggered a scroll event?
            if (
                absX > scrollIntX * scrollMul && 
                absY < wobble && 
                scrollLock != SCROLL_LOCK_Y
            ) {
                if (scrolledX > 0)
                    onScrollLeft();
                else
                    onScrollRight();
                scrollMul++;
                fling = false;
            }
            else if (
                absY > scrollIntY * scrollMul && 
                absX < wobble &&
                scrollLock != SCROLL_LOCK_X
            ) {
                if (scrolledY > 0)
                    onScrollUp();
                else
                    onScrollDown();
                scrollMul++;
                fling = false;
            }

            return true;

        }

        private void resetScroll(MotionEvent start, float dX, float dY, int lock) {
            if (start != null) 
                lastStart = start;
            scrolledX = dX;
            scrolledY = dY;
            scrollMul = 1;
            scrollLock = lock;
            fling = false;
        }

    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        scale = getResources().getDisplayMetrics().density;
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }
    
    @Override
	public void onOptionsMenuClosed(Menu menu) {
		super.onOptionsMenuClosed(menu);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		final boolean onPrepareOptionsMenu = super.onPrepareOptionsMenu(menu);
		setVolumeControlStream(AudioManager.STREAM_NOTIFICATION);
		return onPrepareOptionsMenu;
	}

	@Override
    public boolean onTouchEvent(MotionEvent me) {
        if (gDetector != null && gDetector.onTouchEvent(me)) 
            return true;
        return false;

    }

    @Override
    public void onClick(View v) {
        onAction();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {

        try {
            switch (code) {
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_MENU:
                    super.onKeyDown(code, event);
                    return false;
                case KeyEvent.KEYCODE_DPAD_UP:
                    feMgr.sendKey(Key.UP);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    feMgr.sendKey(Key.DOWN);
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    feMgr.sendKey(Key.LEFT);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    feMgr.sendKey(Key.RIGHT);
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    feMgr.sendKey(Key.ENTER);
                    break;
                case KeyEvent.KEYCODE_DEL:
                    feMgr.sendKey(Key.BACKSPACE);
                    break;
                case KeyEvent.KEYCODE_SPACE:
                    feMgr.sendKey(Key.SPACE);
                    break;
                case KeyEvent.KEYCODE_TAB:
                    feMgr.sendKey(Key.TAB);
                    break;
                case KeyEvent.KEYCODE_ALT_LEFT:
                case KeyEvent.KEYCODE_ALT_RIGHT:
                    alt = !alt;
                    break;
                case KeyEvent.KEYCODE_SHIFT_LEFT:
                case KeyEvent.KEYCODE_SHIFT_RIGHT:
                    shift = !shift;
                    break;
                case KeyEvent.KEYCODE_VOLUME_UP:
					feMgr.sendKey(Key.VOL_UP);
					feMgr.sendKey(Key.VOL_UP);
					break;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
					feMgr.sendKey(Key.VOL_DOWN);
					feMgr.sendKey(Key.VOL_DOWN);
					break;
                default:
                    int meta = (alt ? KeyEvent.META_ALT_ON : 0);
                    meta |= (shift ? KeyEvent.META_SHIFT_ON : 0);
                    String key = String.valueOf((char) keyMap.get(code, meta));
                    if (
                        key.matches("\\p{Print}+") && //$NON-NLS-1$
                        key.matches("\\p{ASCII}+") //$NON-NLS-1$
                    ) feMgr.sendKey(key);
                    if (alt) alt = false;
                    if (shift) shift = false;
            }
            onAction();
        } catch (IOException e) { ErrUtil.err(this, e); }

        return true;

    }
    
    /**
     * Listen to gestures
     * @param listen - true to start listening, false to stop
     */
    protected void listenToGestures(boolean listen) {

        if (listen) {
            gDetector = new GestureDetector(this, new RemoteGestureListener());
            gDetector.setIsLongpressEnabled(false);
        }
        else
            gDetector = null;
    }
    
    protected void onFlingUp() {
        try {
            feMgr.sendKey(Key.UP);
        } catch (IOException e) { ErrUtil.err(this, e); }
        onAction();
    }

    protected void onFlingDown() {
        try {
            feMgr.sendKey(Key.DOWN);
        } catch (IOException e) { ErrUtil.err(this, e); }
        onAction();
    }

    protected void onFlingLeft() {
        try {
            feMgr.sendKey(Key.LEFT);
        } catch (IOException e) { ErrUtil.err(this, e); }
        onAction();
    }

    protected void onFlingRight() {
        try {
            feMgr.sendKey(Key.RIGHT);
        } catch (IOException e) { ErrUtil.err(this, e); }
        onAction();
    }

    protected void onScrollDown() {
        onFlingDown();
    }

    protected void onScrollLeft() {
        onFlingLeft();
    }

    protected void onScrollRight() {
        onFlingRight();
    }

    protected void onScrollUp() {
        onFlingUp();
    }

    protected void onTap() {
        try {
            feMgr.sendKey(Key.ENTER);
        } catch (IOException e) { ErrUtil.err(this, e); }
        onAction();
    }

    /** Override this to be notified of any valid action */
    protected void onAction() {}

}
