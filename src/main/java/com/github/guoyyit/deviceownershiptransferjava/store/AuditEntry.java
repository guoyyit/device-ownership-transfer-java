package com.github.guoyyit.deviceownershiptransferjava.store;

public class AuditEntry {
    public long seq;
    public long timestamp;
    public String eventType;
    public String deviceId;
    public String actorId;
    public String detail;
}
