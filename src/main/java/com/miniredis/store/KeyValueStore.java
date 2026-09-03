package com.miniredis.store;
import com.miniredis.model.DataValue;
import com.miniredis.persistence.AofLogger;
import java.util.concurrent.ConcurrentHashMap;
public class KeyValueStore {
    private final ConcurrentHashMap<String, DataValue> store = new ConcurrentHashMap<>();
    private final AofLogger aofLogger;
    public KeyValueStore(AofLogger aofLogger) {
        this.aofLogger = aofLogger;
    }

    // Standard Operations

    public void set(String key, String value, long ttlSeconds) {
            long expireAt = computeExpiry(ttlSeconds);
            store.put(key, new DataValue(value, expireAt));
            aofLogger.log("SET " + key + " " + value + " " + expireAt);
        }

    public String get(String key) {
        final String[] result = new String[1];

        store.computeIfPresent(key, (k, dataValue) -> {
            if (dataValue.isExpired()) {
                return null;
            }
            result[0] = dataValue.value();
            return dataValue;
        });

        return result[0];
    }

    public boolean del(String key) {
            boolean existed = store.remove(key) != null;
            if (existed) {
                aofLogger.log("DEL " + key);
            }
            return existed;
        }

        public boolean expire(String key, long ttlSeconds) {
                long expireAt = computeExpiry(ttlSeconds);
                DataValue updated = store.computeIfPresent(key, (k, dataValue) -> {
                    if (dataValue.isExpired()) {
                        return null;
                    }
                    return new DataValue(dataValue.value(), expireAt);
                });
                if (updated != null) {
                    aofLogger.log("EXPIRE " + key + " " + expireAt);
                }
                return updated != null;
            }
    // AOF & Expiry Helpers
    

    /** Used ONLY by AOF recovery — bypasses logging entirely. */
    public void setAbsolute(String key, String value, long expireAtTimestamp) {
        store.put(key, new DataValue(value, expireAtTimestamp));
    }

    /** Used ONLY by AOF recovery for replaying DEL commands. */
    public void delSilent(String key) {
        store.remove(key);
    }

    /** Computes absolute timestamp in ms (or -1 if no expiry). */
    public long computeExpiry(long ttlSeconds) {
        return (ttlSeconds > 0) ? System.currentTimeMillis() + (ttlSeconds * 1000) : -1;
    }

    public ConcurrentHashMap<String, DataValue> getStoreView() {
        return store;
    }
}
