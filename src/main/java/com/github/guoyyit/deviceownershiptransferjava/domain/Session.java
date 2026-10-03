package com.github.guoyyit.deviceownershiptransferjava.domain;

public class Session {
    public String token;
    public String ownerId;
    public long issuedAt;
    public long expiresAt;
    public boolean revoked;

    public Session() {
    }
}
