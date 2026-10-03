package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record SessionResponse(String sessionId,
                              String deviceId,
                              String ownerId,
                              long epoch,
                              long issuedAt,
                              long expiresAt,
                              String token) {
}
