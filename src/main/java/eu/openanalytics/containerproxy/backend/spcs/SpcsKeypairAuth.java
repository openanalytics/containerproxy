/*
 * ContainerProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.backend.spcs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

/**
 * Handles keypair authentication for Snowflake SPCS.
 * Generates JWT tokens from RSA private keys and exchanges them for OAuth tokens.
 */
public class SpcsKeypairAuth {
    
    private static final Logger log = LoggerFactory.getLogger(SpcsKeypairAuth.class);
    
    private final String accountIdentifier;
    private final String username;
    private final String accountUrl;
    private final String privateRsaKeyPath;
    private final ObjectMapper jsonMapper;
    
    /**
     * Creates a new SpcsKeypairAuth instance.
     * 
     * @param accountIdentifier The Snowflake account identifier (e.g., "ORG-ACCOUNT" or "ACCOUNT")
     * @param username The Snowflake username
     * @param accountUrl The Snowflake account URL (e.g., "https://account.snowflakecomputing.com")
     * @param privateRsaKeyPath Path to the RSA private key file (PKCS#8 format)
     */
    public SpcsKeypairAuth(String accountIdentifier, String username, String accountUrl, String privateRsaKeyPath) {
        this.accountIdentifier = accountIdentifier;
        this.username = username;
        this.accountUrl = accountUrl;
        this.privateRsaKeyPath = privateRsaKeyPath;
        this.jsonMapper = new ObjectMapper();
    }
    
    /**
     * Generates a JWT token from the RSA private key for keypair authentication.
     * The JWT is signed with RS256 algorithm and includes standard Snowflake claims.
     * 
     * JWT Claims:
     * - iss: {ACCOUNT}.{USERNAME}.{KEY_SHA256}
     * - sub: {ACCOUNT}.{USERNAME}
     * - exp: current time + 30 seconds (short-lived token)
     * 
     * @return The JWT token string
     * @throws RuntimeException If JWT generation fails
     */
    public String generateJwtToken() {
        try {
            // Read RSA private key
            PrivateKey privateKey = loadPrivateKey(privateRsaKeyPath);
            
            // Use stored account identifier (convert hyphens to underscores for JWT claims)
            String account = accountIdentifier.replace("-", "_");
            
            // Calculate SHA256 of public key for issuer claim
            String keySha256 = calculatePublicKeySha256((RSAPrivateKey) privateKey);
            
            // Build issuer: {ACCOUNT}.{USERNAME}.{KEY_SHA256}
            String issuer = account.toUpperCase() + "." + username.toUpperCase() + "." + keySha256;
            
            // Build subject: {ACCOUNT}.{USERNAME}
            String subject = account.toUpperCase() + "." + username.toUpperCase();
            
            // Expiration: current time + 30 seconds
            long now = System.currentTimeMillis() / 1000; // Unix timestamp in seconds
            Date expiration = new Date((now + 30) * 1000);
            
            // Create JWT claims set
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .expirationTime(expiration)
                .build();
            
            // Create JWS header with RS256 algorithm
            JWSHeader header = new JWSHeader(JWSAlgorithm.RS256);
            
            // Create signed JWT
            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            
            // Sign the JWT
            JWSSigner signer = new RSASSASigner(privateKey);
            signedJWT.sign(signer);
            
            // Serialize to compact form
            String jwt = signedJWT.serialize();
            
            log.debug("Generated JWT token for keypair authentication (iss: {}, sub: {})", issuer, subject);
            return jwt;
            
        } catch (Exception e) {
            log.error("Error generating JWT token for keypair authentication", e);
            throw new RuntimeException("Failed to generate JWT token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Exchanges a JWT (key-pair) token for a Snowflake OAuth token.
     * This is required when forwarding HTTPS requests via ingress with key-pair authentication.
     * 
     * Implementation note: This requires JWT encoding with RS256 algorithm and OAuth token exchange.
     * Reference: https://medium.com/@vladimir.timofeenko/hands-on-with-spcs-container-networking-b347866279f9
     * 
     * @param jwtToken The JWT token to exchange
     * @param endpointUrl Optional SPCS endpoint URL for the scope. If null, uses account URL.
     * @return The Snowflake OAuth token
     * @throws IOException If token exchange fails
     */
    public String exchangeJwtForSnowflakeToken(String jwtToken, String endpointUrl) throws IOException {
        try {
            // Build OAuth token endpoint URL
            String oauthTokenUrl = accountUrl + "/oauth/token";
            
            // Build scope - format: session:role:{role} {endpointUrl} or just {endpointUrl}
            // For now, if endpointUrl is provided, use it; otherwise use account URL
            String scope = endpointUrl != null ? endpointUrl : accountUrl;
            
            // Build form-encoded request body
            StringBuilder formData = new StringBuilder();
            formData.append("grant_type=").append(URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8));
            formData.append("&scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8));
            formData.append("&assertion=").append(URLEncoder.encode(jwtToken, StandardCharsets.UTF_8));
            
            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(oauthTokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData.toString(), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();
            
            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("OAuth token exchange interrupted", e);
            }
            int statusCode = response.statusCode();
            String responseBody = response.body();
            
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("OAuth token exchange failed with status " + statusCode + ": " + (responseBody != null ? responseBody : "No error body"));
            }
            if (responseBody == null || responseBody.isEmpty()) {
                throw new IOException("OAuth token exchange returned empty response");
            }
            
            // Format: {"access_token":"...","token_type":"Bearer","expires_in":3600}
            try {
                JsonNode jsonResponse = jsonMapper.readTree(responseBody);
                if (!jsonResponse.has("access_token")) {
                    throw new IOException("OAuth token exchange response does not contain access_token: " + responseBody);
                }
                String oauthToken = jsonResponse.get("access_token").asText();
                log.debug("Successfully exchanged JWT for Snowflake OAuth token (scope: {})", scope);
                return oauthToken;
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IOException("OAuth token exchange response is not valid JSON: " + responseBody, e);
            }
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw e;
            }
            throw new IOException("Error exchanging JWT for Snowflake OAuth token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Loads an RSA private key from a file path.
     * Supports both PKCS#8 (-----BEGIN PRIVATE KEY-----) and PKCS#1 (-----BEGIN RSA PRIVATE KEY-----) formats.
     * 
     * @param keyPath Path to the private key file
     * @return The PrivateKey object
     * @throws Exception If the key cannot be loaded or parsed
     */
    private PrivateKey loadPrivateKey(String keyPath) throws Exception {
        String keyContent = Files.readString(Paths.get(keyPath));
        
        // Remove header, footer, and whitespace
        keyContent = keyContent
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        
        try {
            // Try PKCS#8 format first (most common)
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            // If PKCS#8 fails, try PKCS#1 format
            // PKCS#1 keys need to be converted to PKCS#8 format
            // For simplicity, we'll throw an error suggesting conversion
            throw new IllegalArgumentException("Unable to parse RSA private key. Please ensure the key is in PKCS#8 format (-----BEGIN PRIVATE KEY-----). " +
                "If you have a PKCS#1 key (-----BEGIN RSA PRIVATE KEY-----), convert it using: openssl pkcs8 -topk8 -nocrypt -in key.pem -out key_pkcs8.pem", e);
        }
    }
    
    /**
     * Calculates the SHA256 hash of the RSA public key.
     * This is used in the issuer (iss) claim.
     * 
     * For RSA keys, the public key consists of modulus and public exponent.
     * The standard public exponent is 65537 (0x10001), which is used for most RSA keys.
     * 
     * @param privateKey The RSA private key
     * @return The SHA256 hash as a hexadecimal string (uppercase)
     * @throws Exception If the hash cannot be calculated
     */
    private String calculatePublicKeySha256(RSAPrivateKey privateKey) throws Exception {
        // Use standard RSA public exponent (65537 = 0x10001)
        // This is the most common value for RSA keys
        java.math.BigInteger publicExponent = new java.math.BigInteger("65537");
        
        // Create public key specification using modulus and public exponent
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(privateKey.getModulus(), publicExponent);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
        
        // Get the DER-encoded public key
        byte[] publicKeyBytes = publicKey.getEncoded();
        
        // Calculate SHA256 hash
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKeyBytes);
        
        // Convert to hexadecimal string (uppercase)
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();
    }
}
