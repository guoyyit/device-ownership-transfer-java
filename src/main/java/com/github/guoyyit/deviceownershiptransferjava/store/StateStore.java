package com.github.guoyyit.deviceownershiptransferjava.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.deviceownershiptransferjava.config.AppProperties;
import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StateStore {

    private static final Logger log = LoggerFactory.getLogger(StateStore.class);

    private static final String ISSUER_FILE = "issuer.json";
    private static final String OWNERS_FILE = "owners.json";
    private static final String DEVICES_FILE = "devices.json";
    private static final String TRANSFERS_FILE = "transfers.json";
    private static final String SESSIONS_FILE = "sessions.json";
    private static final String NONCES_FILE = "nonces.json";
    private static final String AUDIT_FILE = "audit.log";

    private final AppProperties properties;
    private final CryptoService cryptoService;
    private final ObjectMapper mapper;

    private Path dataDir;
    private final Map<String, OwnerEntity> owners = new ConcurrentHashMap<>();
    private final Map<String, DeviceEntity> devices = new ConcurrentHashMap<>();
    private final Map<String, TransferEntity> transfers = new ConcurrentHashMap<>();
    private final Map<String, SessionEntity> sessions = new ConcurrentHashMap<>();
    private final Map<String, NonceEntity> nonces = new ConcurrentHashMap<>();
    private final List<AuditEntry> audit = Collections.synchronizedList(new ArrayList<>());
    private IssuerEntity issuer;
    private long auditSeq = 0;

    public StateStore(AppProperties properties, CryptoService cryptoService, ObjectMapper mapper) {
        this.properties = properties;
        this.cryptoService = cryptoService;
        this.mapper = mapper;
    }

    @PostConstruct
    public synchronized void init() throws IOException {
        this.dataDir = Paths.get(properties.getDataDir());
        Files.createDirectories(dataDir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir, "*.tmp")) {
            for (Path p : stream) {
                Files.deleteIfExists(p);
            }
        }
        loadIssuer();
        loadMap(OWNERS_FILE, new TypeReference<LinkedHashMap<String, OwnerEntity>>() {}, owners);
        loadMap(DEVICES_FILE, new TypeReference<LinkedHashMap<String, DeviceEntity>>() {}, devices);
        loadMap(TRANSFERS_FILE, new TypeReference<LinkedHashMap<String, TransferEntity>>() {}, transfers);
        loadMap(SESSIONS_FILE, new TypeReference<LinkedHashMap<String, SessionEntity>>() {}, sessions);
        loadNonces();
        loadAudit();
        log.info("state store initialized at {} (owners={}, devices={}, transfers={}, sessions={}, nonces={}, audit={})",
                dataDir, owners.size(), devices.size(), transfers.size(), sessions.size(), nonces.size(), audit.size());
    }

    public Path getDataDir() {
        return dataDir;
    }

    // ------------------------------------------------------------------ issuer

    public synchronized IssuerEntity getIssuer() {
        if (issuer == null) {
            Keys keys = cryptoService.generateKeyPair();
            issuer = new IssuerEntity();
            issuer.publicKey = keys.publicKey();
            issuer.privateKey = keys.privateKey();
            issuer.createdAt = System.currentTimeMillis();
            try {
                atomicWrite(ISSUER_FILE, issuer);
            } catch (IOException e) {
                throw new IllegalStateException("failed to persist issuer", e);
            }
        }
        return issuer;
    }

    public synchronized void saveIssuer(IssuerEntity issuer) {
        this.issuer = issuer;
        try {
            atomicWrite(ISSUER_FILE, issuer);
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist issuer", e);
        }
    }

    // ------------------------------------------------------------------ owners

    public synchronized OwnerEntity saveOwner(OwnerEntity owner) {
        owners.put(owner.ownerId, owner);
        try {
            atomicWrite(OWNERS_FILE, new LinkedHashMap<>(owners));
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist owner", e);
        }
        return owner;
    }

    public synchronized Optional<OwnerEntity> findOwner(String ownerId) {
        return Optional.ofNullable(owners.get(ownerId));
    }

    public synchronized Optional<OwnerEntity> findOwnerByPublicKey(String publicKey) {
        return owners.values().stream()
                .filter(o -> o.publicKey.equals(publicKey))
                .findFirst();
    }

    // ------------------------------------------------------------------ devices

    public synchronized DeviceEntity saveDevice(DeviceEntity device) {
        devices.put(device.deviceId, device);
        try {
            atomicWrite(DEVICES_FILE, new LinkedHashMap<>(devices));
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist device", e);
        }
        return device;
    }

    public synchronized Optional<DeviceEntity> findDevice(String deviceId) {
        return Optional.ofNullable(devices.get(deviceId));
    }

    // ------------------------------------------------------------------ transfers

    public synchronized TransferEntity saveTransfer(TransferEntity transfer) {
        transfers.put(transfer.transferId, transfer);
        try {
            atomicWrite(TRANSFERS_FILE, new LinkedHashMap<>(transfers));
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist transfer", e);
        }
        return transfer;
    }

    public synchronized Optional<TransferEntity> findTransfer(String transferId) {
        return Optional.ofNullable(transfers.get(transferId));
    }

    public synchronized Optional<TransferEntity> findPendingTransfer(String deviceId) {
        return transfers.values().stream()
                .filter(t -> t.deviceId.equals(deviceId) && "PENDING".equals(t.status))
                .findFirst();
    }

    public synchronized List<TransferEntity> listTransfers(String deviceId) {
        List<TransferEntity> result = new ArrayList<>();
        for (TransferEntity t : transfers.values()) {
            if (t.deviceId.equals(deviceId)) {
                result.add(t);
            }
        }
        result.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
        return result;
    }

    // ------------------------------------------------------------------ sessions

    public synchronized SessionEntity saveSession(SessionEntity session) {
        sessions.put(session.sessionId, session);
        try {
            atomicWrite(SESSIONS_FILE, new LinkedHashMap<>(sessions));
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist session", e);
        }
        return session;
    }

    public synchronized Optional<SessionEntity> findSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public synchronized List<SessionEntity> listSessionsForOwner(String deviceId, String ownerId) {
        List<SessionEntity> result = new ArrayList<>();
        for (SessionEntity s : sessions.values()) {
            if (s.deviceId.equals(deviceId) && s.ownerId.equals(ownerId)) {
                result.add(s);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ nonces

    public synchronized boolean isNonceUsed(String nonce) {
        return nonces.containsKey(nonce);
    }

    public synchronized void markNonceUsed(NonceEntity nonce) {
        nonces.put(nonce.nonce, nonce);
        try {
            atomicWrite(NONCES_FILE, new LinkedHashMap<>(nonces));
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist nonce", e);
        }
    }

    // ------------------------------------------------------------------ audit

    public synchronized void appendAudit(AuditEntry entry) {
        entry.seq = ++auditSeq;
        if (entry.timestamp == 0) {
            entry.timestamp = System.currentTimeMillis();
        }
        audit.add(entry);
        try {
            Path target = dataDir.resolve(AUDIT_FILE);
            String line = mapper.writeValueAsString(entry) + "\n";
            Files.write(target, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            try (FileChannel ch = FileChannel.open(target, StandardOpenOption.WRITE)) {
                ch.force(true);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to append audit", e);
        }
    }

    public synchronized List<AuditEntry> listAudit(String deviceId) {
        List<AuditEntry> result = new ArrayList<>();
        for (AuditEntry e : audit) {
            if (deviceId.equals(e.deviceId)) {
                result.add(e);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ internals

    private void loadIssuer() throws IOException {
        Path file = dataDir.resolve(ISSUER_FILE);
        if (Files.exists(file)) {
            issuer = mapper.readValue(Files.readAllBytes(file), IssuerEntity.class);
        }
    }

    private <T> void loadMap(String fileName, TypeReference<LinkedHashMap<String, T>> typeRef,
                             Map<String, T> target) throws IOException {
        Path file = dataDir.resolve(fileName);
        if (Files.exists(file)) {
            Map<String, T> loaded = mapper.readValue(Files.readAllBytes(file), typeRef);
            target.putAll(loaded);
        }
    }

    private void loadNonces() throws IOException {
        Path file = dataDir.resolve(NONCES_FILE);
        if (Files.exists(file)) {
            Map<String, NonceEntity> loaded = mapper.readValue(Files.readAllBytes(file),
                    new TypeReference<LinkedHashMap<String, NonceEntity>>() {});
            long cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L;
            for (Map.Entry<String, NonceEntity> e : loaded.entrySet()) {
                if (e.getValue().usedAt >= cutoff) {
                    nonces.put(e.getKey(), e.getValue());
                }
            }
        }
    }

    private void loadAudit() throws IOException {
        Path file = dataDir.resolve(AUDIT_FILE);
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                AuditEntry entry = mapper.readValue(line, AuditEntry.class);
                audit.add(entry);
                auditSeq = Math.max(auditSeq, entry.seq);
            }
        }
    }

    private void atomicWrite(String fileName, Object content) throws IOException {
        Path target = dataDir.resolve(fileName);
        Path tmp = dataDir.resolve(fileName + ".tmp");
        byte[] bytes = mapper.writeValueAsBytes(content);
        Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            ch.force(true);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        try (FileChannel ch = FileChannel.open(dataDir, StandardOpenOption.READ)) {
            ch.force(true);
        }
    }
}
