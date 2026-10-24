package no.cantara.julebrus.photo;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;

/** Builds hostile and friendly pictures for the photo tests. Everything is generated, nothing is checked in. */
public final class PhotoFixtures {

    public static final String SECRET_MAKE = "SecretPhoneMaker9000";
    public static final String SECRET_GPS = "GPS-59.9139N-10.7522E";

    private PhotoFixtures() {
    }

    /** Left half red, right half blue, with a green square in the top-left corner: orientation is visible in the pixels. */
    public static BufferedImage marked(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, h);
        g.setColor(Color.BLUE);
        g.fillRect(w / 2, 0, w - w / 2, h);
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, Math.max(2, w / 8), Math.max(2, h / 8));
        g.dispose();
        return img;
    }

    public static byte[] jpeg(BufferedImage img) {
        try {
            ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam p = w.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.9f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream os = new MemoryCacheImageOutputStream(bos)) {
                w.setOutput(os);
                w.write(null, new IIOImage(img, null, null), p);
            }
            w.dispose();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] jpeg(int w, int h) {
        return jpeg(marked(w, h));
    }

    public static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Random noise: the worst case for compression. */
    public static BufferedImage noise(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(42);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, r.nextInt(0x1000000));
        return img;
    }

    /** Inserts an APP1/Exif segment right after SOI: orientation, a maker string and a GPS block with readable text. */
    public static byte[] withExif(byte[] jpeg, int orientation) {
        byte[] maker = (SECRET_MAKE + "\0").getBytes(StandardCharsets.US_ASCII);
        byte[] gps = (SECRET_GPS + "\0").getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream t = new ByteArrayOutputStream();
        // TIFF header, little endian, IFD0 at 8
        t.writeBytes(new byte[]{'I', 'I', 42, 0, 8, 0, 0, 0});
        int entries = 3;
        int ifdSize = 2 + entries * 12 + 4;
        int makerOff = 8 + ifdSize;
        int gpsIfdOff = makerOff + maker.length;
        int gpsIfdSize = 2 + 12 + 4;
        int gpsDataOff = gpsIfdOff + gpsIfdSize;
        t.writeBytes(le16(entries));
        t.writeBytes(entry(0x010F, 2, maker.length, makerOff));
        t.writeBytes(entry(0x0112, 3, 1, orientation));
        t.writeBytes(entry(0x8825, 4, 1, gpsIfdOff));
        t.writeBytes(le32(0));
        t.writeBytes(maker);
        t.writeBytes(le16(1));
        t.writeBytes(entry(0x0001, 2, gps.length, gpsDataOff)); // GPSLatitudeRef, text out of line
        t.writeBytes(le32(0));
        t.writeBytes(gps);
        byte[] tiff = t.toByteArray();
        ByteArrayOutputStream seg = new ByteArrayOutputStream();
        seg.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xE1});
        int len = 2 + 6 + tiff.length;
        seg.write(len >> 8);
        seg.write(len & 0xFF);
        seg.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        seg.writeBytes(tiff);
        return insertAfterSoi(jpeg, seg.toByteArray());
    }

    /** A COM segment and trailing bytes after EOI, both carrying script text. */
    public static byte[] polyglot(byte[] jpeg) {
        byte[] com = "<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream seg = new ByteArrayOutputStream();
        seg.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xFE});
        int len = 2 + com.length;
        seg.write(len >> 8);
        seg.write(len & 0xFF);
        seg.writeBytes(com);
        byte[] with = insertAfterSoi(jpeg, seg.toByteArray());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(with);
        out.writeBytes("\n<?php system($_GET['c']); ?>\n<script>alert(2)</script>".getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private static byte[] insertAfterSoi(byte[] jpeg, byte[] segment) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg[0]);
        out.write(jpeg[1]);
        out.writeBytes(segment);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] entry(int tag, int type, int count, int value) {
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        e.writeBytes(le16(tag));
        e.writeBytes(le16(type));
        e.writeBytes(le32(count));
        e.writeBytes(le32(value));
        return e.toByteArray();
    }

    private static byte[] le16(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8)};
    }

    private static byte[] le32(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    /** Rewrites the dimensions in the first SOF marker: a tiny file that claims to be huge. */
    public static byte[] jpegClaiming(byte[] jpeg, int w, int h) {
        byte[] b = jpeg.clone();
        for (int i = 2; i + 9 < b.length; i++) {
            if ((b[i] & 0xFF) == 0xFF && ((b[i + 1] & 0xFF) == 0xC0 || (b[i + 1] & 0xFF) == 0xC2)) {
                b[i + 5] = (byte) (h >> 8);
                b[i + 6] = (byte) h;
                b[i + 7] = (byte) (w >> 8);
                b[i + 8] = (byte) w;
                return b;
            }
        }
        throw new IllegalStateException("no SOF");
    }

    /** Rewrites IHDR width and height and its CRC. */
    public static byte[] pngClaiming(byte[] png, int w, int h) {
        byte[] b = png.clone();
        // signature 8, length 4, type 4 => data starts at 16
        putInt(b, 16, w);
        putInt(b, 20, h);
        CRC32 crc = new CRC32();
        crc.update(b, 12, 17);
        putInt(b, 29, (int) crc.getValue());
        return b;
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v >> 24);
        b[off + 1] = (byte) (v >> 16);
        b[off + 2] = (byte) (v >> 8);
        b[off + 3] = (byte) v;
    }

    public static byte[] truncated(byte[] b, double fraction) {
        return java.util.Arrays.copyOf(b, (int) (b.length * fraction));
    }

    public static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** JPEG segment markers present in the file, up to the start of scan. */
    public static java.util.List<Integer> markersBeforeScan(byte[] b) {
        var out = new java.util.ArrayList<Integer>();
        int pos = 2;
        while (pos + 4 <= b.length && (b[pos] & 0xFF) == 0xFF) {
            int m = b[pos + 1] & 0xFF;
            out.add(m);
            if (m == 0xDA) break;
            pos += 2 + (((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF));
        }
        return out;
    }

    public static boolean contains(byte[] hay, String needle) {
        byte[] n = needle.getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i + n.length <= hay.length; i++) {
            for (int j = 0; j < n.length; j++) if (hay[i + j] != n[j]) continue outer;
            return true;
        }
        return false;
    }
}
