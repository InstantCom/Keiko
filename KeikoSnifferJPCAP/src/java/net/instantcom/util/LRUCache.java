package net.instantcom.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class LRUCache<K, V> {

    public LRUCache(int capacity) {
        this(capacity, null);
    }

    public LRUCache(int capacity, LRUCacheListener<V> listener) {
        this.capacity = capacity;
        this.listener = listener;
        storage = new LinkedHashMap<K, V>(capacity, 0.75f, true) {

            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                LRUCacheListener<V> listener = LRUCache.this.listener;
                boolean result = size() > LRUCache.this.capacity;
                if (null != listener) {
                    result |= listener.shouldForcefullyRemove(eldest.getValue());
                }
                if (result && null != listener) {
                    listener.onObjectRemoved(eldest.getValue());
                }
                return result;
            }

        };
    }

    public void clear() {
        storage.clear();
        if (null != listener) {
            listener.onCacheCleared();
        }
    }

    public void put(K key, V value) {
        storage.put(key, value);
        if (null != listener) {
            listener.onObjectAdded(value);
        }
    }

    public V get(K key) {
        return storage.get(key);
    }

    public void remove(K key) {
        V obj = storage.remove(key);
        if (null != listener && null != obj) {
            listener.onObjectRemoved(obj);
        }
    }

    public boolean containsKey(K key) {
        return storage.containsKey(key);
    }

    public boolean containsValue(V value) {
        return storage.containsValue(value);
    }

    public Set<K> keySet() {
        return storage.keySet();
    }

    public Collection<V> values() {
        return storage.values();
    }

    public int size() {
        return storage.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isEmpty() {
        return storage.isEmpty();
    }

    public Map<K, V> getStorage() {
        return storage;
    }

    private int capacity;
    private LRUCacheListener<V> listener;
    private Map<K, V> storage;

}
