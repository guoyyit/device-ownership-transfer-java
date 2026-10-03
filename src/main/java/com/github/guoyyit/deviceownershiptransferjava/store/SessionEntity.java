package com.github.guoyyit.deviceownershiptransferjava.store;

public class SessionEntity {
    public String sessionId;
    public String deviceId;
    public String ownerId;
    public long epoch;
    public long issuedAt;
    public long expiresAt;
    public boolean revoked;
}
