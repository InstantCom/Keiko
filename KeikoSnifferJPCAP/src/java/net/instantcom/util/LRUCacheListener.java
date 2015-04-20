package net.instantcom.util;

public interface LRUCacheListener<V> {

    /**
     * Called when cache was cleared.
     */
    public void onCacheCleared();

    /**
     * Called when object was added to cache.
     * 
     * @param obj
     *            added object
     */
    public void onObjectAdded(V obj);

    /**
     * Called when object was or is about to be removed from cache.
     * 
     * @param obj
     *            removed object
     */
    public void onObjectRemoved(V obj);

    /**
     * Called from LRUCache.removeEldestObject() when decision is made whether to keep or remove
     * eldest object. Object will be removed if method returns true, even if cache is not full. Note
     * however that object will be removed regardless of the value this method returns if cache is
     * full (default LRU cache behaviour).
     * 
     * @param obj
     *            eldest object
     * @return true if object should be removed, false if object should be kept
     */
    public boolean shouldForcefullyRemove(V obj);

}
