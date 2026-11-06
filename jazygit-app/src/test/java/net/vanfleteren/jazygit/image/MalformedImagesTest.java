package net.vanfleteren.jazygit.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.control.Either;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * An image made to harm, or broken, is no picture, said soon and without taking much memory; it never throws (ADR 84).
 * The images of a remark are written by whoever wrote it.
 */
class MalformedImagesTest {

    @Test
    void aPngThatSaysItHasMoreDataThanIsEverReadIsNotInflated() {
        // 4096 by 4096 pixels of RGBA of 16 bits would be 128 MB, from a file of a hundred bytes.
        assertEquals(Either.left("too much image data"), Picture.of(png(4096, 4096, 16, 6, new byte[] {0})));
    }

    @Test
    void aPngThatSaysItHasMorePixelsThanAreEverReadIsNotRead() {
        assertEquals(Either.left("too many pixels"), Picture.of(png(65_000, 65_000, 8, 0, new byte[] {0})));
    }

    @Test
    void aPngWithoutTheDataItSaysItHasIsNoPicture() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertTrue(Picture.of(png(4096, 4096, 8, 6, new byte[] {0})).isLeft());
        });
    }

    @Test
    void aPngOfABitDepthItsColourTypeHasNotIsNoPicture() {
        assertTrue(Picture.of(png(4, 4, 3, 2, new byte[16])).getLeft().startsWith("colour type 2 of depth 3"));
        assertTrue(Picture.of(png(4, 4, 16, 3, new byte[16])).isLeft());
        assertTrue(Picture.of(png(4, 4, 0, 0, new byte[16])).isLeft());
    }

    @Test
    void aGifFrameLargerThanItsScreenIsNoPicture() {
        // A screen of 10 by 10 pixels with a frame of 40000 by 40000: 1.6 GB of colour indices.
        ByteBuffer gif = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        gif.put("GIF89a".getBytes(StandardCharsets.US_ASCII));
        gif.putShort((short) 10)
                .putShort((short) 10)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0);
        gif.put((byte) 0x2c).putShort((short) 0).putShort((short) 0);
        gif.putShort((short) 40_000).putShort((short) 40_000).put((byte) 0);
        gif.put((byte) 8).put((byte) 0).put((byte) 0x3b);

        assertEquals(Either.left("a frame larger than its screen"), Picture.of(gif.array()));
    }

    @Test
    void aGifOfAScreenLargerThanIsEverReadIsNotRead() {
        byte[] gif = Arrays.copyOf("GIF89aÿÿÿÿ".getBytes(StandardCharsets.ISO_8859_1), 32);

        assertTrue(Picture.of(gif).isLeft());
    }

    @Test
    void imagesBrokenAtRandomAreAPictureOrNoneButNeverThrowNorHang() throws IOException {
        byte[] png = written(pattern(new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB)), "png");
        byte[] gif = written(pattern(new BufferedImage(40, 30, BufferedImage.TYPE_BYTE_INDEXED)), "gif");
        Random random = new Random(84);

        int[] read = new int[2];

        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            for (int i = 0; i < 20_000; i++) {
                Either<String, Picture> picture = Picture.of(broken(i % 2 == 0 ? png : gif, random));
                picture.forEach(
                        shown -> assertTrue(shown.width() <= Picture.MAX_COLUMNS && shown.rows() <= Picture.MAX_ROWS));
                read[picture.isRight() ? 1 : 0]++;
            }
        });
        // Many are still read, so the broken ones reach far into the readers, not only their first bytes.
        assertTrue(read[1] > 1_000 && read[0] > 1_000, read[0] + " not read, " + read[1] + " read");
    }

    /**
     * {@code bytes} broken at random: some bytes changed, cut off, or a part of it repeated, as a fuzzer would.
     */
    private static byte[] broken(byte[] bytes, Random random) {
        byte[] broken = bytes.clone();
        switch (random.nextInt(3)) {
            case 0 -> {
                for (int n = 1 + random.nextInt(8); n > 0; n--) {
                    broken[random.nextInt(broken.length)] = (byte) random.nextInt(256);
                }
            }
            case 1 -> broken = Arrays.copyOf(broken, random.nextInt(broken.length));
            default -> {
                int from = random.nextInt(broken.length);
                int length = random.nextInt(broken.length - from);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(broken, 0, from + length);
                out.write(broken, from, broken.length - from);
                broken = out.toByteArray();
            }
        }
        return broken;
    }

    /**
     * A PNG of only a header and the image data {@code inflated}, deflated: its checksums are not read.
     */
    private static byte[] png(int width, int height, int depth, int colourType, byte[] inflated) {
        Deflater deflater = new Deflater();
        deflater.setInput(inflated);
        deflater.finish();
        byte[] deflated = new byte[inflated.length + 64];
        int length = deflater.deflate(deflated);
        deflater.end();
        ByteBuffer png = ByteBuffer.allocate(8 + 25 + 12 + length + 12);
        png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        png.putInt(13).put("IHDR".getBytes(StandardCharsets.US_ASCII));
        png.putInt(width)
                .putInt(height)
                .put((byte) depth)
                .put((byte) colourType)
                .put(new byte[3])
                .putInt(0);
        png.putInt(length).put("IDAT".getBytes(StandardCharsets.US_ASCII));
        png.put(deflated, 0, length).putInt(0);
        png.putInt(0).put("IEND".getBytes(StandardCharsets.US_ASCII)).putInt(0);
        return png.array();
    }

    private static BufferedImage pattern(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, (x + y) % 3 == 0 ? 0 : 0xff000000 | x * 0x060504 | y * 0x0a0b0c);
            }
        }
        return image;
    }

    private static byte[] written(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
