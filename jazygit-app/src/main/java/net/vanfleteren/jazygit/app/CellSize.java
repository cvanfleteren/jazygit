package net.vanfleteren.jazygit.app;

import dev.tamboui.backend.panama.unix.LibC;
import io.vavr.control.Option;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import net.vanfleteren.jazygit.kernel.ui.markdown.CellPixels;

/**
 * How many pixels a cell of the terminal is, which Sixel draws in (ADR 191): as {@code JAZYGIT_CELL_PIXELS} says, else
 * as the terminal tells the system the size of its window, in cells and in pixels; none where it does not, as on
 * Windows, or tells no pixels. Without it, a picture is half blocks.
 */
final class CellSize {

    private static final Option<CellPixels> SAID = CellPixels.parse(Option.of(System.getenv("JAZYGIT_CELL_PIXELS")));
    // Whether the system has the LibC that is asked, found out once: without it, as on Windows, it is never asked.
    private static final boolean LIBC = loads(LibC.class);

    private CellSize() {}

    /**
     * The size of a cell now: a font made larger changes it.
     */
    static Option<CellPixels> now() {
        return SAID.orElse(() -> LIBC ? asked() : Option.none());
    }

    /**
     * Whether {@code type} is made: its static fields set, which is where LibC looks up what it calls in the system,
     * and fails where it is not. Made outright, as a field of LibC is a constant javac copies in, and a call to it
     * would be the call that fails; not looked up by name, which the native image finds only for the classes it is
     * told of. Not in a Try, which throws a LinkageError on.
     */
    static boolean loads(Class<?> type) {
        try {
            var _ = MethodHandles.lookup().ensureInitialized(type);
            return true;
        } catch (LinkageError | IllegalAccessException noLibC) {
            return false;
        }
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
