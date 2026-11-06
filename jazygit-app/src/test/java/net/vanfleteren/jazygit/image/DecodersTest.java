package net.vanfleteren.jazygit.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.Test;

/**
 * PNGs and GIFs that the JDK writes are read back to the same pixels, without the JDK (ADR 84).
 */
class DecodersTest {

    private static final long MAX = 1 << 24;

    @Test
    void aPngWithAlphaIsReadToItsPixels() throws IOException {
        BufferedImage image = pattern(new BufferedImage(37, 21, BufferedImage.TYPE_INT_ARGB));

        assertSame(image, Png.read(png(image, false), MAX));
    }

    @Test
    void anInterlacedPngIsReadToTheSamePixels() throws IOException {
        BufferedImage image = pattern(new BufferedImage(19, 13, BufferedImage.TYPE_INT_ARGB));

        assertSame(image, Png.read(png(image, true), MAX));
    }

    @Test
    void aPngOfTruecolourIsReadToItsPixels() throws IOException {
        BufferedImage image = pattern(new BufferedImage(23, 9, BufferedImage.TYPE_INT_RGB));

        assertSame(image, Png.read(png(image, false), MAX));
    }

    @Test
    void aPngOfEightOrSixteenBitGreyIsReadToTheShadesItHolds() throws IOException {
        for (int type : new int[] {BufferedImage.TYPE_BYTE_GRAY, BufferedImage.TYPE_USHORT_GRAY}) {
            BufferedImage image = pattern(new BufferedImage(23, 9, type));
            int shift = type == BufferedImage.TYPE_USHORT_GRAY ? 8 : 0;

            Raster raster = Png.read(png(image, false), MAX);

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int shade = image.getRaster().getSample(x, y, 0) >> shift;
                    assertEquals(0xff000000 | shade * 0x10101, raster.argb()[y * raster.width() + x], x + "," + y);
                }
            }
        }
    }

    @Test
    void aPngWithAPaletteOfTwoBitsAndATransparentColourIsReadToItsPixels() throws IOException {
        IndexColorModel palette = new IndexColorModel(
                2,
                4,
                new byte[] {0, (byte) 255, 0, 10},
                new byte[] {0, 0, (byte) 255, 20},
                new byte[] {0, 0, 0, (byte) 200},
                0);
        BufferedImage image = pattern(new BufferedImage(17, 7, BufferedImage.TYPE_BYTE_BINARY, palette));

        assertSame(image, Png.read(png(image, false), MAX));
    }

    @Test
    void aGifIsReadToItsPixelsWithItsTransparentColour() throws IOException {
        IndexColorModel palette = new IndexColorModel(
                8,
                4,
                new byte[] {0, (byte) 255, 0, 10},
                new byte[] {0, 0, (byte) 255, 20},
                new byte[] {0, 0, 0, (byte) 200},
                0);
        BufferedImage image = pattern(new BufferedImage(33, 17, BufferedImage.TYPE_BYTE_INDEXED, palette));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "gif", out);

        assertTrue(Gif.is(out.toByteArray()));
        assertSame(image, Gif.read(out.toByteArray(), MAX));
    }

    @Test
    void aLargeNoisyGifUsesCodesOfTwelveBits() throws IOException {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_BYTE_INDEXED);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 300; x++) {
                int noise = (int) ((x * 7919L + y * 104729L) * 2654435761L >>> 8);
                image.setRGB(x, y, 0xff000000 | noise & 0xffffff);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "gif", out);

        assertSame(image, Gif.read(out.toByteArray(), MAX));
    }

    @Test
    void anImageWithTooManyPixelsOrCutOffIsNotRead() throws IOException {
        byte[] bytes = png(pattern(new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)), false);

        assertThrows(IllegalArgumentException.class, () -> Png.read(bytes, 100));
        assertThrows(RuntimeException.class, () -> Png.read(Arrays.copyOf(bytes, bytes.length / 2), MAX));
    }

    private static BufferedImage pattern(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = (x + y) % 3 == 0 ? 0 : (x * 37 + y * 11) % 2 == 0 ? 255 : 128;
                image.setRGB(x, y, alpha << 24 | (x * 23 % 256) << 16 | (y * 41 % 256) << 8 | (x * y) % 256);
            }
        }
        return image;
    }

    private static byte[] png(BufferedImage image, boolean interlaced) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setProgressiveMode(interlaced ? ImageWriteParam.MODE_DEFAULT : ImageWriteParam.MODE_DISABLED);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        assertTrue(Png.is(out.toByteArray()));
        return out.toByteArray();
    }

    /**
     * The pixels of {@code raster} are those of {@code image}, as ImageIO reads them back from what was written; a
     * transparent pixel is any colour.
     */
    private static void assertSame(BufferedImage image, Raster raster) {
        assertEquals(image.getWidth(), raster.width());
        assertEquals(image.getHeight(), raster.height());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int expected = image.getRGB(x, y);
                int actual = raster.argb()[y * raster.width() + x];
                if (expected >>> 24 == 0) {
                    assertEquals(0, actual >>> 24, "alpha at " + x + "," + y);
                } else {
                    assertEquals(Integer.toHexString(expected), Integer.toHexString(actual), "at " + x + "," + y);
                }
            }
        }
    }
}
