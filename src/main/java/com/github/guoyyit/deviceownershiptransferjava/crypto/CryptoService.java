package com.github.guoyyit.deviceownershiptransferjava.crypto;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

@Service
public class CryptoService {

    private static final String ALGORITHM = "Ed25519";
    private static final Base64.Encoder B64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_URL_DEC = Base64.getUrlDecoder();

    public Keys generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            java.security.KeyPair pair = generator.generateKeyPair();
            String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
            String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
            return new Keys(publicKey, privateKey);
        } catch (Exception e) {
            throw new IllegalStateException("failed to generate Ed25519 key pair", e);
        }
    }

    public String sign(String privateKeyB64, String payload) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyB64);
            PrivateKey privateKey = KeyFactory.getInstance(ALGORITHM)
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(keyBytes));
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to sign payload", e);
        }
    }

    public boolean verify(String publicKeyB64, String payload, String signatureB64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyB64);
            PublicKey publicKey = KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(keyBytes));
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureB64));
        } catch (Exception e) {
            return false;
        }
    }

    public String encodeToken(String payload, String signature) {
        return B64_URL.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "."
                + B64_URL.encodeToString(signature.getBytes(StandardCharsets.UTF_8));
    }

    public String[] decodeToken(String token) {
        int dot = token.indexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException("malformed token");
        }
        String payload = new String(B64_URL_DEC.decode(token.substring(0, dot)), StandardCharsets.UTF_8);
        String signature = new String(B64_URL_DEC.decode(token.substring(dot + 1)), StandardCharsets.UTF_8);
        return new String[]{payload, signature};
    }
}
