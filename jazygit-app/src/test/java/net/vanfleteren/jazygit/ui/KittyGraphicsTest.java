package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;
import net.vanfleteren.jazygit.image.Picture;
import net.vanfleteren.jazygit.image.Sharp;
import org.junit.jupiter.api.Test;

/**
 * Pictures drawn by the terminal with the Kitty graphics protocol and its Unicode placeholders (ADR 86).
 */
class KittyGraphicsTest {

    private static final Rect AREA = new Rect(0, 0, 10, 4);
    private static final String PLACEHOLDER = "􎻮";

    @Test
    void aCellIsThePlaceholderWithItsRowColumnAndTheHighByteOfItsIdInTheColourOfTheRest() {
        Picture picture = picture(3, 4);
        int id = KittyGraphics.id(picture.sharp().get(), 3, 2);

        Tuple2<Style, String> first = KittyGraphics.cell(picture, 0);
        Tuple2<Style, String> last = KittyGraphics.cell(picture, 5);

        assertEquals(KittyGraphics.placeholder(0, 0, id), first._2());
        assertTrue(first._2().startsWith(PLACEHOLDER + "̅̅"));
        assertTrue(last._2().startsWith(PLACEHOLDER + "̍̎"));
        assertEquals(
                Color.rgb(id >> 16 & 0xff, id >> 8 & 0xff, id & 0xff),
                first._1().fg().orElseThrow());
        assertEquals(first._1(), last._1());
        assertEquals(" ", KittyGraphics.cell(picture, 6)._2());
    }

    @Test
    void theIdOfAnImageSaysHowManyCellsItTakesAndIsNeverNone() {
        Sharp sharp = picture(2, 2).sharp().get();

        int id = KittyGraphics.id(sharp, 100, 24);

        assertNotEquals(0, KittyGraphics.id(sharp, 1, 1));
        assertTrue(KittyGraphics.transmission(id, sharp).contains(",c=100,r=24,"));
        assertTrue(
                KittyGraphics.transmission(KittyGraphics.id(sharp, 1, 1), sharp).contains(",c=1,r=1,"));
        assertNotEquals(id, KittyGraphics.id(sharp, 99, 24));
        assertNotEquals(id, KittyGraphics.id(picture(3, 2).sharp().get(), 100, 24));
    }

    @Test
    void onlyAPictureReadFromAnImageAndAsLargeAsItsIdCanTellIsDrawn() {
        Option<Sharp> sharp = picture(1, 1).sharp();
        assertTrue(KittyGraphics.draws(picture(3, 4)));
        assertFalse(KittyGraphics.draws(new Picture(3, 4, Vector.fill(12, 0xff000000))));
        assertTrue(KittyGraphics.draws(new Picture(Picture.MAX_COLUMNS, 64, Vector.empty(), sharp)));
        assertFalse(KittyGraphics.draws(new Picture(101, 2, Vector.empty(), sharp)));
        assertFalse(KittyGraphics.draws(new Picture(2, 66, Vector.empty(), sharp)));
    }

    @Test
    void aRunOfPlaceholdersIsACellEach() {
        Picture picture = picture(3, 2);
        Style style = KittyGraphics.cell(picture, 0)._1();
        String run =
                List.range(0, 3).map(n -> KittyGraphics.cell(picture, n)._2()).mkString();
        Buffer buffer = Buffer.empty(AREA);

        buffer.setLine(0, 0, Line.from(Span.styled(run, style)));

        assertEquals(List.range(0, 3).map(n -> KittyGraphics.cell(picture, n)._2()), cells(buffer, 0, 3));
        assertTrue(KittyGraphics.isPlaceholder(buffer.get(2, 0)));
        assertFalse(KittyGraphics.isPlaceholder(buffer.get(3, 0)));
    }

    @Test
    void anImageIsSentOnceWhenACellOfItIsOnTheScreenAndAgainOnceForgotten() {
        Picture picture = picture(2, 2);
        Sharp sharp = picture.sharp().get();
        Buffer buffer = Buffer.empty(AREA);
        Tuple2<Style, String> cell = KittyGraphics.cell(picture, 1);
        buffer.setString(4, 2, cell._2(), cell._1());
        KittyGraphics graphics = new KittyGraphics();
        java.util.List<String> sent = new ArrayList<>();
        List<Sharp> shown = List.of(picture(5, 1).sharp().get(), sharp);

        graphics.sendFor(buffer, shown, sent::add);
        graphics.sendFor(buffer, shown, sent::add);

        assertEquals(java.util.List.of(KittyGraphics.transmission(KittyGraphics.id(sharp, 2, 1), sharp)), sent);
        graphics.forget();
        graphics.sendFor(buffer, shown, sent::add);
        assertEquals(2, sent.size());
        graphics.sendFor(Buffer.empty(AREA), shown, sent::add);
        assertEquals(2, sent.size());
    }

    @Test
    void aCellOfAnImageNotShownIsNotSent() {
        Picture picture = picture(2, 2);
        Buffer buffer = Buffer.empty(AREA);
        Tuple2<Style, String> cell = KittyGraphics.cell(picture, 0);
        buffer.setString(0, 0, cell._2(), cell._1());
        java.util.List<String> sent = new ArrayList<>();

        new KittyGraphics().sendFor(buffer, List.of(picture(5, 1).sharp().get()), sent::add);

        assertTrue(sent.isEmpty());
    }

    @Test
    void anImageIsSentInChunksAsAVirtualPlacementWithoutAnAnswer() {
        Sharp sharp = new Sharp(30, 20, "A".repeat(4096 * 2 + 100));
        int id = 7 << 12 | 6 << 5 | 2;

        List<String> chunks = List.of(KittyGraphics.transmission(id, sharp).split("\u001b\\\\"));

        assertEquals(3, chunks.size());
        assertEquals(
                "\u001b_Ga=T,U=1,q=2,f=32,o=z,i=" + id + ",s=30,v=20,c=7,r=3,m=1;" + "A".repeat(4096), chunks.get(0));
        assertEquals("\u001b_Gq=2,m=1;" + "A".repeat(4096), chunks.get(1));
        assertEquals("\u001b_Gq=2,m=0;" + "A".repeat(100), chunks.get(2));
    }

    @Test
    void anIdUsesOnlyTheDiacriticsTamboUiKeepsWithThePlaceholder() {
        List<Integer> ids = List.range(1, 400)
                .map(n -> KittyGraphics.id(new Sharp(1, 1, "image " + n), Picture.MAX_COLUMNS, Picture.MAX_ROWS));

        assertTrue(ids.forAll(id -> id >>> 24 < KittyGraphics.USABLE));
        assertTrue(ids.exists(id -> id >>> 24 > 50));
        assertTrue(ids.forAll(id -> id != 0));
    }

    @Test
    void everyCellOfTheLargestPictureIsACellOfItsOwnInTamboUi() {
        Option<Sharp> sharp = picture(1, 1).sharp();
        Picture picture = new Picture(
                Picture.MAX_COLUMNS,
                Picture.MAX_ROWS * 2,
                Vector.fill(Picture.MAX_COLUMNS * Picture.MAX_ROWS * 2, 0xff000000),
                sharp);
        String last = KittyGraphics.cell(picture, Picture.MAX_COLUMNS * Picture.MAX_ROWS - 1)
                ._2();
        Buffer buffer = Buffer.empty(AREA);

        buffer.setString(0, 0, last + last, Style.EMPTY);

        assertEquals(last, buffer.get(0, 0).symbol());
        assertEquals(last, buffer.get(1, 0).symbol());
    }

    @Test
    void aSharpImageIsItsPixelsAsRgbaCompressedAsLargeAsTheImage() throws IOException {
        Sharp sharp = picture(2, 1).sharp().get();

        byte[] rgba = inflated(sharp.rgba());

        assertEquals(16, sharp.width());
        assertEquals(8, sharp.height());
        assertEquals(16 * 8 * 4, rgba.length);
        assertArrayEquals(new byte[] {0x11, 0x22, 0x33, (byte) 0xff}, Arrays.copyOf(rgba, 4));
    }

    /**
     * A picture as one read from an image {@code width} by {@code height} pixels of a picture large, each
     * {@link Picture#SCALE} pixels of {@code 0xff112233}.
     */
    static Picture picture(int width, int height) {
        return Picture.of(png(width * Picture.SCALE, height * Picture.SCALE)).get();
    }

    private static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xff112233);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static List<String> cells(Buffer buffer, int y, int width) {
        return List.range(0, width).map(x -> buffer.get(x, y).symbol());
    }

    private static byte[] inflated(String base64) throws IOException {
        try (InflaterInputStream in = new InflaterInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(base64)))) {
            return in.readAllBytes();
        }
    }
}
