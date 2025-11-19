package org.acme;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class CacheService {

    private static final Logger LOG = Logger.getLogger(CacheService.class);
    private static final String CACHE_NAME = "greeting-cache";

    @Inject
    RemoteCacheManager cacheManager;

    public void put(String key, String value) {
        LOG.infof("Putting into cache: %s = %s", key, value);
        RemoteCache<String, String> cache = getCache();
        if (cache == null) {
            LOG.error("Cache is null - cannot put value");
            throw new IllegalStateException("Cache '" + CACHE_NAME + "' is not available");
        }
        cache.put(key, value);
    }

    public Optional<String> get(String key) {
        LOG.infof("Getting from cache: %s", key);
        RemoteCache<String, String> cache = getCache();
        if (cache == null) {
            LOG.error("Cache is null - cannot get value");
            return Optional.empty();
        }
        String value = cache.get(key);
        return Optional.ofNullable(value);
    }

    public void clear() {
        LOG.info("Clearing cache");
        RemoteCache<String, String> cache = getCache();
        if (cache != null) {
            cache.clear();
        }
    }

    public boolean isConnected() {
        try {
            RemoteCache<String, String> cache = getCache();
            if (cache == null) {
                LOG.warn("Cache is null during health check");
                return false;
            }
            // Try to get cache size to verify connection
            int size = cache.size();
            LOG.infof("Infinispan cache connected, current size: %d", size);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to connect to Infinispan", e);
            return false;
        }
    }

    private RemoteCache<String, String> getCache() {
        try {
            // This will create the cache on first access if it doesn't exist
            // (using the configuration from application.properties)
            RemoteCache<String, String> cache = cacheManager.getCache(CACHE_NAME);
            if (cache == null) {
                LOG.warnf("Cache '%s' returned null from cache manager", CACHE_NAME);
            }
            return cache;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to get cache '%s'", CACHE_NAME);
            return null;
        }
    }
}
