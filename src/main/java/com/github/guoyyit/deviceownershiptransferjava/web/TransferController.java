package com.github.guoyyit.deviceownershiptransferjava.web;

import com.github.guoyyit.deviceownershiptransferjava.service.TransferService;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.Requests.CancelTransferRequest;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.Requests.DeviceActionRequest;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.Requests.InitiateTransferRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/api/devices/{deviceId}/transfers")
    public Object initiate(@PathVariable String deviceId,
                           @RequestHeader("X-Owner-Token") String ownerToken,
                           @RequestBody InitiateTransferRequest request) {
        return transferService.initiate(deviceId, request.toOwnerId(), ownerToken,
                request.nonce(), request.timestamp());
    }

    @PostMapping("/api/transfers/{transferId}/confirm")
    public Object confirm(@PathVariable String transferId,
                          @RequestBody DeviceActionRequest request) {
        return transferService.confirm(transferId, request.deviceId(), request.signature(),
                request.nonce(), request.timestamp());
    }

    @PostMapping("/api/transfers/{transferId}/reject")
    public Object reject(@PathVariable String transferId,
                         @RequestBody DeviceActionRequest request) {
        return transferService.reject(transferId, request.deviceId(), request.signature(),
                request.nonce(), request.timestamp());
    }

    @PostMapping("/api/transfers/{transferId}/cancel")
    public Object cancel(@PathVariable String transferId,
                         @RequestHeader("X-Owner-Token") String ownerToken,
                         @RequestBody CancelTransferRequest request) {
        return transferService.cancel(transferId, ownerToken, request.nonce(), request.timestamp());
    }
}
