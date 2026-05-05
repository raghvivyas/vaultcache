package com.vaultcache.model;

public enum LimitKeyType {
    API_KEY,   // rate limit by X-API-Key header
    IP,        // rate limit by client IP address
    USER       // rate limit by authenticated username
}
