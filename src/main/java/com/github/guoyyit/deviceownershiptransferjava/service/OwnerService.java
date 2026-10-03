package com.github.guoyyit.deviceownershiptransferjava.service;

import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.Keys;
import com.github.guoyyit.deviceownershiptransferjava.store.OwnerEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OwnerService {

    private final StateStore stateStore;
    private final CryptoService cryptoService;

    public OwnerService(StateStore stateStore, CryptoService cryptoService) {
        this.stateStore = stateStore;
        this.cryptoService = cryptoService;
    }

    public OwnerEntity createOwner(String name) {
        Keys keys = cryptoService.generateKeyPair();
        OwnerEntity owner = new OwnerEntity();
        owner.ownerId = "own-" + UUID.randomUUID();
        owner.name = name;
        owner.publicKey = keys.publicKey();
        owner.privateKey = keys.privateKey();
        owner.createdAt = System.currentTimeMillis();
        return stateStore.saveOwner(owner);
    }

    public OwnerEntity getOwner(String ownerId) {
        return stateStore.findOwner(ownerId)
                .orElseThrow(() -> new ServiceException(ErrorCode.OWNER_NOT_FOUND, "owner not found: " + ownerId));
    }
}
