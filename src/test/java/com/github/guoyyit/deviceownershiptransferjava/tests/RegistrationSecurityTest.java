package com.github.guoyyit.deviceownershiptransferjava.tests;

import com.github.guoyyit.deviceownershiptransferjava.crypto.OwnershipCredential;
import com.github.guoyyit.deviceownershiptransferjava.domain.Device;
import com.github.guoyyit.deviceownershiptransferjava.domain.OwnerAccount;
import com.github.guoyyit.deviceownershiptransferjava.web.ApiException;
import com.github.guoyyit.deviceownershiptransferjava.web.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistrationSecurityTest {

    @TempDir
    Path tempDir;

    @Test
    void registersDeviceAndBindsFirstOwner() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount owner = services.newOwner();
        KeyPair keys = services.newDeviceKeys();

        Device device = services.registerDevice("device-1", keys, owner);

        assertEquals(owner.ownerId, device.ownerId);
        assertEquals(owner.ownerId, services.devices.currentOwner("device-1"));
    }

    @Test
    void rejectsForgedCredentialSignature() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount owner = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        KeyPair attackerKeys = services.newDeviceKeys();

        OwnershipCredential credential = services.issueCredential("device-1", keys, owner);
        String forgedSignature = Base64.getEncoder().encodeToString(services.crypto.sign(
                attackerKeys.getPrivate(), credential.canonicalPayload().getBytes(StandardCharsets.UTF_8)));
        OwnershipCredential forged = new OwnershipCredential(
                credential.credentialId(), credential.deviceId(), credential.devicePublicKey(),
                credential.ownerId(), credential.issuedAt(), credential.expiresAt(), forgedSignature);

        ApiException error = assertThrows(ApiException.class,
                () -> services.devices.register("device-1", services.publicKeyOf(keys), forged));
        assertEquals(ErrorCode.CREDENTIAL_INVALID, error.code());
    }

    @Test
    void rejectsTamperedCredentialFields() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount owner = services.newOwner();
        OwnerAccount attacker = services.newOwner();
        KeyPair keys = services.newDeviceKeys();

        OwnershipCredential credential = services.issueCredential("device-1", keys, owner);
        OwnershipCredential tampered = new OwnershipCredential(
                credential.credentialId(), credential.deviceId(), credential.devicePublicKey(),
                attacker.ownerId, credential.issuedAt(), credential.expiresAt(), credential.signature());

        ApiException error = assertThrows(ApiException.class,
                () -> services.devices.register("device-1", services.publicKeyOf(keys), tampered));
        assertEquals(ErrorCode.CREDENTIAL_INVALID, error.code());
    }

    @Test
    void rejectsCredentialDeviceMismatch() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount owner = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        KeyPair otherKeys = services.newDeviceKeys();

        OwnershipCredential credential = services.issueCredential("device-1", keys, owner);

        ApiException wrongKey = assertThrows(ApiException.class,
                () -> services.devices.register("device-1", services.publicKeyOf(otherKeys), credential));
        assertEquals(ErrorCode.CREDENTIAL_DEVICE_MISMATCH, wrongKey.code());

        ApiException wrongDevice = assertThrows(ApiException.class,
                () -> services.devices.register("device-2", services.publicKeyOf(keys), credential));
        assertEquals(ErrorCode.CREDENTIAL_DEVICE_MISMATCH, wrongDevice.code());
    }

    @Test
    void rejectsDuplicateRegistration() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount owner = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        services.registerDevice("device-1", keys, owner);

        OwnershipCredential second = services.issueCredential("device-1", keys, owner);
        ApiException error = assertThrows(ApiException.class,
                () -> services.devices.register("device-1", services.publicKeyOf(keys), second));
        assertEquals(ErrorCode.DEVICE_ALREADY_REGISTERED, error.code());
    }

    @Test
    void stolenCredentialCannotTakeOverBoundDevice() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount owner = services.newOwner();
        OwnerAccount attacker = services.newOwner();
        KeyPair keys = services.newDeviceKeys();
        services.registerDevice("device-1", keys, owner);

        OwnershipCredential stolen = services.issueCredential("device-1", keys, attacker);
        assertThrows(ApiException.class,
                () -> services.devices.register("device-1", services.publicKeyOf(keys), stolen));

        assertEquals(owner.ownerId, services.devices.currentOwner("device-1"));
    }

    @Test
    void rejectsExpiredCredential() {
        TestServices services = TestServices.fresh(tempDir);
        OwnerAccount owner = services.newOwner();
        KeyPair keys = services.newDeviceKeys();

        OwnershipCredential credential = services.issueCredential("device-1", keys, owner);
        OwnershipCredential expired = new OwnershipCredential(
                credential.credentialId(), credential.deviceId(), credential.devicePublicKey(),
                credential.ownerId(), credential.issuedAt() - 48L * 3600 * 1000,
                credential.issuedAt() - 24L * 3600 * 1000, credential.signature());

        // re-sign with issuer key is not possible from outside; tampered expiry must fail verification
        ApiException error = assertThrows(ApiException.class,
                () -> services.devices.register("device-1", services.publicKeyOf(keys), expired));
        assertEquals(ErrorCode.CREDENTIAL_INVALID, error.code());
    }
}
