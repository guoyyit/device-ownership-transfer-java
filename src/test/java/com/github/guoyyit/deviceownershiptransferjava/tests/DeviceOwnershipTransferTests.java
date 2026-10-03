package com.github.guoyyit.deviceownershiptransferjava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.Payloads;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpStatusCodeException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceOwnershipTransferTests {

    static final Path tempDir;

    static {
        tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "dot-tests", UUID.randomUUID().toString());
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.data-dir", () -> tempDir.toString());
        registry.add("app.timestamp-window-ms", () -> "600000");
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    CryptoService crypto;
    @Autowired
    ObjectMapper objectMapper;

    private String newNonce() {
        return UUID.randomUUID().toString();
    }

    private OwnerResponse createOwner(String name) {
        return rest.postForEntity("/api/owners", new CreateOwnerRequest(name), OwnerResponse.class).getBody();
    }

    private ProvisionedDeviceResponse provisionDevice(String ownerId) {
        return rest.postForEntity("/api/issuer/devices", new ProvisionDeviceRequest(ownerId),
                ProvisionedDeviceResponse.class).getBody();
    }

    private RegisterDeviceRequest buildRegisterRequest(ProvisionedDeviceResponse prov) {
        long ts = System.currentTimeMillis();
        String nonce = newNonce();
        String payload = Payloads.register(prov.deviceId(), prov.devicePublicKey(), nonce, ts);
        String sig = crypto.sign(prov.devicePrivateKey(), payload);
        return new RegisterDeviceRequest(prov.deviceId(), prov.devicePublicKey(), prov.credential(),
                nonce, ts, sig);
    }

    private DeviceResponse registerDevice(ProvisionedDeviceResponse prov) {
        return rest.postForEntity("/api/devices/register", buildRegisterRequest(prov), DeviceResponse.class).getBody();
    }

    private InitiateTransferRequest buildInitiateRequest(OwnerResponse owner, String deviceId,
                                                          String newOwnerPubKey) {
        long ts = System.currentTimeMillis();
        String nonce = newNonce();
        String payload = Payloads.transferInitiate(deviceId, newOwnerPubKey, nonce, ts);
        String sig = crypto.sign(owner.privateKey(), payload);
        return new InitiateTransferRequest(newOwnerPubKey, nonce, ts, sig);
    }

    private PendingTransferRequest buildPendingRequest(ProvisionedDeviceResponse prov) {
        long ts = System.currentTimeMillis();
        String nonce = newNonce();
        String payload = Payloads.transferPending(prov.deviceId(), nonce, ts);
        String sig = crypto.sign(prov.devicePrivateKey(), payload);
        return new PendingTransferRequest(nonce, ts, sig);
    }

    private ConfirmTransferRequest buildConfirmRequest(ProvisionedDeviceResponse prov, String transferId) {
        long ts = System.currentTimeMillis();
        String nonce = newNonce();
        String payload = Payloads.transferConfirm(transferId, prov.deviceId(), nonce, ts);
        String sig = crypto.sign(prov.devicePrivateKey(), payload);
        return new ConfirmTransferRequest(transferId, nonce, ts, sig);
    }

    private RejectTransferRequest buildRejectRequest(ProvisionedDeviceResponse prov, String transferId) {
        long ts = System.currentTimeMillis();
        String nonce = newNonce();
        String payload = Payloads.transferReject(transferId, prov.deviceId(), nonce, ts);
        String sig = crypto.sign(prov.devicePrivateKey(), payload);
        return new RejectTransferRequest(transferId, nonce, ts, sig);
    }

    private CancelTransferRequest buildCancelRequest(OwnerResponse owner, String deviceId, String transferId) {
        long ts = System.currentTimeMillis();
        String nonce = newNonce();
        String payload = Payloads.transferCancel(transferId, deviceId, nonce, ts);
        String sig = crypto.sign(owner.privateKey(), payload);
        return new CancelTransferRequest(transferId, nonce, ts, sig);
    }

    private CreateSessionRequest buildSessionRequest(OwnerResponse owner, String deviceId) {
        long ts = System.currentTimeMillis();
        String nonce = newNonce();
        String payload = Payloads.sessionCreate(deviceId, owner.publicKey(), nonce, ts);
        String sig = crypto.sign(owner.privateKey(), payload);
        return new CreateSessionRequest(nonce, ts, sig);
    }

    private ResponseEntity<String> postRaw(String url, Object body) {
        try {
            return rest.postForEntity(url, body, String.class);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    private ErrorResponse parseError(ResponseEntity<String> resp) throws Exception {
        return objectMapper.readValue(resp.getBody(), ErrorResponse.class);
    }

    private TransferResponse initiateTransfer(OwnerResponse owner, String deviceId, String newOwnerPubKey) {
        return rest.postForEntity("/api/devices/" + deviceId + "/transfers",
                buildInitiateRequest(owner, deviceId, newOwnerPubKey), TransferResponse.class).getBody();
    }

    private TransferResponse fetchPending(ProvisionedDeviceResponse prov) {
        return rest.postForEntity("/api/devices/" + prov.deviceId() + "/transfer/pending",
                buildPendingRequest(prov), TransferResponse.class).getBody();
    }

    private TransferResponse confirmTransfer(ProvisionedDeviceResponse prov, String transferId) {
        return rest.postForEntity("/api/devices/" + prov.deviceId() + "/transfer/confirm",
                buildConfirmRequest(prov, transferId), TransferResponse.class).getBody();
    }

    private TransferResponse rejectTransfer(ProvisionedDeviceResponse prov, String transferId) {
        return rest.postForEntity("/api/devices/" + prov.deviceId() + "/transfer/reject",
                buildRejectRequest(prov, transferId), TransferResponse.class).getBody();
    }

    private TransferResponse cancelTransfer(OwnerResponse owner, String deviceId, String transferId) {
        return rest.postForEntity("/api/devices/" + deviceId + "/transfer/cancel",
                buildCancelRequest(owner, deviceId, transferId), TransferResponse.class).getBody();
    }

    private SessionResponse createSession(OwnerResponse owner, String deviceId) {
        return rest.postForEntity("/api/devices/" + deviceId + "/sessions",
                buildSessionRequest(owner, deviceId), SessionResponse.class).getBody();
    }

    private SessionVerifyResponse verifySession(String sessionId) {
        return rest.postForEntity("/api/sessions/" + sessionId + "/verify", null,
                SessionVerifyResponse.class).getBody();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void registerDevice_success() {
        OwnerResponse owner = createOwner("alice");
        ProvisionedDeviceResponse prov = provisionDevice(owner.ownerId());
        DeviceResponse device = registerDevice(prov);

        assertNotNull(device);
        assertEquals(prov.deviceId(), device.deviceId());
        assertEquals(owner.ownerId(), device.ownerId());
        assertEquals(0L, device.epoch());

        DeviceResponse fetched = rest.getForEntity("/api/devices/" + prov.deviceId(), DeviceResponse.class).getBody();
        assertEquals(owner.ownerId(), fetched.ownerId());
        assertEquals("alice", fetched.ownerName());
    }

    @Test
    void register_forgedCredential_rejected() throws Exception {
        OwnerResponse owner = createOwner("alice");
        ProvisionedDeviceResponse prov = provisionDevice(owner.ownerId());

        // Forge the credential: sign a different payload with a random key
        String forgedPayload = Payloads.ownershipCredential(prov.deviceId(), prov.devicePublicKey(),
                owner.ownerId(), owner.publicKey(), "forged-nonce", System.currentTimeMillis() + 100000);
        String forgedSig = crypto.sign(crypto.generateKeyPair().privateKey(), forgedPayload);
        String forgedCredential = crypto.encodeToken(forgedPayload, forgedSig);

        long ts = System.currentTimeMillis();
        String nonce = newNonce();
        String regPayload = Payloads.register(prov.deviceId(), prov.devicePublicKey(), nonce, ts);
        String devSig = crypto.sign(prov.devicePrivateKey(), regPayload);
        RegisterDeviceRequest req = new RegisterDeviceRequest(prov.deviceId(), prov.devicePublicKey(),
                forgedCredential, nonce, ts, devSig);

        ResponseEntity<String> resp = postRaw("/api/devices/register", req);
        assertEquals(HttpStatusCode.valueOf(401), resp.getStatusCode());
        ErrorResponse err = parseError(resp);
        assertEquals("CREDENTIAL_INVALID", err.error());
    }

    @Test
    void register_credentialDeviceMismatch_rejected() throws Exception {
        OwnerResponse owner = createOwner("alice");
        ProvisionedDeviceResponse provA = provisionDevice(owner.ownerId());
        ProvisionedDeviceResponse provB = provisionDevice(owner.ownerId());

        // Use device B's credential with device A's identity
        long ts = System.currentTimeMillis();
        String nonce = newNonce();
        String regPayload = Payloads.register(provA.deviceId(), provA.devicePublicKey(), nonce, ts);
        String devSig = crypto.sign(provA.devicePrivateKey(), regPayload);
        RegisterDeviceRequest req = new RegisterDeviceRequest(provA.deviceId(), provA.devicePublicKey(),
                provB.credential(), nonce, ts, devSig);

        ResponseEntity<String> resp = postRaw("/api/devices/register", req);
        assertEquals(HttpStatusCode.valueOf(400), resp.getStatusCode());
        ErrorResponse err = parseError(resp);
        assertEquals("CREDENTIAL_DEVICE_MISMATCH", err.error());
    }

    @Test
    void register_duplicateDevice_rejected() throws Exception {
        OwnerResponse owner = createOwner("alice");
        ProvisionedDeviceResponse prov = provisionDevice(owner.ownerId());
        registerDevice(prov);

        ResponseEntity<String> resp = postRaw("/api/devices/register", buildRegisterRequest(prov));
        assertEquals(HttpStatusCode.valueOf(409), resp.getStatusCode());
        ErrorResponse err = parseError(resp);
        assertEquals("DEVICE_ALREADY_REGISTERED", err.error());
    }

    @Test
    void register_replayNonce_rejected() throws Exception {
        OwnerResponse owner = createOwner("alice");
        ProvisionedDeviceResponse prov = provisionDevice(owner.ownerId());
        RegisterDeviceRequest req = buildRegisterRequest(prov);
        registerDevice(prov);

        // Replay the exact same request
        ResponseEntity<String> resp = postRaw("/api/devices/register", req);
        assertEquals(HttpStatusCode.valueOf(409), resp.getStatusCode());
        ErrorResponse err = parseError(resp);
        assertEquals("DEVICE_ALREADY_REGISTERED", err.error());
    }

    @Test
    void transfer_happyPath() {
        OwnerResponse alice = createOwner("alice");
        OwnerResponse bob = createOwner("bob");
        ProvisionedDeviceResponse prov = provisionDevice(alice.ownerId());
        registerDevice(prov);

        // Alice creates a session before transfer
        SessionResponse aliceSession = createSession(alice, prov.deviceId());
        assertTrue(verifySession(aliceSession.sessionId()).valid());

        // Alice initiates transfer to Bob
        TransferResponse transfer = initiateTransfer(alice, prov.deviceId(), bob.publicKey());
        assertEquals("PENDING", transfer.status());

        // Device fetches pending and confirms
        TransferResponse pending = fetchPending(prov);
        assertEquals(transfer.transferId(), pending.transferId());
        TransferResponse confirmed = confirmTransfer(prov, transfer.transferId());
        assertEquals("CONFIRMED", confirmed.status());

        // Owner changed to Bob
        DeviceResponse device = rest.getForEntity("/api/devices/" + prov.deviceId(), DeviceResponse.class).getBody();
        assertEquals(bob.ownerId(), device.ownerId());
        assertEquals(1L, device.epoch());

        // Alice's session is now invalid
        SessionVerifyResponse aliceVerify = verifySession(aliceSession.sessionId());
        assertFalse(aliceVerify.valid());
        assertEquals("REVOKED", aliceVerify.reason());

        // Bob can create a new session
        SessionResponse bobSession = createSession(bob, prov.deviceId());
        assertTrue(verifySession(bobSession.sessionId()).valid());
    }

    @Test
    void transfer_replayInitiate_rejected() throws Exception {
        OwnerResponse alice = createOwner("alice");
        OwnerResponse bob = createOwner("bob");
        ProvisionedDeviceResponse prov = provisionDevice(alice.ownerId());
        registerDevice(prov);

        // Build one signed request and submit it once
        InitiateTransferRequest req = buildInitiateRequest(alice, prov.deviceId(), bob.publicKey());
        rest.postForEntity("/api/devices/" + prov.deviceId() + "/transfers", req, TransferResponse.class).getBody();

        // Replay the exact same request (same nonce)
        ResponseEntity<String> resp = postRaw("/api/devices/" + prov.deviceId() + "/transfers", req);
        assertEquals(HttpStatusCode.valueOf(409), resp.getStatusCode());
        ErrorResponse err = parseError(resp);
        assertEquals("NONCE_REUSED", err.error());
    }

    @Test
    void transfer_credentialLeak_cannotTakeover() throws Exception {
        OwnerResponse alice = createOwner("alice");
        OwnerResponse bob = createOwner("bob");
        ProvisionedDeviceResponse prov = provisionDevice(alice.ownerId());
        registerDevice(prov);

        // Attacker (Bob) has the leaked credential and tries to register the device again
        long ts = System.currentTimeMillis();
        String nonce = newNonce();
        String regPayload = Payloads.register(prov.deviceId(), prov.devicePublicKey(), nonce, ts);
        // Bob does not have the device private key, so he signs with his own key
        String devSig = crypto.sign(bob.privateKey(), regPayload);
        RegisterDeviceRequest req = new RegisterDeviceRequest(prov.deviceId(), prov.devicePublicKey(),
                prov.credential(), nonce, ts, devSig);
        ResponseEntity<String> resp = postRaw("/api/devices/register", req);
        assertEquals(HttpStatusCode.valueOf(409), resp.getStatusCode());
        ErrorResponse err = parseError(resp);
        assertEquals("DEVICE_ALREADY_REGISTERED", err.error());

        // Bob tries to initiate a transfer without Alice's owner key
        InitiateTransferRequest initReq = buildInitiateRequest(bob, prov.deviceId(), bob.publicKey());
        ResponseEntity<String> initResp = postRaw("/api/devices/" + prov.deviceId() + "/transfers", initReq);
        assertEquals(HttpStatusCode.valueOf(401), initResp.getStatusCode());
        ErrorResponse initErr = parseError(initResp);
        assertEquals("INVALID_SIGNATURE", initErr.error());
    }

    @Test
    void offline_transfer() {
        OwnerResponse alice = createOwner("alice");
        OwnerResponse bob = createOwner("bob");
        ProvisionedDeviceResponse prov = provisionDevice(alice.ownerId());
        registerDevice(prov);

        // Device is offline; Alice initiates transfer (stays pending)
        TransferResponse transfer = initiateTransfer(alice, prov.deviceId(), bob.publicKey());
        assertEquals("PENDING", transfer.status());

        // While offline, Bob cannot fetch pending (device-only endpoint) - but the pending transfer persists
        // Device reconnects and fetches pending
        TransferResponse pending = fetchPending(prov);
        assertEquals(transfer.transferId(), pending.transferId());
        assertEquals("PENDING", pending.status());

        // Device confirms
        TransferResponse confirmed = confirmTransfer(prov, transfer.transferId());
        assertEquals("CONFIRMED", confirmed.status());

        DeviceResponse device = rest.getForEntity("/api/devices/" + prov.deviceId(), DeviceResponse.class).getBody();
        assertEquals(bob.ownerId(), device.ownerId());
    }

    @Test
    void concurrent_transfers() throws Exception {
        OwnerResponse alice = createOwner("alice");
        OwnerResponse bob = createOwner("bob");
        ProvisionedDeviceResponse prov = provisionDevice(alice.ownerId());
        registerDevice(prov);

        int n = 10;
        ExecutorService executor = Executors.newFixedThreadPool(n);
        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tasks.add(() -> postRaw("/api/devices/" + prov.deviceId() + "/transfers",
                    buildInitiateRequest(alice, prov.deviceId(), bob.publicKey())));
        }
        List<Future<ResponseEntity<String>>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        int ok = 0;
        int conflict = 0;
        for (Future<ResponseEntity<String>> f : futures) {
            ResponseEntity<String> resp = f.get();
            if (resp.getStatusCode().is2xxSuccessful()) {
                ok++;
            } else if (resp.getStatusCode().value() == 409) {
                conflict++;
                ErrorResponse err = objectMapper.readValue(resp.getBody(), ErrorResponse.class);
                assertEquals("PENDING_TRANSFER_EXISTS", err.error());
            }
        }
        assertEquals(1, ok, "exactly one transfer should succeed");
        assertEquals(n - 1, conflict, "the rest should be rejected with 409");

        // Final owner is still Alice (transfer pending, not confirmed)
        DeviceResponse device = rest.getForEntity("/api/devices/" + prov.deviceId(), DeviceResponse.class).getBody();
        assertEquals(alice.ownerId(), device.ownerId());
    }

    @Test
    void cancel_and_confirm_race() throws Exception {
        OwnerResponse alice = createOwner("alice");
        OwnerResponse bob = createOwner("bob");
        ProvisionedDeviceResponse prov = provisionDevice(alice.ownerId());
        registerDevice(prov);

        TransferResponse transfer = initiateTransfer(alice, prov.deviceId(), bob.publicKey());
        String transferId = transfer.transferId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ResponseEntity<String>> confirmFuture = executor.submit(() ->
                postRaw("/api/devices/" + prov.deviceId() + "/transfer/confirm",
                        buildConfirmRequest(prov, transferId)));
        Future<ResponseEntity<String>> cancelFuture = executor.submit(() ->
                postRaw("/api/devices/" + prov.deviceId() + "/transfer/cancel",
                        buildCancelRequest(alice, prov.deviceId(), transferId)));
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        ResponseEntity<String> confirmResp = confirmFuture.get();
        ResponseEntity<String> cancelResp = cancelFuture.get();

        // Exactly one should succeed, the other should get 409
        boolean confirmOk = confirmResp.getStatusCode().is2xxSuccessful();
        boolean cancelOk = cancelResp.getStatusCode().is2xxSuccessful();
        assertTrue(confirmOk ^ cancelOk, "exactly one of confirm/cancel should win");

        if (confirmOk) {
            // Confirm won: owner changed to Bob
            DeviceResponse device = rest.getForEntity("/api/devices/" + prov.deviceId(), DeviceResponse.class).getBody();
            assertEquals(bob.ownerId(), device.ownerId());
            TransferResponse t = objectMapper.readValue(confirmResp.getBody(), TransferResponse.class);
            assertEquals("CONFIRMED", t.status());
        } else {
            // Cancel won: owner unchanged
            DeviceResponse device = rest.getForEntity("/api/devices/" + prov.deviceId(), DeviceResponse.class).getBody();
            assertEquals(alice.ownerId(), device.ownerId());
            TransferResponse t = objectMapper.readValue(cancelResp.getBody(), TransferResponse.class);
            assertEquals("CANCELLED", t.status());
        }
    }

    @Test
    void reject_transfer() {
        OwnerResponse alice = createOwner("alice");
        OwnerResponse bob = createOwner("bob");
        ProvisionedDeviceResponse prov = provisionDevice(alice.ownerId());
        registerDevice(prov);

        TransferResponse transfer = initiateTransfer(alice, prov.deviceId(), bob.publicKey());
        TransferResponse rejected = rejectTransfer(prov, transfer.transferId());
        assertEquals("REJECTED", rejected.status());

        // Owner unchanged
        DeviceResponse device = rest.getForEntity("/api/devices/" + prov.deviceId(), DeviceResponse.class).getBody();
        assertEquals(alice.ownerId(), device.ownerId());

        // Can initiate a new transfer after rejection
        TransferResponse newTransfer = initiateTransfer(alice, prov.deviceId(), bob.publicKey());
        assertEquals("PENDING", newTransfer.status());
    }

    @Test
    void cancel_transfer() {
        OwnerResponse alice = createOwner("alice");
        OwnerResponse bob = createOwner("bob");
        ProvisionedDeviceResponse prov = provisionDevice(alice.ownerId());
        registerDevice(prov);

        TransferResponse transfer = initiateTransfer(alice, prov.deviceId(), bob.publicKey());
        TransferResponse cancelled = cancelTransfer(alice, prov.deviceId(), transfer.transferId());
        assertEquals("CANCELLED", cancelled.status());

        // Owner unchanged
        DeviceResponse device = rest.getForEntity("/api/devices/" + prov.deviceId(), DeviceResponse.class).getBody();
        assertEquals(alice.ownerId(), device.ownerId());

        // Can initiate a new transfer after cancellation
        TransferResponse newTransfer = initiateTransfer(alice, prov.deviceId(), bob.publicKey());
        assertEquals("PENDING", newTransfer.status());
    }

    @Test
    void pendingBlocksNewTransfer() throws Exception {
        OwnerResponse alice = createOwner("alice");
        OwnerResponse bob = createOwner("bob");
        ProvisionedDeviceResponse prov = provisionDevice(alice.ownerId());
        registerDevice(prov);

        initiateTransfer(alice, prov.deviceId(), bob.publicKey());

        // Second transfer attempt while one is pending
        ResponseEntity<String> resp = postRaw("/api/devices/" + prov.deviceId() + "/transfers",
                buildInitiateRequest(alice, prov.deviceId(), bob.publicKey()));
        assertEquals(HttpStatusCode.valueOf(409), resp.getStatusCode());
        ErrorResponse err = parseError(resp);
        assertEquals("PENDING_TRANSFER_EXISTS", err.error());
    }

    @Test
    void transferHistory_andAudit() {
        OwnerResponse alice = createOwner("alice");
        OwnerResponse bob = createOwner("bob");
        ProvisionedDeviceResponse prov = provisionDevice(alice.ownerId());
        registerDevice(prov);

        TransferResponse t1 = initiateTransfer(alice, prov.deviceId(), bob.publicKey());
        rejectTransfer(prov, t1.transferId());
        TransferResponse t2 = initiateTransfer(alice, prov.deviceId(), bob.publicKey());
        confirmTransfer(prov, t2.transferId());

        TransferResponse[] history = rest.getForEntity("/api/devices/" + prov.deviceId() + "/transfers",
                TransferResponse[].class).getBody();
        assertEquals(2, history.length);
        assertEquals("REJECTED", history[0].status());
        assertEquals("CONFIRMED", history[1].status());

        // Audit log exists
        ResponseEntity<String> auditResp = rest.getForEntity("/api/devices/" + prov.deviceId() + "/audit",
                String.class);
        assertTrue(auditResp.getBody().contains("DEVICE_REGISTERED"));
        assertTrue(auditResp.getBody().contains("TRANSFER_INITIATED"));
        assertTrue(auditResp.getBody().contains("TRANSFER_REJECTED"));
        assertTrue(auditResp.getBody().contains("TRANSFER_CONFIRMED"));
    }
}
