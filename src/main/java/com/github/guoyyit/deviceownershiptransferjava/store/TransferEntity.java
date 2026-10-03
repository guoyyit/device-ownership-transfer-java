package com.github.guoyyit.deviceownershiptransferjava.store;

public class TransferEntity {
    public String transferId;
    public String deviceId;
    public String fromOwnerId;
    public String toOwnerId;
    public String status;
    public String initiateNonce;
    public long createdAt;
    public long updatedAt;
    public String confirmNonce;
    public String rejectNonce;
    public String cancelNonce;
}
