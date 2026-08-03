package com.shipflow.security.jwt;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public record JwtKeyMaterial(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
}
