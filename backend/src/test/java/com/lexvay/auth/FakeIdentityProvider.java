package com.lexvay.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An OpenID Connect provider for tests: a loopback HTTP server that serves discovery metadata, a signing key
 * set and, at its token endpoint, whatever ID token the test has queued. No network beyond the loopback
 * interface.
 */
final class FakeIdentityProvider implements AutoCloseable {

    static final String CLIENT_ID = "lexvay-test-client";

    private final HttpServer server;
    private final RSAKey key;
    private final AtomicReference<String> nextIdToken = new AtomicReference<>();

    FakeIdentityProvider() {
        try {
            key = new RSAKeyGenerator(2048).keyID("test-key").generate();
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (JOSEException | IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/.well-known/openid-configuration", exchange -> respond(exchange, "{\"issuer\":\"" + baseUrl()
                + "\",\"authorization_endpoint\":\"" + baseUrl() + "/authorize\",\"token_endpoint\":\"" + baseUrl()
                + "/token\",\"jwks_uri\":\"" + baseUrl() + "/jwks\",\"response_types_supported\":[\"code\"],"
                + "\"subject_types_supported\":[\"public\"],\"id_token_signing_alg_values_supported\":[\"RS256\"]}"));
        server.createContext("/jwks", exchange -> respond(exchange, new JWKSet(key.toPublicJWK()).toString()));
        server.createContext("/token", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String idToken = nextIdToken.get();
            respond(exchange, "{\"access_token\":\"access-token\",\"token_type\":\"Bearer\",\"expires_in\":300,"
                    + "\"id_token\":\"" + idToken + "\"}");
        });
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /**
     * Queues the ID token the token endpoint returns next. {@code claims} are added to the standard ones
     * (issuer, subject, audience, expiry, the nonce the browser's authorization request carried).
     */
    void issueIdToken(String nonce, Map<String, Object> claims) {
        issueIdToken(baseUrl(), nonce, claims);
    }

    void issueIdToken(String issuer, String nonce, Map<String, Object> claims) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer).subject("subject-" + claims.getOrDefault("email", "anonymous"))
                .audience(List.of(CLIENT_ID)).issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("nonce", nonce);
        claims.forEach(builder::claim);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), builder.build());
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new UncheckedIOException(new IOException(e));
        }
        nextIdToken.set(jwt.serialize());
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
