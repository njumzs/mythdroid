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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.mythdroid.Globals;
import org.mythdroid.resource.Messages;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

/**
 * An external storage based cache
 */
public class ImageDiskCache {
    
    static final private int STORAGE_READABLE = 1;
    static final private int STORAGE_WRITABLE = 2;
    
    static final private String cachePath = "/Android/data/org.mythdroid/cache/"; //$NON-NLS-1$
    
    private long sizeOnDisk = 0, maxSizeOnDisk = 0;
    private File cacheDir = null;
    
    /**
     * Constructor
     * @param name name of cache directory
     * @param maxSize maximum size on disk in bytes
     */
    public ImageDiskCache(String name, int maxSize) throws IOException {
        
        if (!checkStorageState(STORAGE_READABLE))
            throw new IOException(Messages.getString("ImageDiskCache.0")); //$NON-NLS-1$
        
        cacheDir = new File( 
            Environment.getExternalStorageDirectory().getAbsolutePath() +
            cachePath + name
        );
        
        if (!cacheDir.exists()) {
            if (!checkStorageState(STORAGE_WRITABLE))
                throw new IOException(Messages.getString("ImageDiskCache.1")); //$NON-NLS-1$
            if (!cacheDir.mkdirs())
                throw new IOException(
                    Messages.getString("ImageDiskCache.2") + //$NON-NLS-1$
                    cacheDir.getAbsolutePath()
                );
            if (Globals.debug)
                Log.v("ImageDiskCache", "created new cache named " + name); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else if (Globals.debug) 
                Log.v("ImageDiskCache", "using existing cache named " + name); //$NON-NLS-1$ //$NON-NLS-2$
        
        maxSizeOnDisk = maxSize;
        sizeOnDisk = getSizeOnDisk();
        
    }
    
    /**
     * Add a Bitmap to the cache
     * @param key String that identifies the Bitmap
     * @param value the Bitmap to cache
     * @return true if cached successfully, false otherwise
     */
    public boolean put(String key, Bitmap value) {
        
        if (!checkStorageState(STORAGE_WRITABLE))
            return false;
        
        if (sizeOnDisk > maxSizeOnDisk) 
            removeOldest();
        
        File f = new File(cacheDir, key);
        BufferedOutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(f));
        } catch (FileNotFoundException e) {
            return false;
        }
        
        if (! value.compress(Bitmap.CompressFormat.PNG, 0, os)) {
            try {
                os.close();
            } catch (IOException e) {}
            return false;
        }
        
        try {
            os.flush();
            os.close();
        } catch (IOException e) {
            return false;
        }
        
        sizeOnDisk += getFileSizeOnDisk(f);
        
        return true;
        
    }
    
    /**
     * Retrieve a cached Bitmap
     * @param key String that identifies the Bitmap
     * @return a Bitmap or null if the requested key wasn't in the cache
     */
    public Bitmap get(String key) {
        
        if (!checkStorageState(STORAGE_READABLE))
            return null;
        
        File f = new File(cacheDir, key);
        if (!f.exists()) return null;
        f.setLastModified(System.currentTimeMillis());
        return BitmapFactory.decodeFile(f.getAbsolutePath());
        
    }
    
    /**
     * Get the size of the cache on disk
     * @return the size of the cache in bytes
     */
    public long size() {
        return sizeOnDisk;
    }
    
    private void removeOldest() {
        
        List<File> filesList = Arrays.asList(cacheDir.listFiles());
        
        Collections.sort(
            filesList, 
            new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    if (f1.lastModified() > f2.lastModified())
                        return 1;
                    if (f1.lastModified() < f2.lastModified())
                        return -1;
                    return 0;
                }
            }
        );
        
        File[] files = (File[])filesList.toArray();
        int i = 0, len = files.length;
        
        while (sizeOnDisk > maxSizeOnDisk && i < len) {
            File f = files[i];
            long size = getFileSizeOnDisk(f);
            if (f.delete())
                sizeOnDisk -= size;
            if (Globals.debug)
                Log.v(
                    "ImageDiskCache", //$NON-NLS-1$
                    "removed old file " + f.getName() + //$NON-NLS-1$
                    " total size now " + sizeOnDisk + " bytes" //$NON-NLS-1$ //$NON-NLS-2$
                );
            i++;
        }
        
    }
    
    private boolean checkStorageState(int desiredState) {
        int status;
        String state = Environment.getExternalStorageState();
        if (state.equals(Environment.MEDIA_MOUNTED))
            status = STORAGE_READABLE|STORAGE_WRITABLE;
        else if (state.equals(Environment.MEDIA_MOUNTED_READ_ONLY))
            status = STORAGE_READABLE;
        else
            status = 0;
        return (status & desiredState) > 0;
    }
    
    /**
     * Calculate the size on disk of the cache directory
     * @return total size on disk of cache directory in bytes
     */
    private long getSizeOnDisk() {
        long size = 0;
        File[] files = cacheDir.listFiles();
        int num = files.length;
        for (int i = 0; i < num; i++) {
            if (!files[i].isFile()) continue;
            size += getFileSizeOnDisk(files[i]);
        }
        if (Globals.debug)
            Log.v(
                "ImageDiskCache",  //$NON-NLS-1$
                cacheDir.getName() + " cache contains " + num + //$NON-NLS-1$
                " files, total size " + size + " bytes" //$NON-NLS-1$ //$NON-NLS-2$
            ); 
        
        return size;
    }
    
    /**
     * Determine the size on disk of a file
     * The sdcard fs has a 32k allocation unit so round up to nearest 32k
     * @param f File to determine size on disk of
     * @return size on disk in bytes
     */
    private long getFileSizeOnDisk(final File f) {
        long len = f.length();
        long mod = len % 32768;
        return mod == 0 ? len : len - mod + 32768;
    }

}
