package no.cantara.julebrus.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/** VAPID (RFC 8292): the server identifies itself to the push service with an ES256 JWT. */
public final class Vapid {

    public record Keys(String publicKey, String privateKey) {
    }

    static final Duration LIFETIME = Duration.ofHours(12);

    private Vapid() {
    }

    /** A fresh pair, base64url raw, to paste into the environment. */
    public static Keys generate() throws GeneralSecurityException {
        KeyPair pair = WebPushCrypto.generateKeyPair();
        return new Keys(WebPushCrypto.b64(WebPushCrypto.uncompressed((ECPublicKey) pair.getPublic())),
                WebPushCrypto.b64(WebPushCrypto.scalar((ECPrivateKey) pair.getPrivate())));
    }

    /** The Authorization header value for one push endpoint: {@code vapid t=<jwt>, k=<public key>}. */
    public static String authorization(String endpoint, String subject, Keys keys, Instant now) throws GeneralSecurityException {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        String header = WebPushCrypto.b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = WebPushCrypto.b64(("{\"aud\":\"" + audience + "\",\"exp\":" + now.plus(LIFETIME).getEpochSecond()
                + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(WebPushCrypto.privateKey(WebPushCrypto.b64(keys.privateKey())));
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        String jwt = signingInput + "." + WebPushCrypto.b64(derToRaw(signer.sign()));
        return "vapid t=" + jwt + ", k=" + keys.publicKey();
    }

    /** JOSE wants r || s as two 32-byte integers; the JDK produces a DER SEQUENCE. */
    static byte[] derToRaw(byte[] der) {
        int at = 2;
        if ((der[1] & 0x80) != 0) at += der[1] & 0x7f;
        at++; // INTEGER tag of r
        int rLen = der[at++];
        byte[] r = Arrays.copyOfRange(der, at, at + rLen);
        at += rLen;
        at++; // INTEGER tag of s
        int sLen = der[at++];
        byte[] s = Arrays.copyOfRange(der, at, at + sLen);
        byte[] out = new byte[64];
        copyRight(r, out, 0);
        copyRight(s, out, 32);
        return out;
    }

    private static void copyRight(byte[] value, byte[] out, int offset) {
        int skip = Math.max(0, value.length - 32);
        int len = value.length - skip;
        System.arraycopy(value, skip, out, offset + 32 - len, len);
    }
}
