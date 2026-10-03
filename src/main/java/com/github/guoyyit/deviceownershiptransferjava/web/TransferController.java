package com.github.guoyyit.deviceownershiptransferjava.web;

import com.github.guoyyit.deviceownershiptransferjava.service.TransferService;
import com.github.guoyyit.deviceownershiptransferjava.store.TransferEntity;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices/{deviceId}")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> initiate(@PathVariable String deviceId,
                                                     @RequestBody InitiateTransferRequest request) {
        TransferEntity transfer = transferService.initiate(deviceId, request.newOwnerPublicKey(),
                request.nonce(), request.timestamp(), request.ownerSignature());
        return ResponseEntity.ok(toResponse(transfer));
    }

    @GetMapping("/transfers")
    public ResponseEntity<List<TransferResponse>> history(@PathVariable String deviceId) {
        List<TransferEntity> transfers = transferService.history(deviceId);
        return ResponseEntity.ok(transfers.stream().map(this::toResponse).toList());
    }

    @PostMapping("/transfer/pending")
    public ResponseEntity<TransferResponse> pending(@PathVariable String deviceId,
                                                   @RequestBody PendingTransferRequest request) {
        TransferEntity transfer = transferService.fetchPending(deviceId, request.nonce(),
                request.timestamp(), request.deviceSignature());
        return ResponseEntity.ok(toResponse(transfer));
    }

    @PostMapping("/transfer/confirm")
    public ResponseEntity<TransferResponse> confirm(@PathVariable String deviceId,
                                                    @RequestBody ConfirmTransferRequest request) {
        TransferEntity transfer = transferService.confirm(deviceId, request.transferId(),
                request.nonce(), request.timestamp(), request.deviceSignature());
        return ResponseEntity.ok(toResponse(transfer));
    }

    @PostMapping("/transfer/reject")
    public ResponseEntity<TransferResponse> reject(@PathVariable String deviceId,
                                                   @RequestBody RejectTransferRequest request) {
        TransferEntity transfer = transferService.reject(deviceId, request.transferId(),
                request.nonce(), request.timestamp(), request.deviceSignature());
        return ResponseEntity.ok(toResponse(transfer));
    }

    @PostMapping("/transfer/cancel")
    public ResponseEntity<TransferResponse> cancel(@PathVariable String deviceId,
                                                   @RequestBody CancelTransferRequest request) {
        TransferEntity transfer = transferService.cancel(deviceId, request.transferId(),
                request.nonce(), request.timestamp(), request.ownerSignature());
        return ResponseEntity.ok(toResponse(transfer));
    }

    private TransferResponse toResponse(TransferEntity transfer) {
        return new TransferResponse(transfer.transferId, transfer.deviceId, transfer.fromOwnerId,
                transfer.toOwnerId, transfer.status, transfer.createdAt, transfer.updatedAt);
    }
}
