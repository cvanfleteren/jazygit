package net.vanfleteren.jazygit;

import dev.tamboui.backend.panama.unix.LibC;
import io.vavr.control.Option;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import net.vanfleteren.jazygit.ui.CellPixels;

/**
 * How many pixels a cell of the terminal is, which Sixel draws in (ADR 87): as {@code JAZYGIT_CELL_PIXELS} says, else
 * as the terminal tells the system the size of its window, in cells and in pixels; none where it does not, as on
 * Windows, or tells no pixels.
 */
final class CellSize {

    private static final Option<CellPixels> SAID = CellPixels.parse(Option.of(System.getenv("JAZYGIT_CELL_PIXELS")));

    private CellSize() {}

    /**
     * The size of a cell now: a font made larger changes it.
     */
    static Option<CellPixels> now() {
        return SAID.orElse(CellSize::asked);
    }

    // As the terminal tells the system. Not in a Try, which throws a LinkageError on, as where there is no LibC.
    private static Option<CellPixels> asked() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment size = LibC.allocateWinsize(arena);
            if (LibC.ioctl(LibC.STDOUT_FILENO, LibC.TIOCGWINSZ, size) != 0
                    && LibC.ioctl(LibC.STDIN_FILENO, LibC.TIOCGWINSZ, size) != 0) {
                return Option.none();
            }
            // struct winsize: rows, columns, width and height in pixels, each an unsigned short.
            return CellPixels.of(
                    Short.toUnsignedInt(size.get(ValueLayout.JAVA_SHORT, 2)),
                    Short.toUnsignedInt(size.get(ValueLayout.JAVA_SHORT, 0)),
                    Short.toUnsignedInt(size.get(ValueLayout.JAVA_SHORT, 4)),
                    Short.toUnsignedInt(size.get(ValueLayout.JAVA_SHORT, 6)));
        } catch (LinkageError | RuntimeException noTerminal) {
            return Option.none();
        }
    }
}
