package com.github.guoyyit.deviceownershiptransferjava.domain;

public class AuditRecord {
    public String id;
    public long timestamp;
    public String deviceId;
    public String transferId;
    public String action;
    public String actor;
    public String detail;

    public AuditRecord() {
    }
}
