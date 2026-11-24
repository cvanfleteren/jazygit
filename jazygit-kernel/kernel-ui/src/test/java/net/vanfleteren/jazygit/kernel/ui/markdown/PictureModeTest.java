package net.vanfleteren.jazygit.kernel.ui.markdown;

import static net.vanfleteren.jazygit.kernel.ui.markdown.PictureMode.HALF_BLOCKS;
import static net.vanfleteren.jazygit.kernel.ui.markdown.PictureMode.KITTY;
import static net.vanfleteren.jazygit.kernel.ui.markdown.PictureMode.SIXEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * How pictures are drawn in a terminal: Kitty's protocol, else Sixel, else half blocks (ADR 191).
 */
class PictureModeTest {

    private static final Option<String> NONE = Option.none();

    private static PictureMode in(Map<String, String> env) {
        return PictureMode.of(NONE, env::get);
    }

    @Test
    void kittyAndGhosttyDrawImagesThemselves() {
        assertEquals(KITTY, in(HashMap.of("TERM", "xterm-kitty")));
        assertEquals(KITTY, in(HashMap.of("TERM", "xterm-ghostty")));
        assertEquals(KITTY, in(HashMap.of("TERM", "xterm-256color", "TERM_PROGRAM", "ghostty")));
    }

    @Test
    void terminalsWithSixelPaintItWhereKittysProtocolIsNot() {
        assertEquals(SIXEL, in(HashMap.of("TERM", "xterm-256color", "TERM_PROGRAM", "WezTerm")));
        assertEquals(SIXEL, in(HashMap.of("TERM_PROGRAM", "iTerm.app")));
        assertEquals(SIXEL, in(HashMap.of("TERM", "foot")));
        assertEquals(SIXEL, in(HashMap.of("TERM", "foot-extra")));
        assertEquals(SIXEL, in(HashMap.of("TERM", "xterm-256color", "KONSOLE_VERSION", "240202")));
        assertEquals(SIXEL, in(HashMap.of("WT_SESSION", "1")));
    }

    @Test
    void elsewhereAndInAMultiplexerPicturesAreHalfBlocks() {
        assertEquals(HALF_BLOCKS, in(HashMap.empty()));
        assertEquals(HALF_BLOCKS, in(HashMap.of("TERM", "xterm-256color", "TERM_PROGRAM", "Apple_Terminal")));
        assertEquals(HALF_BLOCKS, in(HashMap.of("TERM", "xterm-kitty", "TMUX", "/tmp/tmux")));
        assertEquals(HALF_BLOCKS, in(HashMap.of("TERM", "foot", "STY", "1.pts")));
    }

    @Test
    void whatIsAskedForIsWhatIsDrawn() {
        Option<String> kitty = Option.of("xterm-kitty");
        assertEquals(HALF_BLOCKS, PictureMode.of(Option.of("halfblocks"), name -> kitty));
        assertEquals(KITTY, PictureMode.of(Option.of("kitty"), name -> Option.of("screen")));
        assertEquals(SIXEL, PictureMode.of(Option.of(" Sixel "), name -> kitty));
        assertEquals(KITTY, PictureMode.of(Option.of("auto"), HashMap.of("TERM", "xterm-kitty")::get));
        assertEquals(HALF_BLOCKS, PictureMode.of(Option.of("braille"), name -> NONE));
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
