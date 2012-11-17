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

package org.mythdroid.frontend;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.Enums.Extras;
import org.mythdroid.activities.VideoPlayer;
import org.mythdroid.frontend.FrontendDB;
import org.mythdroid.frontend.FrontendManager;
import org.mythdroid.frontend.WakeOnLan;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.views.MDVideoView;
import org.mythdroid.vlc.VLCRemote;

import android.R.drawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

/** A Helper class for implementing moving in-progress playback */ 
public class MovePlaybackHelper {

    private Context ctx             = null;
    private String moveTo           = null;
    private OnFrontendReady onReady = null;
    private OnPlaybackMoved onMoved = null;
    
    /** 
     * A Runnable that will run a callback when the specified frontend is ready 
     * for use 
     */
    private final class PrepareFrontendRunnable implements Runnable {
        
        @Override
        public void run() {
            String addr = FrontendDB.getFrontendAddr(ctx, moveTo);
            try {
                new FrontendManager(moveTo, addr); 
                onReady.onFrontendReady(moveTo);
            } catch (SocketTimeoutException e) {
                // The box might be asleep or turned off, can we wake it?
                String hwaddr = FrontendDB.getFrontendHwAddr(ctx, moveTo);
                if (hwaddr == null) {
                    ErrUtil.postErr(
                        ctx, e.getMessage() + Messages.getString("TVRemote.4") //$NON-NLS-1$
                    );
                    return;
                }
                tryToWake(moveTo, addr, hwaddr);
            } catch (IOException e) {
                ErrUtil.postErr(ctx, e);
                return;
            }
        }
        
        private void tryToWake(String fe, String addr, String hwaddr) {
            
            ErrUtil.postErr(ctx, Messages.getString("TVRemote.6") + fe + "...");  //$NON-NLS-1$//$NON-NLS-2$
            
            try {
                WakeOnLan.wakeFrontend(fe, addr, hwaddr);
            } catch (Exception e) {
                ErrUtil.postErr(
                    ctx, 
                    Messages.getString("TVRemote.7") + fe + //$NON-NLS-1$
                    Messages.getString("TVRemote.8") + e.getMessage() //$NON-NLS-1$
                );
                return;
            }
            
            // Give mythfrontend time to start up properly
            try { Thread.sleep(5000); } catch (InterruptedException e) {}
            onReady.onFrontendReady(fe);
            
        }
    
    }
    
    /**
     * Constructor
     * @param c Context
     * @param ready OnFrontendReady to call when a frontend is ready
     * @param moved OnPlaybackMoved to call when playback has been moved
     */
    public MovePlaybackHelper(
        Context c, OnFrontendReady ready, OnPlaybackMoved moved
    ) {
        ctx     = c;
        onReady = ready;
        onMoved = moved;
    }
    
    /**
     * Create a frontend chooser dialog for moving of playback from anywhere
     * to a new frontend
     * @return a frontend chooser dialog
     */
    public Dialog frontendChooserDialog() {
        
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
                    moveTo = ((String)av.getAdapter().getItem(pos));
                    Globals.runOnThreadPool(new PrepareFrontendRunnable());
                    d.dismiss();
                }
            }
        );
        
        return d;
        
    }
    
    /**
     * Get a frontend chooser dialog for moving of playback from a frontend to 
     * anywhere (including VideoPlayer ("Here"))
     * @param feMgr
     * @param feLock
     * @return a frontend chooser dialog
     */
    public Dialog frontendChooserDialog(
        final FrontendManager feMgr, final Object feLock 
    ) {
    
        final AlertDialog d = (AlertDialog)frontendChooserDialog();
    
        d.getListView().setOnItemClickListener(
            new OnItemClickListener() {
                @Override
                public void onItemClick(
                    AdapterView<?> av, View v, int pos, long id
                ) {
                    moveTo = ((String)av.getAdapter().getItem(pos));
                    if (
                        moveTo.equals(
                            Messages.getString("MDActivity.0") //$NON-NLS-1$
                        )
                    ) {
                        FrontendLocation loc = null;
                        try {
                            synchronized (feLock) { loc = feMgr.getLoc(); }
                        } catch (IOException e) {
                            ErrUtil.err(ctx, e);
                            return;
                        } catch (IllegalArgumentException e) {
                            ErrUtil.err(ctx, e);
                            return;
                        }
                        Intent intent =
                            new Intent(ctx, VideoPlayer.class);
                        intent.putExtra(
                            Extras.SEEKTO.toString(),
                            loc.position > 5 ?
                                loc.position - 5 : loc.position
                        );
                        moveToNewFrontend(intent, false);
                        return;
                    }
                        
                    Globals.runOnThreadPool(new PrepareFrontendRunnable());
                    d.dismiss();
                }
            }
        );
        
        return d;
        
    }
    
    /**
     * Create a move playback prompt dialog for moving from VideoPlayer
     * @param vlc - VLCRemote
     * @param videoView - MDVideoView
     * @return an AlertDialog
     */
    public Dialog movePromptDialog(
        final VLCRemote vlc, final MDVideoView videoView
    ) {
        
        OnClickListener mcl = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (which != Dialog.BUTTON_POSITIVE) return;
                if (vlc == null) {
                    ErrUtil.err(
                        ctx, Messages.getString("VideoPlayer.2") //$NON-NLS-1$
                    );
                    return;
                }
                int pos = (videoView.getCurrentPosition() / 1000);
                int seekTo = pos > 5 ? pos - 5 : pos;
                final Intent intent = new Intent(ctx, TVRemote.class);
                if (seekTo > 0)
                    intent.putExtra(Extras.SEEKTO.toString(), seekTo);
                moveToNewFrontend(intent, false);
            }
        };

        return createMovePromptDialog(mcl);
        
    }
    
    /**
     * Create a move playback prompt dialog for moving from a frontend
     * @param feMgr
     * @param feLock
     * @return an AlertDialog
     */
    public Dialog movePromptDialog(
        final FrontendManager feMgr, final Object feLock 
    ) {
    
        OnClickListener mcl = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (which != Dialog.BUTTON_POSITIVE) return;
                FrontendLocation loc = null;
                try {
                    synchronized (feLock) { loc = feMgr.getLoc(); }
                } catch (IOException e) {
                    ErrUtil.err(ctx, e);
                    return;
                } catch (IllegalArgumentException e) {
                    ErrUtil.err(ctx, e);
                    return;
                }
                final Intent intent = new Intent(ctx, TVRemote.class);
                if (loc.livetv)
                    intent.putExtra(
                        Extras.JUMPCHAN.toString(), loc.chanid
                    );
                else
                    intent.putExtra(
                        Extras.SEEKTO.toString(),
                        loc.position > 5 ?
                            loc.position - 5 : loc.position
                     );
                moveToNewFrontend(intent, false);
            }
        };
    
        return createMovePromptDialog(mcl);
        
    }
    
    private Dialog createMovePromptDialog(OnClickListener ocl) {
        return
            new AlertDialog.Builder(ctx)
                .setTitle(Messages.getString("TVRemote.11")) //$NON-NLS-1$
                .setMessage(Messages.getString("TVRemote.9")) //$NON-NLS-1$
                .setPositiveButton(R.string.yes, ocl)
                .setNegativeButton(R.string.cancel, ocl)
                .create();
    }
    
    /**
     * Prepare a move playback prompt dialog
     * @param dialog Dialog to prepare
     */
    public void prepareMoveDialog(Dialog dialog) {
        ((AlertDialog)dialog).setTitle(
            moveTo + Messages.getString("TVRemote.10") //$NON-NLS-1$
        );
        return;
    }
    
    /**
     * Initiate a move to the new frontend
     * @param intent Intent specifying the destination activity
     */
    public void moveToNewFrontend(Intent intent, boolean isAbort) {
        final Bundle extras = ((Activity)ctx).getIntent().getExtras();
        if (extras != null)
            intent.putExtras(extras);
        Globals.prevFe = Globals.curFe;
        if (!isAbort)
            Globals.curFe = moveTo;
        ctx.startActivity(intent);
        onMoved.onPlaybackMoved();
    }
    
    /** Abort a failed move, try to resume on the previous frontend */
    public void abortMove() {
        
        String cur  = Globals.curFe;
        String prev = Globals.prevFe;
        
        if (prev == null || cur.equals(prev)) return;
            
        ErrUtil.postErr(
            ctx,
            Messages.getString("TVRemote.12") + cur + //$NON-NLS-1$
            Messages.getString("TVRemote.13") + prev //$NON-NLS-1$
        );
        
        Globals.curFe = prev;
        
        moveToNewFrontend(
            new Intent(
                ctx,
                prev.equals(Messages.getString("MDActivity.0")) ? //$NON-NLS-1$
                    VideoPlayer.class : TVRemote.class
            ),
            true
        );
        
    }
    
}