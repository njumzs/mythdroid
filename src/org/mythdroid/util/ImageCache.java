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


import android.graphics.Bitmap;

/**
 * A two level cache for images.
 * The first level is a MemCache, the second level is an ImageDiskCache.
 * Writes to the second level are lazy
 */
public class ImageCache {
    
    static final private LinkedList<Runnable> queue
        = new LinkedList<Runnable>();
    
    static private Thread diskCacheThread = null;
    static private boolean runDiskCacheThread = true;
    
    private MemCache<String, Bitmap> memCache = null;
    private ImageDiskCache diskCache = null;
    private long memMax = 0;
        
    /**
     * Constructor
     * @param name name of the cache
     * @param memCapacity initial capacity of MemCache in bytes
     * @param memMaxCapacity maximum capacity of MemCache in bytes
     * @param diskMaxSize maximum size of disk backed cache in bytes
     */
    public ImageCache(
        String name, int memCapacity, int memMaxCapacity, long memMaxSize, int diskMaxSize
    ) {
        try {
            diskCache = new ImageDiskCache(name, diskMaxSize);
        } catch (IOException e) {
            LogUtil.debug("Disk cache disabled: " + e.getMessage()); //$NON-NLS-1$
        }
        
        if (diskCache != null && diskCacheThread == null)
            newDiskCacheThread();
        
        memCache = new MemCache<String, Bitmap>(memCapacity, memMaxCapacity);
        
        memMax = memMaxSize;
        
    }
    
    /**
     * Add an image to the cache
     * @param key identifier
     * @param value image to cache
     */
    public void put(final String key, final Bitmap value) {
        final String k = normalise(key);
        if (memMax > 0 && value.getRowBytes() * value.getHeight() <= memMax)
            memCache.put(k, value);
        if (diskCache != null) {
            synchronized (queue) {
                queue.add(
                    new Runnable() {
                        @Override
                        public void run() {
                            diskCache.put(k, value);
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
    public Bitmap get(final String key) {
        final String k = normalise(key);
        Bitmap ret = memCache.get(k);
        if (ret != null || diskCache == null)
            return ret;
        ret = diskCache.get(k);
        if (ret != null)
            memCache.put(k, ret);
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
                           } catch (NoSuchElementException e) { continue; }
                       }
                       
                       try {
                           r.run();
                       } catch (RuntimeException e) {
                           LogUtil.debug(
                               "Exception in diskCacheThread: " + e.getMessage() //$NON-NLS-1$
                           );
                       }
                       r = null;
                       
                   }
                   
                }
            }
        );
        diskCacheThread.setName("diskCache"); //$NON-NLS-1$
        diskCacheThread.setDaemon(true);
        diskCacheThread.setPriority(Thread.MIN_PRIORITY);
        diskCacheThread.start();
    }
    
    private String normalise(final String key) {
        
        int i = 0;
        
        if (key.startsWith("http://")) i = key.indexOf('/', 7);  //$NON-NLS-1$
        
        char[] array = key.toCharArray();
        int size = array.length;
        char[] dest = new char[size];
        
        int j = 0;
        for (; i < size; i++) {
            switch (array[i]) {
                case '/':
                case '&':
                case '?':
                case ';':
                case ':':
                    continue;
                default:
                    dest[j++] = array[i];
            }
        }
        
        return String.valueOf(dest, 0, j);
        
    }

}
