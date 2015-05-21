// $Id: CacheItemManager.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

public interface CacheItemManager {
    public CacheItem loadCacheItem(String absUrl, boolean withData, boolean recursive);
    public int insertCacheItem(CacheItem ci, boolean recursive);
    public int saveCacheItem(CacheItem ci, boolean recursive);
}
