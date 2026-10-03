package com.github.guoyyit.deviceownershiptransferjava.web;

import com.github.guoyyit.deviceownershiptransferjava.service.OwnerService;
import com.github.guoyyit.deviceownershiptransferjava.store.OwnerEntity;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.CreateOwnerRequest;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.OwnerResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owners")
public class OwnerController {

    private final OwnerService ownerService;

    public OwnerController(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

    @PostMapping
    public ResponseEntity<OwnerResponse> createOwner(@RequestBody CreateOwnerRequest request) {
        OwnerEntity owner = ownerService.createOwner(request.name());
        return ResponseEntity.ok(new OwnerResponse(owner.ownerId, owner.name, owner.publicKey,
                owner.privateKey, owner.createdAt));
    }

    @GetMapping("/{ownerId}")
    public ResponseEntity<OwnerResponse> getOwner(@PathVariable String ownerId) {
        OwnerEntity owner = ownerService.getOwner(ownerId);
        return ResponseEntity.ok(new OwnerResponse(owner.ownerId, owner.name, owner.publicKey,
                owner.privateKey, owner.createdAt));
    }
}
