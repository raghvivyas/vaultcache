package com.vaultcache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vaultcache")
public class AppProperties {

    private String jwtSecret       = "vaultcache-default-secret-change-in-production-256bits!!";
    private long   jwtExpirationMs = 86400000L;

    // Default rate limit applied when no specific config is found
    private int    defaultLimitPerMinute = 60;
    private int    defaultBurstSize      = 10;

    public String getJwtSecret()                   { return jwtSecret; }
    public void   setJwtSecret(String v)           { this.jwtSecret = v; }
    public long   getJwtExpirationMs()             { return jwtExpirationMs; }
    public void   setJwtExpirationMs(long v)       { this.jwtExpirationMs = v; }
    public int    getDefaultLimitPerMinute()        { return defaultLimitPerMinute; }
    public void   setDefaultLimitPerMinute(int v)  { this.defaultLimitPerMinute = v; }
    public int    getDefaultBurstSize()             { return defaultBurstSize; }
    public void   setDefaultBurstSize(int v)        { this.defaultBurstSize = v; }
}
