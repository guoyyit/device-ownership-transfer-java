package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record OwnerResponse(String ownerId,
                           String name,
                           String publicKey,
                           String privateKey,
                           long createdAt) {
}
