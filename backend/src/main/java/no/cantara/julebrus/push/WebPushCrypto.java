package no.cantara.julebrus.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Message encryption for Web Push (RFC 8291, aes128gcm per RFC 8188) on the JDK alone:
 * ECDH on P-256, HKDF with SHA-256, AES-128-GCM. Pure functions; the only state is the
 * random salt and the ephemeral key, both injectable so the RFC's test vector can be run.
 */
public final class WebPushCrypto {

    static final int RECORD_SIZE = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {
    }

    /** The encrypted body for one push message, with a fresh salt and a fresh sender key. */
    public static byte[] encrypt(byte[] plaintext, byte[] receiverPublic, byte[] authSecret) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, receiverPublic, authSecret, salt, generateKeyPair());
    }

    /** RFC 8291 section 3 with every input given, so it can be checked against Appendix A. */
    public static byte[] encrypt(byte[] plaintext, byte[] receiverPublic, byte[] authSecret, byte[] salt, KeyPair sender)
            throws GeneralSecurityException {
        byte[] senderPublic = uncompressed((ECPublicKey) sender.getPublic());
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(sender.getPrivate());
        agreement.doPhase(publicKey(receiverPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), receiverPublic, senderPublic);
        byte[] ikm = hkdfExpand(prkKey, keyInfo, 32);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        // One record, no padding: the plaintext plus the "last record" delimiter.
        byte[] record = concat(plaintext, new byte[]{2});
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = gcm.doFinal(record);

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + senderPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) senderPublic.length).put(senderPublic);
        return concat(header.array(), ciphertext);
    }

    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        return gen.generateKeyPair();
    }

    /** The 65-byte uncompressed point (0x04 || X || Y) that browsers and VAPID use. */
    public static byte[] uncompressed(ECPublicKey key) {
        byte[] x = unsigned(key.getW().getAffineX());
        byte[] y = unsigned(key.getW().getAffineY());
        return concat(new byte[]{4}, x, y);
    }

    public static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) throw new GeneralSecurityException("Not an uncompressed P-256 point");
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256()));
    }

    public static ECPrivateKey privateKey(byte[] scalar) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), p256()));
    }

    /** The 32-byte private scalar. */
    public static byte[] scalar(ECPrivateKey key) {
        return unsigned(key.getS());
    }

    public static KeyPair keyPair(byte[] publicUncompressed, byte[] privateScalar) throws GeneralSecurityException {
        PublicKey pub = publicKey(publicUncompressed);
        PrivateKey priv = privateKey(privateScalar);
        return new KeyPair(pub, priv);
    }

    static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        return params.getParameterSpec(ECParameterSpec.class);
    }

    /** HKDF-Extract: HMAC-SHA256 keyed with the salt over the input keying material. */
    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    /** HKDF-Expand for lengths up to one block (32 bytes), which is all Web Push needs. */
    static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        byte[] block = hmac(prk, concat(info, new byte[]{1}));
        return Arrays.copyOf(block, length);
    }

    /** A positive big integer as exactly 32 big-endian bytes. */
    static byte[] unsigned(BigInteger n) {
        byte[] raw = n.toByteArray();
        byte[] out = new byte[32];
        int skip = Math.max(0, raw.length - 32);
        System.arraycopy(raw, skip, out, 32 - (raw.length - skip), raw.length - skip);
        return out;
    }

    static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    public static byte[] b64(String urlSafe) {
        return Base64.getUrlDecoder().decode(urlSafe.trim());
    }

    public static String b64(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
