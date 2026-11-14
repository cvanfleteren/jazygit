package net.vanfleteren.jazygit;

import io.vavr.control.Option;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Counts the frames drawn, and those that changed no cell on the screen, a line per second in the file
 * {@code jazygit.frameStats} names; off without it. For measuring only: scripts/framebench drives it, and takes the
 * time of whole frames from the JFR events TamboUI records.
 *
 * <p>A timer writes each second once it is over, frames or none, and a shutdown hook writes the last, unfinished one,
 * so the seconds at the end of a run are not lost.
 */
final class FrameStats {

    private final Option<Path> file;
    private long second = System.currentTimeMillis() / 1000L;
    private int frames;
    private int changing;
    private long cells;
    private long renderNanos;
    private boolean drawnThisFrame;
    private boolean flushed;

    private FrameStats(Option<Path> file) {
        this.file = file;
    }

    /** Counting into the file {@code jazygit.frameStats} names, or not at all without it. */
    static FrameStats fromProperty() {
        FrameStats stats = new FrameStats(
                Option.of(System.getProperty("jazygit.frameStats")).map(Path::of));
        if (stats.file.isDefined()) {
            Executors.newSingleThreadScheduledExecutor(task -> {
                        Thread thread = new Thread(task, "frame-stats");
                        thread.setDaemon(true);
                        return thread;
                    })
                    .scheduleAtFixedRate(stats::roll, 100, 100, TimeUnit.MILLISECONDS);
            Runtime.getRuntime().addShutdownHook(new Thread(stats::flush, "frame-stats-flush"));
        }
        return stats;
    }

    /** A frame starts: render() is called. */
    synchronized void rendering() {
        if (file.isEmpty()) {
            return;
        }
        roll();
        frames++;
        drawnThisFrame = false;
    }

    /** render() returned its element. */
    synchronized void rendered(long started) {
        if (file.isDefined()) {
            renderNanos += System.nanoTime() - started;
        }
    }

    /** The frame's diff is drawn on the terminal. */
    synchronized void drawn(int changedCells) {
        if (file.isEmpty()) {
            return;
        }
        if (changedCells > 0 && !drawnThisFrame) {
            changing++;
            drawnThisFrame = true;
        }
        cells += changedCells;
    }

    // Writes the seconds that are over: the current one with its counts, those after it until now as without frames.
    private synchronized void roll() {
        long now = System.currentTimeMillis() / 1000L;
        if (flushed || now == second) {
            return;
        }
        StringBuilder lines = new StringBuilder(line());
        for (long empty = second + 1; empty < now; empty++) {
            lines.append(empty).append(" frames=0 changing=0 needless=0 cells=0 renderMs=0.0\n");
        }
        write(lines.toString());
        second = now;
        frames = 0;
        changing = 0;
        cells = 0;
        renderNanos = 0;
    }

    // At exit: the seconds that are over, then the unfinished one.
    private synchronized void flush() {
        roll();
        write(line());
        flushed = true;
    }

    private String line() {
        return String.format(
                "%d frames=%d changing=%d needless=%d cells=%d renderMs=%.1f%n",
                second, frames, changing, frames - changing, cells, renderNanos / 1e6);
    }

    private void write(String text) {
        file.forEach(path -> {
            try {
                Files.writeString(
                        path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
