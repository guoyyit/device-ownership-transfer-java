package com.github.guoyyit.deviceownershiptransferjava.crypto;

import com.github.guoyyit.deviceownershiptransferjava.web.ApiException;
import com.github.guoyyit.deviceownershiptransferjava.web.ErrorCode;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class CryptoService {

    private static final String KEY_ALGORITHM = "Ed25519";
    private static final String SIGNATURE_ALGORITHM = "Ed25519";

    private final SecureRandom random = new SecureRandom();

    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("failed to generate key pair", e);
        }
    }

    public byte[] sign(PrivateKey privateKey, byte[] data) {
        try {
            Signature signer = Signature.getInstance(SIGNATURE_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(data);
            return signer.sign();
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign", e);
        }
    }

    public boolean verify(PublicKey publicKey, byte[] data, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public String encodePublicKey(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public PublicKey decodePublicKey(String base64) {
        try {
            byte[] encoded = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "malformed public key");
        }
    }

    public String encodePrivateKey(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public PrivateKey decodePrivateKey(String base64) {
        try {
            byte[] encoded = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new IllegalStateException("malformed private key", e);
        }
    }

    public String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
