package com.github.guoyyit.deviceownershiptransferjava.tests;

import com.github.guoyyit.deviceownershiptransferjava.domain.AuditRecord;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferFlowTest {

    @TempDir
    Path tempDir;

    private record Fixture(TestServices services, OwnerAccount seller, OwnerAccount buyer,
                           KeyPair deviceKeys, String sellerToken) {
    }

    private Fixture newFixture() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount seller = services.newOwner();
        OwnerAccount buyer = services.newOwner();
        KeyPair deviceKeys = services.newDeviceKeys();
        services.registerDevice("device-1", deviceKeys, seller);
        return new Fixture(services, seller, buyer, deviceKeys, services.sessionToken(seller));
    }

    private Transfer initiate(Fixture fixture) {
        return fixture.services.transfers.initiate("device-1", fixture.buyer.ownerId,
                fixture.sellerToken, fixture.services.uniqueNonce(), fixture.services.now());
    }

    private Transfer confirm(Fixture fixture, Transfer transfer) {
        TestServices services = fixture.services;
        String nonce = services.uniqueNonce();
        long timestamp = services.now();
        String signature = services.sign(fixture.deviceKeys, services.confirmPayload(transfer, nonce, timestamp));
        return services.transfers.confirm(transfer.transferId, "device-1", signature, nonce, timestamp);
    }

    @Test
    void fullTransferFlowSwitchesOwnership() {
        Fixture fixture = newFixture();
        Transfer transfer = initiate(fixture);
        assertEquals(TransferStatus.PENDING, transfer.status);
        assertEquals(fixture.seller.ownerId, fixture.services.devices.currentOwner("device-1"));

        Transfer confirmed = confirm(fixture, transfer);
        assertEquals(TransferStatus.COMPLETED, confirmed.status);
        assertEquals(fixture.buyer.ownerId, fixture.services.devices.currentOwner("device-1"));
    }

    @Test
    void offlineDeviceKeepsTransferPendingUntilReconnect() {
        Fixture fixture = newFixture();
        Transfer transfer = initiate(fixture);

        // device offline: transfer stays pending, ownership unchanged, no dual control
        assertEquals(fixture.seller.ownerId, fixture.services.devices.currentOwner("device-1"));
        Transfer pending = fixture.services.transfers.pendingForDevice("device-1");
        assertNotNull(pending);
        assertEquals(transfer.transferId, pending.transferId);

        // device reconnects, learns the pending transfer and confirms it
        confirm(fixture, pending);
        assertEquals(fixture.buyer.ownerId, fixture.services.devices.currentOwner("device-1"));
        assertNull(fixture.services.transfers.pendingForDevice("device-1"));
    }

    @Test
    void oldOwnerSessionRevokedImmediatelyAfterCompletion() {
        Fixture fixture = newFixture();
        Transfer transfer = initiate(fixture);
        confirm(fixture, transfer);

        ApiException error = assertThrows(ApiException.class,
                () -> fixture.services.sessions.authenticate(fixture.sellerToken));
        assertEquals(ErrorCode.SESSION_REVOKED, error.code());

        ApiException initiateError = assertThrows(ApiException.class,
                () -> fixture.services.transfers.initiate("device-1", fixture.seller.ownerId,
                        fixture.sellerToken, fixture.services.uniqueNonce(), fixture.services.now()));
        assertEquals(ErrorCode.SESSION_REVOKED, initiateError.code());
    }

    @Test
    void oldOwnerCannotInitiateTransferWithFreshSession() {
        Fixture fixture = newFixture();
        Transfer transfer = initiate(fixture);
        confirm(fixture, transfer);

        String freshToken = fixture.services.sessionToken(fixture.seller);
        ApiException error = assertThrows(ApiException.class,
                () -> fixture.services.transfers.initiate("device-1", fixture.buyer.ownerId,
                        freshToken, fixture.services.uniqueNonce(), fixture.services.now()));
        assertEquals(ErrorCode.NOT_DEVICE_OWNER, error.code());
    }

    @Test
    void nonOwnerCannotInitiateTransfer() {
        Fixture fixture = newFixture();
        String attackerToken = fixture.services.sessionToken(fixture.buyer);
        ApiException error = assertThrows(ApiException.class,
                () -> fixture.services.transfers.initiate("device-1", fixture.buyer.ownerId,
                        attackerToken, fixture.services.uniqueNonce(), fixture.services.now()));
        assertEquals(ErrorCode.NOT_DEVICE_OWNER, error.code());
    }

    @Test
    void duplicateInitiationWhilePendingIsRejected() {
        Fixture fixture = newFixture();
        initiate(fixture);

        ApiException error = assertThrows(ApiException.class, () -> initiate(fixture));
        assertEquals(ErrorCode.TRANSFER_CONFLICT, error.code());
    }

    @Test
    void doubleConfirmationIsRejected() {
        Fixture fixture = newFixture();
        Transfer transfer = initiate(fixture);
        confirm(fixture, transfer);

        ApiException error = assertThrows(ApiException.class, () -> confirm(fixture, transfer));
        assertEquals(ErrorCode.TRANSFER_NOT_PENDING, error.code());
    }

    @Test
    void forgedDeviceSignatureCannotConfirm() {
        Fixture fixture = newFixture();
        Transfer transfer = initiate(fixture);
        KeyPair attackerKeys = fixture.services.newDeviceKeys();

        String nonce = fixture.services.uniqueNonce();
        long timestamp = fixture.services.now();
        String forged = fixture.services.sign(attackerKeys,
                fixture.services.confirmPayload(transfer, nonce, timestamp));
        ApiException error = assertThrows(ApiException.class,
                () -> fixture.services.transfers.confirm(transfer.transferId, "device-1", forged, nonce, timestamp));
        assertEquals(ErrorCode.SIGNATURE_INVALID, error.code());
        assertEquals(fixture.seller.ownerId, fixture.services.devices.currentOwner("device-1"));
    }

    @Test
    void deviceCanRejectTransfer() {
        Fixture fixture = newFixture();
        Transfer transfer = initiate(fixture);

        TestServices services = fixture.services;
        String nonce = services.uniqueNonce();
        long timestamp = services.now();
        String signature = services.sign(fixture.deviceKeys, services.rejectPayload(transfer, nonce, timestamp));
        Transfer rejected = services.transfers.reject(transfer.transferId, "device-1", signature, nonce, timestamp);

        assertEquals(TransferStatus.REJECTED, rejected.status);
        assertEquals(fixture.seller.ownerId, services.devices.currentOwner("device-1"));
        assertNull(services.transfers.pendingForDevice("device-1"));
    }

    @Test
    void initiatorCanCancelPendingTransfer() {
        Fixture fixture = newFixture();
        Transfer transfer = initiate(fixture);

        Transfer cancelled = fixture.services.transfers.cancel(transfer.transferId,
                fixture.sellerToken, fixture.services.uniqueNonce(), fixture.services.now());

        assertEquals(TransferStatus.CANCELLED, cancelled.status);
        assertEquals(fixture.seller.ownerId, fixture.services.devices.currentOwner("device-1"));

        ApiException error = assertThrows(ApiException.class, () -> confirm(fixture, transfer));
        assertEquals(ErrorCode.TRANSFER_NOT_PENDING, error.code());
    }

    @Test
    void nonInitiatorCannotCancel() {
        Fixture fixture = newFixture();
        Transfer transfer = initiate(fixture);
        String buyerToken = fixture.services.sessionToken(fixture.buyer);

        ApiException error = assertThrows(ApiException.class,
                () -> fixture.services.transfers.cancel(transfer.transferId, buyerToken,
                        fixture.services.uniqueNonce(), fixture.services.now()));
        assertEquals(ErrorCode.TRANSFER_FORBIDDEN, error.code());
    }

    @Test
    void transferHistoryAndAuditAreRecorded() {
        Fixture fixture = newFixture();
        Transfer transfer = initiate(fixture);
        confirm(fixture, transfer);

        List<Transfer> history = fixture.services.transfers.historyForDevice("device-1");
        assertEquals(1, history.size());
        assertEquals(TransferStatus.COMPLETED, history.get(0).status);

        List<AuditRecord> audit = fixture.services.audit.forDevice("device-1");
        assertTrue(audit.stream().anyMatch(record -> record.action.equals("DEVICE_REGISTERED")));
        assertTrue(audit.stream().anyMatch(record -> record.action.equals("TRANSFER_INITIATED")));
        assertTrue(audit.stream().anyMatch(record -> record.action.equals("TRANSFER_COMPLETED")));
    }
}
