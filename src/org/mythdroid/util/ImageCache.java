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

package org.mythdroid.util;

import java.io.IOException;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import org.mythdroid.Globals;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * A two level cache for images
 * The first level is a MemCache
 * The second level is an ImageDiskCache
 * Writes to the second level are lazy
 */
public class ImageCache {
    
    static final private LinkedList<Runnable> queue
        = new LinkedList<Runnable>();
    
    static private Thread diskCacheThread = null;
    static private boolean runDiskCacheThread = true;
    
    private MemCache<Integer, Bitmap> memCache = null;
    private ImageDiskCache diskCache = null;
        
    /**
     * Constructor
     * @param name name of the cache
     * @param memCapacity initial capacity of MemCache in number of images
     * @param memMaxCapacity maximum capacity of MemCache in number of images
     * @param diskMaxSize maximum size of disk backed cache in bytes
     */
    public ImageCache(
        String name, int memCapacity, int memMaxCapacity, int diskMaxSize
    ) {
        try {
            diskCache = new ImageDiskCache(name, diskMaxSize);
        } catch (IOException e) {
            if (Globals.debug)
                Log.v("ImageCache", "Disk cache disabled: " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        if (diskCache != null && diskCacheThread == null)
            newDiskCacheThread();
        
        memCache = new MemCache<Integer, Bitmap>(memCapacity, memMaxCapacity);
        
    }
    
    /**
     * Add an image to the cache
     * @param key identifier
     * @param value image to cache
     */
    public void put(final int key, final Bitmap value) {
        memCache.put(key, value);
        if (diskCache != null) {
            synchronized (queue) {
                queue.add(
                    new Runnable() {
                        @Override
                        public void run() {
                            diskCache.put(String.valueOf(key), value);
                        }
                    }
                );
                queue.notify();
            }
        }
    }
    
    /**
     * Retrieve an image from the cache
     * @param key identifier
     * @return the cached image
     */
    public Bitmap get(final int key) {
        Bitmap ret = memCache.get(key);
        if (ret != null || diskCache == null)
            return ret;
        ret = diskCache.get(String.valueOf(key));
        if (ret != null)
            memCache.put(key, ret);
        return ret;
    }
    
    /**
     * Clean up internal resources employed by the ImageCache
     */
    public void shutdown() {
        runDiskCacheThread = false;
        if (diskCacheThread == null) {
            runDiskCacheThread = true;
            return;
        }
        diskCacheThread.interrupt();
        try {
            diskCacheThread.join();
        } catch (InterruptedException e) {}
        diskCacheThread = null;
        runDiskCacheThread = true;
    }
    
    private void newDiskCacheThread() {
        diskCacheThread = new Thread(
            new Runnable() {
                @Override
                public void run() {
                   
                   Runnable r = null;
                   while (runDiskCacheThread) {
                       
                       synchronized(queue) {
                           while (queue.isEmpty() && runDiskCacheThread)
                               try {
                                   queue.wait();
                               } catch (InterruptedException e) {}
                           try {
                               r = queue.removeFirst();
                           } catch (NoSuchElementException e) { return; }
                       }
                       
                       try {
                           r.run();
                       } catch (RuntimeException e) {
                           Log.v(
                               "ImageCache", //$NON-NLS-1$
                               "Exception in diskCacheThread: " + //$NON-NLS-1$
                                   e.getMessage()
                           );
                       }
                   }
                   
                }
            }
        );
        diskCacheThread.setName("diskCache"); //$NON-NLS-1$
        diskCacheThread.setDaemon(true);
        diskCacheThread.setPriority(Thread.MIN_PRIORITY);
        diskCacheThread.start();
    }

}
