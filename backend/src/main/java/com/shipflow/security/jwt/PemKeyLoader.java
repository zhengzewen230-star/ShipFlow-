package com.shipflow.security.jwt;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class PemKeyLoader {

    private static final String PRIVATE_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_END = "-----END PRIVATE KEY-----";
    private static final String PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_END = "-----END PUBLIC KEY-----";

    private final ResourceLoader resourceLoader;

    public PemKeyLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public JwtKeyMaterial load(JwtProperties properties) {
        properties.validate();
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("JWT key loading is disabled");
        }
        return new JwtKeyMaterial(
                loadPrivateKey(readResource(properties.getPrivateKeyLocation(), "private")),
                loadPublicKey(readResource(properties.getPublicKeyLocation(), "public")));
    }

    public RSAPrivateKey loadPrivateKey(String pem) {
        try {
            byte[] der = decodePem(pem, PRIVATE_BEGIN, PRIVATE_END);
            var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateKey rsaKey)) {
                throw new IllegalArgumentException("JWT private key must use RSA");
            }
            ensureKeySize(rsaKey.getModulus().bitLength());
            return rsaKey;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw keyLoadFailure("private", e);
        }
    }

    public RSAPublicKey loadPublicKey(String pem) {
        try {
            byte[] der = decodePem(pem, PUBLIC_BEGIN, PUBLIC_END);
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            if (!(key instanceof RSAPublicKey rsaKey)) {
                throw new IllegalArgumentException("JWT public key must use RSA");
            }
            ensureKeySize(rsaKey.getModulus().bitLength());
            return rsaKey;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw keyLoadFailure("public", e);
        }
    }

    private String readResource(String location, String kind) {
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("JWT " + kind + " key resource is missing or unreadable");
            }
            try (InputStream input = resource.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw keyLoadFailure(kind, e);
        }
    }

    private byte[] decodePem(String pem, String begin, String end) {
        if (pem == null || !pem.contains(begin) || !pem.contains(end)) {
            throw new IllegalArgumentException("PEM boundaries are invalid");
        }
        String body = pem.replace(begin, "").replace(end, "")
                .replaceAll("\\s", "");
        if (body.isBlank()) {
            throw new IllegalArgumentException("PEM body is empty");
        }
        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("PEM body is not valid Base64", e);
        }
    }

    private void ensureKeySize(int bits) {
        if (bits < 2048) {
            throw new IllegalArgumentException("RSA key must be at least 2048 bits");
        }
    }

    private IllegalStateException keyLoadFailure(String kind, Exception cause) {
        return new IllegalStateException("Unable to load JWT " + kind + " RSA key", cause);
    }
}
