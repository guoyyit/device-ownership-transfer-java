package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record DeviceResponse(String deviceId,
                             String publicKey,
                             String ownerId,
                             String ownerName,
                             long epoch,
                             long registeredAt) {
}
