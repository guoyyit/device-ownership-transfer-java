package com.github.guoyyit.deviceownershiptransferjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String dataDir = "./data";
    private long timestampWindowMs = 300_000;
    private long sessionTtlMs = 3_600_000;
    private long credentialTtlMs = 86_400_000L;

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public long getTimestampWindowMs() { return timestampWindowMs; }
    public void setTimestampWindowMs(long timestampWindowMs) { this.timestampWindowMs = timestampWindowMs; }
    public long getSessionTtlMs() { return sessionTtlMs; }
    public void setSessionTtlMs(long sessionTtlMs) { this.sessionTtlMs = sessionTtlMs; }
    public long getCredentialTtlMs() { return credentialTtlMs; }
    public void setCredentialTtlMs(long credentialTtlMs) { this.credentialTtlMs = credentialTtlMs; }
}
