package no.cantara.julebrus.photo;

import no.cantara.julebrus.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Turns an untrusted upload into a small, clean JPEG, or refuses it. Order matters:
 * <ol>
 *   <li>the magic bytes decide the format (JPEG or PNG; the client's content type and file name are never looked at);</li>
 *   <li>the image header gives the dimensions, and a decompression bomb is refused before a single pixel is decoded;</li>
 *   <li>the image is decoded (sub-sampled while reading, so a big photo never exists in memory at full size),
 *       rotated by its EXIF orientation, flattened onto white and scaled to at most {@value #MAX_OUTPUT_SIDE} px;</li>
 *   <li>it is written again as a fresh JPEG. Nothing of the original file survives: no EXIF (GPS, device), no
 *       ICC profile, no comments, no trailing bytes. Colours that relied on a wide-gamut ICC profile are shown as sRGB.</li>
 * </ol>
 * Pure: no Spring, no database.
 */
public final class ImageSanitizer {

    private static final Logger log = LoggerFactory.getLogger(ImageSanitizer.class);

    /** Longest side the header may declare. */
    public static final int MAX_SOURCE_SIDE = 8000;
    /** Pixels the header may declare (width times height). */
    public static final long MAX_SOURCE_PIXELS = 24_000_000L;
    public static final int MAX_OUTPUT_SIDE = 800;
    private static final float[] QUALITIES = {0.85f, 0.7f, 0.55f};
    /** Re-encoded size above which a photo is refused (noise at 800 px is the worst case, well below this). */
    public static final int MAX_OUTPUT_BYTES = 900_000;

    /** The cleaned image and its size. */
    public record Clean(byte[] jpeg, int width, int height) {
    }

    private ImageSanitizer() {
    }

    public static Clean sanitize(byte[] in) {
        Format format = sniff(in);
        if (format == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PHOTO_FORMAT", "Only JPEG and PNG pictures are accepted");
        }
        if (format == Format.PNG && isAnimatedPng(in)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PHOTO_FORMAT", "Animated pictures are not accepted");
        }
        ImageReader reader = readerFor(format);
        try (ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(in))) {
            reader.setInput(iis, true, true);
            int w = reader.getWidth(0);
            int h = reader.getHeight(0);
            if (w < 1 || h < 1) throw unreadable();
            if (w > MAX_SOURCE_SIDE || h > MAX_SOURCE_SIDE || (long) w * h > MAX_SOURCE_PIXELS) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_DIMENSIONS",
                        "The picture is too large (at most " + MAX_SOURCE_SIDE + " px per side and 24 megapixels)");
            }
            int orientation = format == Format.JPEG ? exifOrientation(in) : 1;

            // Decode only every n-th pixel when the picture is much larger than the output.
            int sub = Math.max(1, Math.max(w, h) / MAX_OUTPUT_SIDE);
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(sub, sub, 0, 0);
            AtomicBoolean warned = new AtomicBoolean();
            reader.addIIOReadWarningListener((source, warning) -> warned.set(true));
            BufferedImage src = reader.read(0, param);
            // A truncated JPEG decodes with a warning and a grey bottom; do not keep that.
            if (warned.get() || src == null) throw unreadable();
            return encode(render(src, orientation));
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.info("Rejected unreadable picture: {}", e.toString());
            throw unreadable();
        } catch (OutOfMemoryError e) {
            log.warn("Picture decoding ran out of memory, refused");
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_DIMENSIONS", "The picture is too large");
        } finally {
            reader.dispose();
        }
    }

    private static ApiException unreadable() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_UNREADABLE", "The file is not a readable picture");
    }

    enum Format { JPEG, PNG }

    static Format sniff(byte[] b) {
        if (b == null) return null;
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return Format.JPEG;
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (b.length >= 8) {
            for (int i = 0; i < 8; i++) if (b[i] != png[i]) return null;
            return Format.PNG;
        }
        return null;
    }

    private static ImageReader readerFor(Format format) {
        Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName(format == Format.JPEG ? "jpeg" : "png");
        if (!it.hasNext()) throw unreadable();
        return it.next();
    }

    /** An APNG carries an acTL chunk before its first IDAT. */
    static boolean isAnimatedPng(byte[] b) {
        int pos = 8;
        while (pos + 12 <= b.length) {
            long len = ((b[pos] & 0xFFL) << 24) | ((b[pos + 1] & 0xFFL) << 16) | ((b[pos + 2] & 0xFFL) << 8) | (b[pos + 3] & 0xFFL);
            String type = new String(b, pos + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (type.equals("acTL")) return true;
            if (type.equals("IDAT") || type.equals("IEND")) return false;
            long next = pos + 12L + len;
            if (next > b.length) return false;
            pos = (int) next;
        }
        return false;
    }

    /** EXIF orientation 1..8 from the APP1 segment, 1 when absent or anything is out of bounds. */
    static int exifOrientation(byte[] b) {
        try {
            int pos = 2;
            while (pos + 4 <= b.length && (b[pos] & 0xFF) == 0xFF) {
                int marker = b[pos + 1] & 0xFF;
                if (marker == 0xFF) { pos++; continue; }
                if (marker == 0xD9 || marker == 0xDA) return 1; // end of image / start of scan
                if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { pos += 2; continue; }
                int len = u16(b, pos + 2, false);
                if (len < 2) return 1;
                if (marker == 0xE1 && len >= 16 && pos + 4 + 6 <= b.length
                        && b[pos + 4] == 'E' && b[pos + 5] == 'x' && b[pos + 6] == 'i' && b[pos + 7] == 'f'
                        && b[pos + 8] == 0 && b[pos + 9] == 0) {
                    int tiff = pos + 10;
                    int end = Math.min(b.length, pos + 2 + len);
                    boolean le = b[tiff] == 'I' && b[tiff + 1] == 'I';
                    if (!le && !(b[tiff] == 'M' && b[tiff + 1] == 'M')) return 1;
                    if (u16(b, tiff + 2, le) != 42) return 1;
                    long ifd = tiff + u32(b, tiff + 4, le);
                    if (ifd < tiff || ifd + 2 > end) return 1;
                    int count = u16(b, (int) ifd, le);
                    for (int i = 0; i < Math.min(count, 256); i++) {
                        int entry = (int) ifd + 2 + 12 * i;
                        if (entry + 12 > end) return 1;
                        if (u16(b, entry, le) == 0x0112) {
                            int v = u16(b, entry + 8, le);
                            return v >= 1 && v <= 8 ? v : 1;
                        }
                    }
                    return 1;
                }
                pos += 2 + len;
            }
        } catch (RuntimeException e) {
            return 1;
        }
        return 1;
    }

    private static int u16(byte[] b, int off, boolean le) {
        if (off < 0 || off + 2 > b.length) throw new IndexOutOfBoundsException();
        return le ? (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) : ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    private static long u32(byte[] b, int off, boolean le) {
        if (off < 0 || off + 4 > b.length) throw new IndexOutOfBoundsException();
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int x = b[off + (le ? 3 - i : i)] & 0xFF;
            v = (v << 8) | x;
        }
        return v;
    }

    /** Orientation as an affine map from source pixels to the upright picture. */
    static AffineTransform orient(int orientation, int w, int h) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
            default -> new AffineTransform();
        };
    }

    private static BufferedImage render(BufferedImage src, int orientation) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        boolean swap = orientation >= 5;
        int ow = swap ? sh : sw;
        int oh = swap ? sw : sh;
        double scale = Math.min(1.0, (double) MAX_OUTPUT_SIDE / Math.max(ow, oh));
        int tw = Math.max(1, (int) Math.round(ow * scale));
        int th = Math.max(1, (int) Math.round(oh * scale));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE); // transparency is flattened onto white
            g.fillRect(0, 0, tw, th);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            AffineTransform at = AffineTransform.getScaleInstance(tw / (double) ow, th / (double) oh);
            at.concatenate(orient(orientation, sw, sh));
            g.drawImage(src, at, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static Clean encode(BufferedImage img) throws IOException {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        if (!it.hasNext()) throw new IOException("no JPEG writer");
        ImageWriter writer = it.next();
        try {
            for (float q : QUALITIES) {
                ImageWriteParam p = writer.getDefaultWriteParam();
                p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                p.setCompressionQuality(q);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (MemoryCacheImageOutputStream os = new MemoryCacheImageOutputStream(bos)) {
                    writer.setOutput(os);
                    // null metadata: the writer emits its default JFIF header and nothing from the source
                    writer.write(null, new IIOImage(img, null, null), p);
                }
                if (bos.size() <= MAX_OUTPUT_BYTES) return new Clean(bos.toByteArray(), img.getWidth(), img.getHeight());
            }
        } finally {
            writer.dispose();
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_UNREADABLE", "The picture cannot be compressed small enough");
    }
}
