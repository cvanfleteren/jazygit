package net.vanfleteren.jazygit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Counts the frames drawn, and those that changed no cell on the screen, a line per second in the file
 * {@code jazygit.frameStats} names; off without it. For measuring only: scripts/framebench drives it, and takes the
 * time of whole frames from the JFR events TamboUI records.
 */
final class FrameStats {

    private static final Path FILE = Option.file();
    private static long second = System.currentTimeMillis() / 1000L;
    private static int frames;
    private static int changing;
    private static long cells;
    private static long renderNanos;
    private static boolean drawnThisFrame;

    private FrameStats() {}

    static boolean on() {
        return FILE != null;
    }

    /** A frame starts: render() is called. */
    static void rendering() {
        if (!on()) {
            return;
        }
        roll();
        frames++;
        drawnThisFrame = false;
    }

    /** render() returned its element. */
    static void rendered(long started) {
        if (on()) {
            renderNanos += System.nanoTime() - started;
        }
    }

    /** The frame's diff is drawn. */
    static void drawn(int changedCells) {
        if (!on()) {
            return;
        }
        if (changedCells > 0 && !drawnThisFrame) {
            changing++;
            drawnThisFrame = true;
        }
        cells += changedCells;
    }


    private static synchronized void roll() {
        long now = System.currentTimeMillis() / 1000L;
        if (now == second) {
            return;
        }
        String line = String.format(
                "%d frames=%d changing=%d needless=%d cells=%d renderMs=%.1f%n",
                second, frames, changing, frames - changing, cells, renderNanos / 1e6);
        try {
            Files.writeString(FILE, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Seconds without a frame are written as none.
        for (long empty = second + 1; empty < now; empty++) {
            try {
                Files.writeString(
                        FILE,
                        empty + " frames=0 changing=0 needless=0 cells=0 renderMs=0.0\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        second = now;
        frames = 0;
        changing = 0;
        cells = 0;
        renderNanos = 0;
    }

    private static final class Option {
        static Path file() {
            String name = System.getProperty("jazygit.frameStats");
            return name == null ? null : Path.of(name);
        }
    }
}
