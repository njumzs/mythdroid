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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A synchronised, in-memory cache implemented with SoftReferences
 * @param <K> key, identifies a cached item 
 * @param <V> value, the cached item
 */
public class MemCache<K,V> extends AbstractMap<K,V> { 
    
    final private ReferenceQueue<V> queue = new ReferenceQueue<V>();
    
    private Map<K, SoftReference<V>> hash = null;
    
    /**
     * Create a new MemCache of zero initial capacity and unlimited max capacity
     */
    public MemCache() {
        hash = Collections.synchronizedMap(
            new LinkedHashMap<K, SoftReference<V>>()
        );
    }
    
    /**
     * Create a new MemCache of the specified initial capacity and unlimited max
     * capacity
     * @param capacity initial capacity of cache
     */
    public MemCache(int capacity) {
        hash = Collections.synchronizedMap(
            new LinkedHashMap<K, SoftReference<V>>(capacity)
        );
    }
    
    /**
     * Create a new MemCache of the specified initial and max capacities
     * @param capacity initial capacity of cache
     * @param maxCapacity maximum capacity of cache
     */
    public MemCache(int capacity, final int maxCapacity) {
        hash = Collections.synchronizedMap(
            new LinkedHashMap<K, SoftReference<V>>(capacity) {
                static final private long serialVersionUID = 1L;
                @Override
                public boolean removeEldestEntry(Entry<K, SoftReference<V>> e)
                {
                    return size() > maxCapacity;
                }
            }
        );
    }

    @Override
    public V put(K key, V value) {
        clean();
        SoftReference<V> ref = new SoftReference<V>(value, queue);
        SoftReference<V> res = hash.put(key, ref);
        if (res == null) return null;
        return res.get();
    }
    
    @Override
    public V get(Object key) {
        clean();
        SoftReference<V> ref = hash.get(key);
        if (ref == null) return null;
        V val = ref.get();
        if (val != null) return val;
        hash.remove(key);
        return null;
    }
    
    @Override
    public V remove(Object key) {
        clean();
        SoftReference<V> ref = hash.remove(key);
        if (ref == null) return null;
        return ref.get();
    }
    
    @Override
    public void clear() {
        hash.clear();
    }
    
    @Override
    public int size() {
        clean();
        return hash.size();
    }
    
    @Override
    public Set<Entry<K, V>> entrySet() {
        clean();
        Set<Entry<K,V>> res = new LinkedHashSet<Entry<K,V>>(hash.size());
        for (final Entry<K,SoftReference<V>> entry : hash.entrySet()) {
            final V value = entry.getValue().get();
            if (value == null) continue;
            res.add(
                new Entry<K,V>() {
                    @Override
                    public K getKey() {
                        return entry.getKey();
                    }
                    @Override
                    public V getValue() {
                        return value;
                    }
                    @Override
                    public V setValue(V v) {
                        entry.setValue(new SoftReference<V>(v, queue));
                        return value;
                    }
                }
            );
        }
        return res;
    }
    
    private void clean() {
        Reference<? extends V> sv;
        while ((sv = queue.poll()) != null) 
            hash.remove(sv);
    }
    
}
