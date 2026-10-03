package com.github.guoyyit.deviceownershiptransferjava.domain;

public class Transfer {
    public String transferId;
    public String deviceId;
    public String fromOwnerId;
    public String toOwnerId;
    public TransferStatus status;
    public long createdAt;
    public long updatedAt;
    public String confirmChallenge;

    public Transfer() {
    }
}
