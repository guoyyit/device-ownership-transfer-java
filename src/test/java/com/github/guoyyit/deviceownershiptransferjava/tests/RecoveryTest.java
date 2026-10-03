package com.github.guoyyit.deviceownershiptransferjava.tests;

import com.github.guoyyit.deviceownershiptransferjava.domain.OwnerAccount;
import com.github.guoyyit.deviceownershiptransferjava.domain.Transfer;
import com.github.guoyyit.deviceownershiptransferjava.domain.TransferStatus;
import com.github.guoyyit.deviceownershiptransferjava.web.ApiException;
import com.github.guoyyit.deviceownershiptransferjava.web.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void registeredDevicesAndOwnershipSurviveRestart() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount owner = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        services.registerDevice("device-1", keys, owner);

        TestServices restarted = TestServices.restart(tempDir);
        assertEquals(owner.ownerId, restarted.devices.currentOwner("device-1"));
        assertEquals(services.publicKeyOf(keys), restarted.devices.getDevice("device-1").publicKey);
    }

    @Test
    void pendingTransferSurvivesRestartAndCanComplete() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount seller = services.newOwner();
        OwnerAccount buyer = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        services.registerDevice("device-1", keys, seller);
        String token = services.sessionToken(seller);
        Transfer transfer = services.transfers.initiate("device-1", buyer.ownerId, token,
                services.uniqueNonce(), services.now());

        TestServices restarted = TestServices.restart(tempDir);
        Transfer pending = restarted.transfers.pendingForDevice("device-1");
        assertNotNull(pending);
        assertEquals(transfer.transferId, pending.transferId);
        assertEquals(TransferStatus.PENDING, pending.status);
        assertEquals(seller.ownerId, restarted.devices.currentOwner("device-1"));

        String nonce = restarted.uniqueNonce();
        long timestamp = restarted.now();
        String signature = restarted.sign(keys, restarted.confirmPayload(pending, nonce, timestamp));
        restarted.transfers.confirm(pending.transferId, "device-1", signature, nonce, timestamp);
        assertEquals(buyer.ownerId, restarted.devices.currentOwner("device-1"));

        TestServices restartedAgain = TestServices.restart(tempDir);
        assertEquals(buyer.ownerId, restartedAgain.devices.currentOwner("device-1"));
    }

    @Test
    void completedTransferAndRevokedSessionsSurviveRestart() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount seller = services.newOwner();
        OwnerAccount buyer = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        services.registerDevice("device-1", keys, seller);
        String sellerToken = services.sessionToken(seller);
        Transfer transfer = services.transfers.initiate("device-1", buyer.ownerId, sellerToken,
                services.uniqueNonce(), services.now());
        String nonce = services.uniqueNonce();
        long timestamp = services.now();
        String signature = services.sign(keys, services.confirmPayload(transfer, nonce, timestamp));
        services.transfers.confirm(transfer.transferId, "device-1", signature, nonce, timestamp);

        TestServices restarted = TestServices.restart(tempDir);
        assertEquals(buyer.ownerId, restarted.devices.currentOwner("device-1"));

        ApiException error = assertThrows(ApiException.class,
                () -> restarted.sessions.authenticate(sellerToken));
        assertEquals(ErrorCode.SESSION_REVOKED, error.code());

        List<Transfer> history = restarted.transfers.historyForDevice("device-1");
        assertEquals(1, history.size());
        assertEquals(TransferStatus.COMPLETED, history.get(0).status);
        assertTrue(restarted.audit.forDevice("device-1").stream()
                .anyMatch(record -> record.action.equals("TRANSFER_COMPLETED")));
    }

    @Test
    void issuerKeysSurviveRestartSoUnusedCredentialsRemainValid() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount owner = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        var credential = services.issueCredential("device-1", keys, owner);

        TestServices restarted = TestServices.restart(tempDir);
        var device = restarted.devices.register("device-1", restarted.publicKeyOf(keys), credential);
        assertEquals(owner.ownerId, device.ownerId);
    }

    @Test
    void singleOwnerInvariantHoldsAfterRestart() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount seller = services.newOwner();
        OwnerAccount buyer = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        services.registerDevice("device-1", keys, seller);
        String token = services.sessionToken(seller);
        services.transfers.initiate("device-1", buyer.ownerId, token,
                services.uniqueNonce(), services.now());

        TestServices restarted = TestServices.restart(tempDir);
        assertEquals(seller.ownerId, restarted.devices.currentOwner("device-1"));
        long pendingCount = restarted.store.state().transfers.values().stream()
                .filter(transfer -> transfer.deviceId.equals("device-1")
                        && transfer.status == TransferStatus.PENDING)
                .count();
        assertEquals(1, pendingCount);
    }
}
