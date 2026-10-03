package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record SessionVerifyResponse(boolean valid,
                                   String sessionId,
                                   String deviceId,
                                   String ownerId,
                                   Long epoch,
                                   String reason) {
}
