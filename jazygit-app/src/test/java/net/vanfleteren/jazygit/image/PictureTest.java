package net.vanfleteren.jazygit.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.Vector;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * An image as large as it shows in a browser, a pixel of the picture for each 8 of it, within its bounds (ADR 84).
 */
class PictureTest {

    @Test
    void anImageIsAPixelForEachEightOfItRoundedUp() throws IOException {
        Picture picture = Picture.of(png(14, 14, 0xffe07828)).get();

        assertEquals(2, picture.width());
        assertEquals(2, picture.height());
        assertEquals(1, picture.rows());
        assertEquals(Vector.fill(4, 0xffe07828), picture.argb());
    }

    @Test
    void aLargeImageIsShrunkToFitKeepingItsShape() throws IOException {
        Picture picture = Picture.of(png(1600, 900, 0xff112233)).get();

        // 200 by 113 pixels, as high as it may be.
        assertEquals(85, picture.width());
        assertEquals(Picture.MAX_ROWS * 2, picture.height());
        Picture wide = Picture.of(png(4000, 100, 0xff112233)).get();
        assertEquals(Picture.MAX_COLUMNS, wide.width());
        assertEquals(3, wide.height());
    }

    @Test
    void pixelsAreAveragedByHowOpaqueTheyAre() {
        Picture picture = new Picture(2, 1, Vector.of(0xffff0000, 0x000000ff));

        assertEquals(new Picture(1, 1, Vector.of(0x7fff0000)), picture.fit(1, 1, 1, 1));
    }

    @Test
    void fittedItIsNoLargerThanItIsNorThanTheRoomItHas() {
        Picture picture = new Picture(4, 4, Vector.fill(16, 0xff000000));

        assertEquals(4, picture.fit(10, 10, 80, 20).width());
        assertEquals(2, picture.fit(4, 4, 2, 20).width());
        assertEquals(2, picture.fit(4, 4, 2, 20).height());
    }

    @Test
    void whatIsNoPngOrGifOrCannotBeReadIsNoPicture() throws IOException {
        assertTrue(Picture.of(new byte[0]).isLeft());
        assertTrue(
                Picture.of(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}).isLeft());
        assertTrue(Picture.of(Arrays.copyOf(png(4, 4, 0xff000000), 20)).isLeft());
    }

    @Test
    void anImageIsKeptSharpAtAsManyPixelsAsItsCellsHaveAtMost() throws IOException {
        Picture small = Picture.of(png(14, 14, 0xffe07828)).get();
        Picture large = Picture.of(png(1600, 1200, 0xff112233)).get();

        assertEquals(14, small.sharp().get().width());
        assertEquals(14, small.sharp().get().height());
        // No more than 24 rows of 16 pixels high, keeping its shape.
        assertEquals(512, large.sharp().get().width());
        assertEquals(384, large.sharp().get().height());
        assertEquals(large.sharp(), large.fit(10, 10, 10, 10).sharp());
    }

    private static byte[] png(int width, int height, int argb) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
