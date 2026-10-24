package no.cantara.julebrus.photo;

import no.cantara.julebrus.web.ApiException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static no.cantara.julebrus.photo.PhotoFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The sanitiser alone: what it refuses, and that what it keeps carries nothing from the input. */
class ImageSanitizerTest {

    private static void refused(byte[] in, int status, String code) {
        assertThatThrownBy(() -> ImageSanitizer.sanitize(in)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.code()).isEqualTo(code);
            assertThat(e.status().value()).isEqualTo(status);
        });
    }

    private static BufferedImage read(byte[] jpeg) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    private static int rgb(BufferedImage img, double fx, double fy) {
        return img.getRGB((int) (fx * (img.getWidth() - 1)), (int) (fy * (img.getHeight() - 1))) & 0xFFFFFF;
    }

    private static boolean near(int rgb, int r, int g, int b) {
        return Math.abs(((rgb >> 16) & 255) - r) < 60 && Math.abs(((rgb >> 8) & 255) - g) < 60 && Math.abs((rgb & 255) - b) < 60;
    }

    // ---- format by magic bytes, not by name or claimed type

    @Test
    void htmlAndPhpNamedJpgAreRefusedByTheirBytes() {
        refused(ascii("<html><script>alert(1)</script></html>"), 415, "PHOTO_FORMAT");
        refused(ascii("<?php system($_GET['c']); ?>"), 415, "PHOTO_FORMAT");
        refused(new byte[0], 415, "PHOTO_FORMAT");
        refused(new byte[]{(byte) 0xFF, (byte) 0xD8}, 415, "PHOTO_FORMAT"); // too short to be a JPEG header
    }

    @Test
    void gifWebpSvgHeicBmpAreRefused() {
        refused("GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000;".getBytes(StandardCharsets.ISO_8859_1), 415, "PHOTO_FORMAT");
        refused("RIFF$\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1), 415, "PHOTO_FORMAT");
        refused(ascii("<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>"), 415, "PHOTO_FORMAT");
        refused(ascii("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>"), 415, "PHOTO_FORMAT");
        refused(new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0}, 415, "PHOTO_FORMAT");
        refused("BM......".getBytes(StandardCharsets.ISO_8859_1), 415, "PHOTO_FORMAT");
    }

    @Test
    void animatedPngIsRefused() {
        byte[] png = png(marked(20, 20));
        // insert an acTL chunk right after IHDR (offset 33)
        byte[] actl = {0, 0, 0, 8, 'a', 'c', 'T', 'L', 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] animated = new byte[png.length + actl.length];
        System.arraycopy(png, 0, animated, 0, 33);
        System.arraycopy(actl, 0, animated, 33, actl.length);
        System.arraycopy(png, 33, animated, 33 + actl.length, png.length - 33);
        refused(animated, 415, "PHOTO_FORMAT");
    }

    // ---- decompression bombs: refused from the header, before any pixel is decoded

    @Test
    void jpegDeclaringThirtyThousandPixelsIsRefusedFromTheHeader() {
        byte[] tiny = jpeg(16, 16);
        assertThat(tiny.length).isLessThan(2000);
        refused(jpegClaiming(tiny, 30000, 30000), 422, "PHOTO_DIMENSIONS");
        refused(jpegClaiming(tiny, 8001, 100), 422, "PHOTO_DIMENSIONS");
        refused(jpegClaiming(tiny, 7000, 7000), 422, "PHOTO_DIMENSIONS"); // 49 MP, each side below the cap
    }

    @Test
    void pngDeclaringThirtyThousandPixelsIsRefusedFromTheHeader() {
        byte[] tiny = png(marked(16, 16));
        refused(pngClaiming(tiny, 30000, 30000), 422, "PHOTO_DIMENSIONS");
        refused(pngClaiming(tiny, 100, 9000), 422, "PHOTO_DIMENSIONS");
        refused(pngClaiming(tiny, 6000, 5000), 422, "PHOTO_DIMENSIONS"); // 30 MP
    }

    @Test
    void aPictureJustInsideTheLimitsIsAcceptedHeaderWise() {
        // 4000 x 3000 = 12 MP, drawn real; sub-sampled while decoding.
        var clean = ImageSanitizer.sanitize(jpeg(4000, 3000));
        assertThat(Math.max(clean.width(), clean.height())).isEqualTo(800);
        assertThat(clean.width()).isEqualTo(800);
        assertThat(clean.height()).isEqualTo(600);
    }

    // ---- corrupt input

    @Test
    void truncatedAndCorruptPicturesAreRefused() {
        byte[] j = jpeg(300, 200);
        refused(truncated(j, 0.5), 422, "PHOTO_UNREADABLE");
        refused(truncated(j, 0.9), 422, "PHOTO_UNREADABLE");
        byte[] p = png(marked(300, 200));
        refused(truncated(p, 0.5), 422, "PHOTO_UNREADABLE");
        byte[] header = java.util.Arrays.copyOf(j, 40);
        refused(header, 422, "PHOTO_UNREADABLE");
        byte[] garbage = new byte[2000];
        new java.util.Random(1).nextBytes(garbage);
        garbage[0] = (byte) 0xFF; garbage[1] = (byte) 0xD8; garbage[2] = (byte) 0xFF;
        refused(garbage, 422, "PHOTO_UNREADABLE");
        byte[] pngGarbage = java.util.Arrays.copyOf(p, p.length);
        for (int i = 40; i < pngGarbage.length - 20; i += 3) pngGarbage[i] ^= 0x5A;
        refused(pngGarbage, 422, "PHOTO_UNREADABLE");
    }

    // ---- what is kept

    @Test
    void exifGpsAndDeviceAreAbsentFromTheStoredBytes() throws Exception {
        byte[] in = withExif(jpeg(400, 300), 1);
        assertThat(contains(in, SECRET_MAKE)).isTrue();
        assertThat(contains(in, SECRET_GPS)).isTrue();
        assertThat(markersBeforeScan(in)).contains(0xE1);

        byte[] out = ImageSanitizer.sanitize(in).jpeg();
        assertThat(contains(out, SECRET_MAKE)).isFalse();
        assertThat(contains(out, SECRET_GPS)).isFalse();
        assertThat(contains(out, "Exif")).isFalse();
        // The only segments the writer produced: JFIF, tables, frame header, scan. No APPn other than JFIF, no COM.
        assertThat(markersBeforeScan(out)).doesNotContain(0xE1, 0xE2, 0xE3, 0xED, 0xEE, 0xFE);
        assertThat(read(out)).isNotNull();
    }

    @Test
    void polyglotWithScriptInCommentAndTrailerIsReencodedClean() throws Exception {
        byte[] in = polyglot(jpeg(200, 100));
        assertThat(contains(in, "<?php")).isTrue();
        byte[] out = ImageSanitizer.sanitize(in).jpeg();
        assertThat(contains(out, "<?php")).isFalse();
        assertThat(contains(out, "<script")).isFalse();
        assertThat(contains(out, "alert")).isFalse();
        // nothing after EOI
        assertThat(out[out.length - 2] & 0xFF).isEqualTo(0xFF);
        assertThat(out[out.length - 1] & 0xFF).isEqualTo(0xD9);
        assertThat(read(out)).isNotNull();
    }

    @Test
    void alphaPngIsFlattenedOntoWhite() throws Exception {
        BufferedImage argb = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB); // fully transparent
        for (int y = 0; y < 100; y++) for (int x = 50; x < 100; x++) argb.setRGB(x, y, 0xFFFF0000); // right half opaque red
        var clean = ImageSanitizer.sanitize(png(argb));
        BufferedImage img = read(clean.jpeg());
        assertThat(near(rgb(img, 0.1, 0.5), 255, 255, 255)).as("transparent area is white, not black").isTrue();
        assertThat(near(rgb(img, 0.9, 0.5), 255, 0, 0)).isTrue();
    }

    @Test
    void largeImagesAreScaledToAtMost800OnTheLongestSideAndSmallOnesAreNotUpscaled() throws Exception {
        var big = ImageSanitizer.sanitize(png(marked(2400, 1200)));
        assertThat(big.width()).isEqualTo(800);
        assertThat(big.height()).isEqualTo(400);
        BufferedImage img = read(big.jpeg());
        assertThat(img.getWidth()).isEqualTo(800);
        var tall = ImageSanitizer.sanitize(jpeg(1000, 3000));
        assertThat(tall.height()).isEqualTo(800);
        assertThat(tall.width()).isBetween(266, 267);
        var small = ImageSanitizer.sanitize(jpeg(120, 80));
        assertThat(small.width()).isEqualTo(120);
        assertThat(small.height()).isEqualTo(80);
    }

    @Test
    void exifOrientationIsAppliedAndThenDiscarded() throws Exception {
        // marked(): left half red, right half blue, green square top-left. Orientation 6 = rotate 90 degrees clockwise:
        // the upright picture is portrait, the red half is on top and the green square top-right.
        var clean = ImageSanitizer.sanitize(withExif(jpeg(400, 200), 6));
        assertThat(clean.width()).isEqualTo(200);
        assertThat(clean.height()).isEqualTo(400);
        BufferedImage img = read(clean.jpeg());
        assertThat(near(rgb(img, 0.5, 0.3), 255, 0, 0)).as("red on top").isTrue();
        assertThat(near(rgb(img, 0.5, 0.8), 0, 0, 255)).as("blue below").isTrue();
        assertThat(near(rgb(img, 0.95, 0.02), 0, 255, 0)).as("green top-right").isTrue();

        // Orientation 3 = upside down: blue on the left now, green bottom-right.
        var rot = ImageSanitizer.sanitize(withExif(jpeg(400, 200), 3));
        BufferedImage r = read(rot.jpeg());
        assertThat(rot.width()).isEqualTo(400);
        assertThat(near(rgb(r, 0.2, 0.5), 0, 0, 255)).isTrue();
        assertThat(near(rgb(r, 0.98, 0.98), 0, 255, 0)).isTrue();
    }

    @Test
    void brokenExifDoesNotBreakTheUpload() {
        byte[] j = withExif(jpeg(100, 100), 6);
        byte[] cut = java.util.Arrays.copyOf(j, 30); // EXIF cut in the middle
        assertThat(ImageSanitizer.exifOrientation(cut)).isEqualTo(1);
        byte[] weird = withExif(jpeg(100, 100), 99);
        assertThat(ImageSanitizer.exifOrientation(weird)).isEqualTo(1);
        assertThat(ImageSanitizer.sanitize(weird).width()).isEqualTo(100);
    }

    @Test
    void noiseStaysUnderTheOutputCap() {
        var clean = ImageSanitizer.sanitize(png(noise(1000, 1000)));
        assertThat(clean.jpeg().length).isLessThanOrEqualTo(ImageSanitizer.MAX_OUTPUT_BYTES);
        assertThat(clean.width()).isEqualTo(800);
    }
}
