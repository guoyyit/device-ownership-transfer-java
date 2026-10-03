package com.github.guoyyit.deviceownershiptransferjava.tests;

import com.github.guoyyit.deviceownershiptransferjava.Application;
import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.Payloads;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrashRecoveryTests {

    @TempDir
    static Path tempDir;

    private ConfigurableApplicationContext bootApp(String dataDir) {
        return new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.SERVLET)
                .properties("app.data-dir=" + dataDir, "server.port=0",
                        "app.timestamp-window-ms=600000")
                .run();
    }

    private int getPort(ConfigurableApplicationContext context) {
        return context.getEnvironment().getProperty("local.server.port", Integer.class, 0);
    }

    private String newNonce() {
        return UUID.randomUUID().toString();
    }

    @Test
    void crashRecovery_preservesState() {
        String dataDir = tempDir.toString();

        // ---------------- first boot ----------------
        ConfigurableApplicationContext ctx1 = bootApp(dataDir);
        int port1 = getPort(ctx1);
        TestRestTemplate rest = new TestRestTemplate();
        CryptoService crypto = ctx1.getBean(CryptoService.class);
        String base1 = "http://localhost:" + port1;

        OwnerResponse alice = rest.postForEntity(base1 + "/api/owners",
                new CreateOwnerRequest("alice"), OwnerResponse.class).getBody();
        OwnerResponse bob = rest.postForEntity(base1 + "/api/owners",
                new CreateOwnerRequest("bob"), OwnerResponse.class).getBody();
        ProvisionedDeviceResponse prov = rest.postForEntity(base1 + "/api/issuer/devices",
                new ProvisionDeviceRequest(alice.ownerId()), ProvisionedDeviceResponse.class).getBody();

        // Register
        long ts = System.currentTimeMillis();
        String nonce = newNonce();
        String regPayload = Payloads.register(prov.deviceId(), prov.devicePublicKey(), nonce, ts);
        String devSig = crypto.sign(prov.devicePrivateKey(), regPayload);
        RegisterDeviceRequest regReq = new RegisterDeviceRequest(prov.deviceId(), prov.devicePublicKey(),
                prov.credential(), nonce, ts, devSig);
        rest.postForEntity(base1 + "/api/devices/register", regReq, DeviceResponse.class).getBody();

        // Create a session for Alice
        long tsS = System.currentTimeMillis();
        String nonceS = newNonce();
        String sessPayload = Payloads.sessionCreate(prov.deviceId(), alice.publicKey(), nonceS, tsS);
        String sessSig = crypto.sign(alice.privateKey(), sessPayload);
        CreateSessionRequest sessReq = new CreateSessionRequest(nonceS, tsS, sessSig);
        SessionResponse aliceSession = rest.postForEntity(base1 + "/api/devices/" + prov.deviceId() + "/sessions",
                sessReq, SessionResponse.class).getBody();

        // Initiate a pending transfer
        long tsT = System.currentTimeMillis();
        String nonceT = newNonce();
        String initPayload = Payloads.transferInitiate(prov.deviceId(), bob.publicKey(), nonceT, tsT);
        String initSig = crypto.sign(alice.privateKey(), initPayload);
        InitiateTransferRequest initReq = new InitiateTransferRequest(bob.publicKey(), nonceT, tsT, initSig);
        TransferResponse pendingTransfer = rest.postForEntity(base1 + "/api/devices/" + prov.deviceId() + "/transfers",
                initReq, TransferResponse.class).getBody();
        assertEquals("PENDING", pendingTransfer.status());

        // Close the first context (simulate crash)
        ctx1.close();

        // ---------------- second boot ----------------
        ConfigurableApplicationContext ctx2 = bootApp(dataDir);
        int port2 = getPort(ctx2);
        TestRestTemplate rest2 = new TestRestTemplate();
        CryptoService crypto2 = ctx2.getBean(CryptoService.class);
        String base2 = "http://localhost:" + port2;

        // Device owner is still Alice
        DeviceResponse device = rest2.getForEntity(base2 + "/api/devices/" + prov.deviceId(),
                DeviceResponse.class).getBody();
        assertEquals(alice.ownerId(), device.ownerId());
        assertEquals(0L, device.epoch());

        // Alice's session is still valid (epoch persisted)
        SessionVerifyResponse sessVerify = rest2.postForEntity(
                base2 + "/api/sessions/" + aliceSession.sessionId() + "/verify", null,
                SessionVerifyResponse.class).getBody();
        assertTrue(sessVerify.valid());

        // Pending transfer is recovered
        long tsP = System.currentTimeMillis();
        String nonceP = newNonce();
        String pendPayload = Payloads.transferPending(prov.deviceId(), nonceP, tsP);
        String pendSig = crypto2.sign(prov.devicePrivateKey(), pendPayload);
        PendingTransferRequest pendReq = new PendingTransferRequest(nonceP, tsP, pendSig);
        TransferResponse recoveredPending = rest2.postForEntity(
                base2 + "/api/devices/" + prov.deviceId() + "/transfer/pending", pendReq,
                TransferResponse.class).getBody();
        assertEquals(pendingTransfer.transferId(), recoveredPending.transferId());
        assertEquals("PENDING", recoveredPending.status());

        // Device confirms the transfer after restart
        long tsC = System.currentTimeMillis();
        String nonceC = newNonce();
        String confPayload = Payloads.transferConfirm(pendingTransfer.transferId(), prov.deviceId(), nonceC, tsC);
        String confSig = crypto2.sign(prov.devicePrivateKey(), confPayload);
        ConfirmTransferRequest confReq = new ConfirmTransferRequest(pendingTransfer.transferId(), nonceC, tsC, confSig);
        TransferResponse confirmed = rest2.postForEntity(
                base2 + "/api/devices/" + prov.deviceId() + "/transfer/confirm", confReq,
                TransferResponse.class).getBody();
        assertEquals("CONFIRMED", confirmed.status());

        // Owner changed to Bob
        DeviceResponse deviceAfter = rest2.getForEntity(base2 + "/api/devices/" + prov.deviceId(),
                DeviceResponse.class).getBody();
        assertEquals(bob.ownerId(), deviceAfter.ownerId());
        assertEquals(1L, deviceAfter.epoch());

        // Alice's session is now invalid (epoch bumped)
        SessionVerifyResponse sessVerifyAfter = rest2.postForEntity(
                base2 + "/api/sessions/" + aliceSession.sessionId() + "/verify", null,
                SessionVerifyResponse.class).getBody();
        assertFalse(sessVerifyAfter.valid());
        assertEquals("REVOKED", sessVerifyAfter.reason());

        // Bob can create a new session
        long tsB = System.currentTimeMillis();
        String nonceB = newNonce();
        String sessPayloadB = Payloads.sessionCreate(prov.deviceId(), bob.publicKey(), nonceB, tsB);
        String sessSigB = crypto2.sign(bob.privateKey(), sessPayloadB);
        CreateSessionRequest sessReqB = new CreateSessionRequest(nonceB, tsB, sessSigB);
        SessionResponse bobSession = rest2.postForEntity(base2 + "/api/devices/" + prov.deviceId() + "/sessions",
                sessReqB, SessionResponse.class).getBody();
        SessionVerifyResponse bobVerify = rest2.postForEntity(
                base2 + "/api/sessions/" + bobSession.sessionId() + "/verify", null,
                SessionVerifyResponse.class).getBody();
        assertTrue(bobVerify.valid());

        ctx2.close();
    }
}
