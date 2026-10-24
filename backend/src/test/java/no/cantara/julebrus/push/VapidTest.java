package no.cantara.julebrus.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VapidTest {

    @Test
    void theJwtVerifiesWithThePublicKeyAndNamesTheOrigin() throws Exception {
        Vapid.Keys keys = Vapid.generate();
        Instant now = Instant.parse("2026-12-01T17:00:00Z");
        String header = Vapid.authorization("https://fcm.googleapis.com/fcm/send/abc123", "mailto:julebrus@cantara.no", keys, now);

        assertThat(header).startsWith("vapid t=").contains(", k=" + keys.publicKey());
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        String claims = new String(WebPushCrypto.b64(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"")
                .contains("\"sub\":\"mailto:julebrus@cantara.no\"")
                .contains("\"exp\":" + now.plus(Vapid.LIFETIME).getEpochSecond());

        // The JOSE signature is r || s; rebuild DER and verify with the JDK.
        byte[] raw = WebPushCrypto.b64(parts[2]);
        assertThat(raw).hasSize(64);
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(WebPushCrypto.publicKey(WebPushCrypto.b64(keys.publicKey())));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(rawToDer(raw))).isTrue();
    }

    static byte[] rawToDer(byte[] raw) {
        byte[] r = positive(java.util.Arrays.copyOfRange(raw, 0, 32));
        byte[] s = positive(java.util.Arrays.copyOfRange(raw, 32, 64));
        byte[] out = new byte[6 + r.length + s.length];
        out[0] = 0x30;
        out[1] = (byte) (4 + r.length + s.length);
        out[2] = 0x02;
        out[3] = (byte) r.length;
        System.arraycopy(r, 0, out, 4, r.length);
        out[4 + r.length] = 0x02;
        out[5 + r.length] = (byte) s.length;
        System.arraycopy(s, 0, out, 6 + r.length, s.length);
        return out;
    }

    /** DER integers are signed: a leading 1-bit needs a zero byte in front. Leading zeros go. */
    static byte[] positive(byte[] v) {
        int i = 0;
        while (i < v.length - 1 && v[i] == 0) i++;
        byte[] t = java.util.Arrays.copyOfRange(v, i, v.length);
        if ((t[0] & 0x80) != 0) {
            byte[] p = new byte[t.length + 1];
            System.arraycopy(t, 0, p, 1, t.length);
            return p;
        }
        return t;
    }
}
