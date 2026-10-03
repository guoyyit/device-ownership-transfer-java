package com.github.guoyyit.deviceownershiptransferjava.tests;

import com.github.guoyyit.deviceownershiptransferjava.domain.OwnerAccount;
import com.github.guoyyit.deviceownershiptransferjava.domain.Transfer;
import com.github.guoyyit.deviceownershiptransferjava.web.ApiException;
import com.github.guoyyit.deviceownershiptransferjava.web.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReplayProtectionTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsReusedNonce() {
        TestServices services = TestServices.fresh(tempDir);
        String nonce = services.uniqueNonce();
        services.replayGuard.checkAndRecord(nonce, services.now());

        ApiException error = assertThrows(ApiException.class,
                () -> services.replayGuard.checkAndRecord(nonce, services.now()));
        assertEquals(ErrorCode.REPLAY_DETECTED, error.code());
    }

    @Test
    void rejectsStaleTimestamp() {
        TestServices services = TestServices.fresh(tempDir);
        long stale = services.now() - 10L * 60 * 1000;

        ApiException error = assertThrows(ApiException.class,
                () -> services.replayGuard.checkAndRecord(services.uniqueNonce(), stale));
        assertEquals(ErrorCode.STALE_TIMESTAMP, error.code());
    }

    @Test
    void rejectsFutureTimestamp() {
        TestServices services = TestServices.fresh(tempDir);
        long future = services.now() + 10L * 60 * 1000;

        ApiException error = assertThrows(ApiException.class,
                () -> services.replayGuard.checkAndRecord(services.uniqueNonce(), future));
        assertEquals(ErrorCode.STALE_TIMESTAMP, error.code());
    }

    @Test
    void replayedTransferInitiationIsRejected() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount owner = services.newOwner();
        OwnerAccount buyer = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        services.registerDevice("device-1", keys, owner);
        String token = services.sessionToken(owner);

        String nonce = services.uniqueNonce();
        long timestamp = services.now();
        Transfer transfer = services.transfers.initiate("device-1", buyer.ownerId, token, nonce, timestamp);
        assertEquals("PENDING", transfer.status.name());

        ApiException error = assertThrows(ApiException.class,
                () -> services.transfers.initiate("device-1", buyer.ownerId, token, nonce, timestamp));
        assertEquals(ErrorCode.REPLAY_DETECTED, error.code());
    }

    @Test
    void replayedDeviceConfirmationIsRejected() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount owner = services.newOwner();
        OwnerAccount buyer = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        services.registerDevice("device-1", keys, owner);
        String token = services.sessionToken(owner);
        Transfer transfer = services.transfers.initiate(
                "device-1", buyer.ownerId, token, services.uniqueNonce(), services.now());

        String nonce = services.uniqueNonce();
        long timestamp = services.now();
        String signature = services.sign(keys, services.confirmPayload(transfer, nonce, timestamp));
        services.transfers.confirm(transfer.transferId, "device-1", signature, nonce, timestamp);
        assertEquals(buyer.ownerId, services.devices.currentOwner("device-1"));

        ApiException error = assertThrows(ApiException.class,
                () -> services.transfers.confirm(transfer.transferId, "device-1", signature, nonce, timestamp));
        assertEquals(ErrorCode.REPLAY_DETECTED, error.code());
    }

    @Test
    void noncesSurviveRestart() {
        TestServices services = TestServices.fresh(tempDir);
        String nonce = services.uniqueNonce();
        services.replayGuard.checkAndRecord(nonce, services.now());

        TestServices restarted = TestServices.restart(tempDir);
        ApiException error = assertThrows(ApiException.class,
                () -> restarted.replayGuard.checkAndRecord(nonce, restarted.now()));
        assertEquals(ErrorCode.REPLAY_DETECTED, error.code());
    }
}
