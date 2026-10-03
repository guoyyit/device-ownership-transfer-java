package com.github.guoyyit.deviceownershiptransferjava.tests;

import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.store.path=target/test-data/web-it/state.json")
class WebApiSmokeTest {

    static {
        Path dir = Path.of("target/test-data/web-it");
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
        } catch (IOException ignored) {
        }
    }

    @Autowired
    TestRestTemplate rest;

    private final CryptoService crypto = new CryptoService();

    private static String nonce() {
        return UUID.randomUUID().toString();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, Object body) {
        ResponseEntity<Map> response = rest.postForEntity(url, body, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "POST " + url + " -> " + response.getBody());
        return response.getBody();
    }

    @Test
    void fullOwnershipTransferOverHttp() {
        Map<String, Object> seller = post("/api/owners", Map.of());
        Map<String, Object> buyer = post("/api/owners", Map.of());
        String sellerId = (String) seller.get("ownerId");
        String buyerId = (String) buyer.get("ownerId");

        Map<String, Object> session = post("/api/sessions",
                Map.of("ownerId", sellerId, "secret", seller.get("secret")));
        String sellerToken = (String) session.get("token");

        KeyPair deviceKeys = crypto.generateKeyPair();
        String devicePublicKey = crypto.encodePublicKey(deviceKeys.getPublic());
        String deviceId = "device-http-1";

        Map<String, Object> issued = post("/api/credentials",
                Map.of("deviceId", deviceId, "devicePublicKey", devicePublicKey, "ownerId", sellerId));
        Map<String, Object> credential = (Map<String, Object>) issued.get("credential");
        assertNotNull(credential.get("signature"));

        Map<String, Object> registerBody = new HashMap<>();
        registerBody.put("deviceId", deviceId);
        registerBody.put("publicKey", devicePublicKey);
        registerBody.put("credential", credential);
        registerBody.put("nonce", nonce());
        registerBody.put("timestamp", now());
        Map<String, Object> registered = post("/api/devices/register", registerBody);
        assertEquals(sellerId, registered.get("ownerId"));

        // replaying the exact same registration request must be rejected
        ResponseEntity<Map> replay = rest.postForEntity("/api/devices/register", registerBody, Map.class);
        assertEquals(HttpStatus.CONFLICT, replay.getStatusCode());
        assertEquals("REPLAY_DETECTED", replay.getBody().get("code"));

        // registering the same device again with a fresh nonce must also be rejected
        registerBody.put("nonce", nonce());
        ResponseEntity<Map> duplicate = rest.postForEntity("/api/devices/register", registerBody, Map.class);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertEquals("DEVICE_ALREADY_REGISTERED", duplicate.getBody().get("code"));

        ResponseEntity<Map> ownerBefore = rest.getForEntity("/api/devices/" + deviceId + "/owner", Map.class);
        assertEquals(sellerId, ownerBefore.getBody().get("ownerId"));

        // initiate transfer while device is "offline"
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Owner-Token", sellerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> initiateBody = Map.of("toOwnerId", buyerId, "nonce", nonce(), "timestamp", now());
        ResponseEntity<Map> initiated = rest.exchange("/api/devices/" + deviceId + "/transfers",
                HttpMethod.POST, new HttpEntity<>(initiateBody, headers), Map.class);
        assertEquals(HttpStatus.OK, initiated.getStatusCode());
        String transferId = (String) initiated.getBody().get("transferId");
        assertEquals("PENDING", initiated.getBody().get("status"));

        // second initiation while pending must conflict
        Map<String, Object> secondBody = Map.of("toOwnerId", buyerId, "nonce", nonce(), "timestamp", now());
        ResponseEntity<Map> conflict = rest.exchange("/api/devices/" + deviceId + "/transfers",
                HttpMethod.POST, new HttpEntity<>(secondBody, headers), Map.class);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("TRANSFER_CONFLICT", conflict.getBody().get("code"));

        // device reconnects and polls for the pending transfer
        String pollNonce = nonce();
        long pollTs = now();
        String pollSignature = Base64.getEncoder().encodeToString(crypto.sign(deviceKeys.getPrivate(),
                ("POLL|" + deviceId + "|" + pollNonce + "|" + pollTs).getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> pollBody = Map.of("deviceId", deviceId, "signature", pollSignature,
                "nonce", pollNonce, "timestamp", pollTs);
        Map<String, Object> polled = post("/api/devices/" + deviceId + "/pending-transfer", pollBody);
        Map<String, Object> pending = (Map<String, Object>) polled.get("pending");
        assertNotNull(pending);
        assertEquals(transferId, pending.get("transferId"));
        String challenge = (String) pending.get("confirmChallenge");

        // device confirms the transfer
        String confirmNonce = nonce();
        long confirmTs = now();
        String confirmSignature = Base64.getEncoder().encodeToString(crypto.sign(deviceKeys.getPrivate(),
                ("CONFIRM|" + transferId + "|" + challenge + "|" + confirmNonce + "|" + confirmTs)
                        .getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> confirmBody = Map.of("deviceId", deviceId, "signature", confirmSignature,
                "nonce", confirmNonce, "timestamp", confirmTs);
        Map<String, Object> confirmed = post("/api/transfers/" + transferId + "/confirm", confirmBody);
        assertEquals("COMPLETED", confirmed.get("status"));

        ResponseEntity<Map> ownerAfter = rest.getForEntity("/api/devices/" + deviceId + "/owner", Map.class);
        assertEquals(buyerId, ownerAfter.getBody().get("ownerId"));

        // old owner token is revoked immediately
        Map<String, Object> hijackBody = Map.of("toOwnerId", sellerId, "nonce", nonce(), "timestamp", now());
        ResponseEntity<Map> hijack = rest.exchange("/api/devices/" + deviceId + "/transfers",
                HttpMethod.POST, new HttpEntity<>(hijackBody, headers), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, hijack.getStatusCode());
        assertEquals("SESSION_REVOKED", hijack.getBody().get("code"));

        // history and audit are queryable
        ResponseEntity<List> history = rest.getForEntity("/api/devices/" + deviceId + "/transfers", List.class);
        assertEquals(1, history.getBody().size());
        ResponseEntity<List> audit = rest.getForEntity("/api/devices/" + deviceId + "/audit", List.class);
        assertTrue(audit.getBody().size() >= 3);
    }

    @Test
    void unknownDeviceReturnsNotFound() {
        ResponseEntity<Map> response = rest.getForEntity("/api/devices/no-such-device/owner", Map.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("DEVICE_NOT_FOUND", response.getBody().get("code"));
    }
}
