package net.vanfleteren.jazygit.ui;

import static net.vanfleteren.jazygit.ui.PictureMode.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * How pictures are drawn in a terminal (ADR 86, ADR 87).
 */
class PictureModeTest {

    private static final Option<String> NONE = Option.none();

    @Test
    void kittyAndGhosttyDrawImagesOutsideAMultiplexer() {
        assertEquals(KITTY, of(NONE, Option.of("xterm-kitty"), NONE, false));
        assertEquals(KITTY, of(NONE, Option.of("xterm-ghostty"), NONE, false));
        assertEquals(KITTY, of(NONE, Option.of("xterm-256color"), Option.of("ghostty"), false));
        assertEquals(KITTY, of(Option.of("auto"), Option.of("xterm-kitty"), NONE, false));
        assertEquals(HALF_BLOCKS, of(NONE, Option.of("xterm-kitty"), NONE, true));
        assertEquals(HALF_BLOCKS, of(NONE, Option.of("xterm-256color"), Option.of("WezTerm"), false));
        assertEquals(HALF_BLOCKS, of(NONE, NONE, NONE, false));
    }

    @Test
    void whatIsAskedForIsWhatIsDrawn() {
        assertEquals(HALF_BLOCKS, of(Option.of("halfblocks"), Option.of("xterm-kitty"), NONE, false));
        assertEquals(KITTY, of(Option.of("kitty"), Option.of("screen"), NONE, true));
        assertEquals(SIXEL, of(Option.of(" Sixel "), Option.of("xterm-kitty"), NONE, false));
        assertEquals(HALF_BLOCKS, of(Option.of("braille"), NONE, NONE, false));
    }

    @Test
    void onlyHalfBlocksAreNoPlaceholders() {
        assertFalse(HALF_BLOCKS.placeholders());
        assertTrue(KITTY.placeholders());
        assertTrue(SIXEL.placeholders());
    }

    @Test
    void aCellIsAsLargeAsTheScreenOverItsCellsOrAsSaid() {
        assertEquals(Option.of(new CellPixels(9, 18)), CellPixels.of(100, 40, 900, 720));
        assertEquals(Option.none(), CellPixels.of(100, 40, 0, 0));
        assertEquals(Option.of(new CellPixels(10, 20)), CellPixels.parse(Option.of("10x20")));
        assertEquals(Option.none(), CellPixels.parse(Option.of("10 by 20")));
        assertEquals(Option.none(), CellPixels.parse(Option.of("0x20")));
    }
}
