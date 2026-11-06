package net.vanfleteren.jazygit.image;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * An image as Sixel draws it (ADR 87).
 */
class SixelTest {

    private static final int RED = 0xffff0000;

    @Test
    void anImageIsItsSizeItsColoursAndItsSixelsBetweenDcsAndSt() {
        String sixel = Sixel.encode(new int[] {RED, RED}, 2, 1);

        assertEquals("\u001bP0;1;0q\"1;1;2;1#0;2;100;0;0#0@@$-\u001b\\", sixel);
    }

    @Test
    void aRunOfFourOrMoreIsWrittenOnce() {
        int[] row = new int[10];
        Arrays.fill(row, RED);

        assertTrue(Sixel.encode(row, 10, 1).contains("#0!10@$"));
    }

    @Test
    void aPixelLessThanHalfOpaqueIsNotDrawn() {
        String sixel = Sixel.encode(new int[] {0x40ff0000, RED}, 2, 1);

        assertTrue(sixel.contains("#0?@$"), sixel);
        assertEquals("\u001bP0;1;0q\"1;1;1;1-\u001b\\", Sixel.encode(new int[] {0}, 1, 1));
    }

    @Test
    void sixRowsAreABandAndEachColourOfItIsItsOwnPass() {
        int[] pixels = new int[7];
        Arrays.fill(pixels, RED);
        pixels[6] = 0xff0000ff;

        String sixel = Sixel.encode(pixels, 1, 7);

        String red = register(sixel, "100;0;0");
        String blue = register(sixel, "0;0;100");
        assertTrue(sixel.endsWith("#" + red + "~$-#" + blue + "@$-\u001b\\"), sixel);
    }

    @Test
    void anImageOfManyColoursIsGiven255() {
        int[] pixels = new int[4096];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xff000000 | (i >> 8) << 20 | (i >> 4 & 0xf) << 12 | (i & 0xf) << 4;
        }
        // 4096 colours, each its own to 5 bits a channel.

        String sixel = Sixel.encode(pixels, 64, 64);

        assertEquals(255, Pattern.compile("#\\d+;2;").matcher(sixel).results().count());
    }

    // The register colour rgb, in percent, is defined as.
    private static String register(String sixel, String rgb) {
        java.util.regex.Matcher defined =
                Pattern.compile("#(\\d+);2;" + rgb + "(?!\\d)").matcher(sixel);
        assertTrue(defined.find(), sixel);
        return defined.group(1);
    }
}
