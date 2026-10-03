package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record TransferResponse(String transferId,
                              String deviceId,
                              String fromOwnerId,
                              String toOwnerId,
                              String status,
                              long createdAt,
                              long updatedAt) {
}
