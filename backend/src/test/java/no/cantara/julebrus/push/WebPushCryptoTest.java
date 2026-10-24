package no.cantara.julebrus.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;

/** RFC 8291 Appendix A, byte for byte. */
class WebPushCryptoTest {

    @Test
    void matchesTheRfcTestVector() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        byte[] receiverPublic = WebPushCrypto.b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] authSecret = WebPushCrypto.b64("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = WebPushCrypto.b64("DGv6ra1nlYgDCS1FRnbzlw");
        KeyPair sender = WebPushCrypto.keyPair(
                WebPushCrypto.b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"),
                WebPushCrypto.b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));

        byte[] body = WebPushCrypto.encrypt(plaintext, receiverPublic, authSecret, salt, sender);

        assertThat(WebPushCrypto.b64(body)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void freshKeysRoundTripThroughTheRawEncodings() throws Exception {
        KeyPair pair = WebPushCrypto.generateKeyPair();
        byte[] pub = WebPushCrypto.uncompressed((java.security.interfaces.ECPublicKey) pair.getPublic());
        byte[] priv = WebPushCrypto.scalar((java.security.interfaces.ECPrivateKey) pair.getPrivate());
        assertThat(pub).hasSize(65);
        assertThat(priv).hasSize(32);
        KeyPair again = WebPushCrypto.keyPair(pub, priv);
        assertThat(WebPushCrypto.uncompressed((java.security.interfaces.ECPublicKey) again.getPublic())).isEqualTo(pub);
        // A real message for a real receiver decrypts nowhere here, but it must at least build with a random salt.
        byte[] body = WebPushCrypto.encrypt("hei".getBytes(StandardCharsets.UTF_8), pub, new byte[16]);
        assertThat(body).hasSize(16 + 4 + 1 + 65 + 3 + 1 + 16);
    }
}
