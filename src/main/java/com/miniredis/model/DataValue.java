package com.miniredis.model;

public record DataValue(String value, long expireAtTimestamp) {
    public boolean isExpired() {
        return expireAtTimestamp != -1 && System.currentTimeMillis() > expireAtTimestamp;
    }
}
