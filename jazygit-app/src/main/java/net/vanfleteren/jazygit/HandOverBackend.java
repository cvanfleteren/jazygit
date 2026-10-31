package net.vanfleteren.jazygit;

import dev.tamboui.buffer.DiffResult;
import dev.tamboui.layout.Position;
import dev.tamboui.layout.Size;
import dev.tamboui.terminal.Backend;
import dev.tamboui.terminal.BackendFactory;
import dev.tamboui.tui.TuiConfig;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * The terminal backend of the app, which can hand the terminal over to another program, like an editor, and take it
 * back once that is done. It wraps the backend TamboUI would pick by itself, so any backend can be used.
 *
 * <p>While the terminal is handed over, nothing is drawn and no key is read. The app keeps running meanwhile: what it
 * draws is dropped, and drawn anew once it has the terminal back. This relies on a read of the wrapped backend that
 * timed out leaving no read behind, as for the Panama backend; JLine leaves a thread blocked on reading the terminal,
 * which would take the first key typed in the other program.
 *
 * <p>{@link #leave} and {@link #comeBack} are called on the render thread, which also draws, so a frame is never
 * half drawn when the terminal changes hands.
 */
final class HandOverBackend implements Backend {

    // What a read returns when no key came in time.
    private static final int NO_KEY = -2;
    private static final int ESC = 27;
    // What a terminal that reports the focus sends when it gets it, ESC [ I, and when it loses it, ESC [ O. TamboUI
    // reads both as an unknown key, so they are taken out before it reads them (ADR 58).
    private static final int FOCUS_IN = 'I';
    private static final int FOCUS_OUT = 'O';
    private static final String REPORT_FOCUS = "\u001b[?1004h";
    private static final String STOP_REPORTING_FOCUS = "\u001b[?1004l";
    // How long the rest of an escape sequence is waited for once its ESC came: the terminal sends it at once.
    private static final int SEQUENCE_MILLIS = 50;
    // How long a key is read for at a time, which is as long as handing the terminal over waits for a read. As long as
    // TamboUI reads for: shorter slices only wake the reader more often while nothing is typed.
    private static final int SLICE_MILLIS = TuiConfig.DEFAULT_POLL_TIMEOUT;

    private final Backend backend;
    // Told when the terminal gets the focus (true) or loses it (false), on the thread that reads keys.
    private final Consumer<Boolean> focus;
    // Held while a key is read, so none is read once the terminal is handed over. Fair, as the reader takes it again
    // right after it lets go, and would else keep the render thread waiting in leave for as long as it reads.
    private final Lock reading = new ReentrantLock(true);
    // What was read past an ESC to tell whether it began a focus report, and was not: read again first. Only the
    // thread that reads keys touches it.
    private final Deque<Integer> unread = new ArrayDeque<>();
    private volatile boolean keysPaused;
    private volatile boolean muted;
    // Whether the terminal has the focus, as it reported last, or a key read since says. Only the thread that reads
    // keys touches it.
    private boolean focused = true;

    HandOverBackend(Backend backend, Consumer<Boolean> focus) {
        this.backend = backend;
        this.focus = focus;
    }

    /**
     * On the terminal of this process, through the backend TamboUI picks, as {@code tamboui.backend} or
     * {@code TAMBOUI_BACKEND} may say, telling {@code focus} when the terminal gets the focus or loses it.
     */
    static HandOverBackend system(Consumer<Boolean> focus) throws IOException {
        return new HandOverBackend(BackendFactory.create(), focus);
    }

    /**
     * Hands the terminal over: stops reading keys, and puts the terminal back as it was before the app started, as
     * {@code config} set it up.
     */
    void leave(TuiConfig config) throws IOException {
        reading.lock();
        try {
            keysPaused = true;
        } finally {
            reading.unlock();
        }
        if (config.bracketedPaste()) {
            backend.disableBracketedPaste();
        }
        if (config.mouseCapture()) {
            backend.disableMouseCapture();
        }
        backend.showCursor();
        if (config.alternateScreen()) {
            backend.writeRaw(STOP_REPORTING_FOCUS);
            backend.leaveAlternateScreen();
        }
        if (config.rawMode()) {
            backend.disableRawMode();
        }
        backend.flush();
        muted = true;
    }

    /**
     * Takes the terminal back, set up as {@code config} says, and reads keys again. The screen is still to be drawn
     * anew.
     */
    void comeBack(TuiConfig config) throws IOException {
        muted = false;
        if (config.rawMode()) {
            backend.enableRawMode();
        }
        if (config.alternateScreen()) {
            backend.enterAlternateScreen();
            backend.writeRaw(REPORT_FOCUS);
        }
        if (config.hideCursor()) {
            backend.hideCursor();
        }
        if (config.mouseCapture()) {
            backend.enableMouseCapture(config.mouseMotion());
        }
        if (config.bracketedPaste()) {
            backend.enableBracketedPaste();
        }
        backend.flush();
        keysPaused = false;
    }

    @Override
    public void close() throws IOException {
        backend.close();
    }

    @Override
    public Size size() throws IOException {
        return backend.size();
    }

    @Override
    public void onResize(Runnable handler) {
        backend.onResize(handler);
    }

    // While the terminal is handed over, no key is read: the read waits as long as it would for a key that does not
    // come.

    /**
     * The next key within {@code timeoutMs}, leaving out the reports of the focus, which go to {@link #focus}. A key,
     * a click or a paste only comes from a terminal that has the focus: one read after the terminal said it lost it
     * tells that it has it again, should the report of that have been lost, split up on its way or never sent.
     */
    @Override
    public int read(int timeoutMs) throws IOException {
        int key = next(timeoutMs);
        if (key != NO_KEY && !focused) {
            reportFocus(true);
        }
        return key;
    }

    /**
     * Tells {@link #focus} that the terminal got the focus, or lost it, unless it was told so last.
     */
    private void reportFocus(boolean in) {
        if (in != focused) {
            focused = in;
            focus.accept(in);
        }
    }

    /**
     * The next key within {@code timeoutMs}, the reports of the focus left out.
     */
    private int next(int timeoutMs) throws IOException {
        if (!unread.isEmpty()) {
            return unread.poll();
        }
        long deadline = System.nanoTime() + Math.max(timeoutMs, 0) * 1_000_000L;
        while (true) {
            int key = await((int) Math.max((deadline - System.nanoTime()) / 1_000_000L, 0), backend::read);
            if (key != ESC || await(SEQUENCE_MILLIS, backend::peek) != '[') {
                return key;
            }
            int bracket = await(SEQUENCE_MILLIS, backend::read);
            int last = await(SEQUENCE_MILLIS, backend::read);
            if (last != FOCUS_IN && last != FOCUS_OUT) {
                unread.add(bracket);
                if (last != NO_KEY) {
                    unread.add(last);
                }
                return key;
            }
            reportFocus(last == FOCUS_IN);
            if (System.nanoTime() >= deadline) {
                return NO_KEY;
            }
        }
    }

    @Override
    public int peek(int timeoutMs) throws IOException {
        return unread.isEmpty() ? await(timeoutMs, backend::peek) : unread.peek();
    }

    /**
     * The key {@code read} gives within {@code timeoutMs}, read in short slices, so that {@link #leave} only waits for
     * the slice under way rather than for the whole read.
     */
    private int await(int timeoutMs, Read read) throws IOException {
        long deadline = System.nanoTime() + Math.max(timeoutMs, 0) * 1_000_000L;
        while (true) {
            // Rounded up: rounded down, the last part of a millisecond is read in slices of 0, over and over until the
            // deadline, at every read that waits in vain.
            int slice =
                    (int) Math.min(SLICE_MILLIS, Math.max(Math.ceilDiv(deadline - System.nanoTime(), 1_000_000L), 0));
            reading.lock();
            try {
                if (!keysPaused) {
                    int key = read.within(slice);
                    if (key != NO_KEY) {
                        return key;
                    }
                }
            } finally {
                reading.unlock();
            }
            if (System.nanoTime() >= deadline) {
                return NO_KEY;
            }
            if (keysPaused) {
                pause(slice);
            }
        }
    }

    private static void pause(int millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        }
    }

    // Asks the terminal, and reads its answer as keys.
    @Override
    public Position getCursorPosition() throws IOException {
        return muted ? Position.ORIGIN : backend.getCursorPosition();
    }

    // While the terminal is handed over, what the app draws is dropped.

    @Override
    public void draw(DiffResult diff) throws IOException {
        if (!muted) {
            backend.draw(diff);
        }
    }

    @Override
    public void flush() throws IOException {
        if (!muted) {
            backend.flush();
        }
    }

    @Override
    public void clear() throws IOException {
        if (!muted) {
            backend.clear();
        }
    }

    @Override
    public void setCursorPosition(Position position) throws IOException {
        if (!muted) {
            backend.setCursorPosition(position);
        }
    }

    @Override
    public void showCursor() throws IOException {
        if (!muted) {
            backend.showCursor();
        }
    }

    @Override
    public void hideCursor() throws IOException {
        if (!muted) {
            backend.hideCursor();
        }
    }

    // The terminal reports the focus while the app is on its alternate screen.

    @Override
    public void enterAlternateScreen() throws IOException {
        if (!muted) {
            backend.enterAlternateScreen();
            backend.writeRaw(REPORT_FOCUS);
        }
    }

    @Override
    public void leaveAlternateScreen() throws IOException {
        if (!muted) {
            backend.writeRaw(STOP_REPORTING_FOCUS);
            backend.leaveAlternateScreen();
        }
    }

    @Override
    public void enableRawMode() throws IOException {
        if (!muted) {
            backend.enableRawMode();
        }
    }

    @Override
    public void disableRawMode() throws IOException {
        if (!muted) {
            backend.disableRawMode();
        }
    }

    @Override
    public void enableMouseCapture() throws IOException {
        if (!muted) {
            backend.enableMouseCapture();
        }
    }

    @Override
    public void enableMouseCapture(boolean motion) throws IOException {
        if (!muted) {
            backend.enableMouseCapture(motion);
        }
    }

    @Override
    public void disableMouseCapture() throws IOException {
        if (!muted) {
            backend.disableMouseCapture();
        }
    }

    @Override
    public void beginSynchronizedUpdate() throws IOException {
        if (!muted) {
            backend.beginSynchronizedUpdate();
        }
    }

    @Override
    public void endSynchronizedUpdate() throws IOException {
        if (!muted) {
            backend.endSynchronizedUpdate();
        }
    }

    @Override
    public void enableBracketedPaste() throws IOException {
        if (!muted) {
            backend.enableBracketedPaste();
        }
    }

    @Override
    public void disableBracketedPaste() throws IOException {
        if (!muted) {
            backend.disableBracketedPaste();
        }
    }

    @Override
    public void scrollUp(int lines) throws IOException {
        if (!muted) {
            backend.scrollUp(lines);
        }
    }

    @Override
    public void scrollDown(int lines) throws IOException {
        if (!muted) {
            backend.scrollDown(lines);
        }
    }

    @Override
    public void insertLines(int n) throws IOException {
        if (!muted) {
            backend.insertLines(n);
        }
    }

    @Override
    public void deleteLines(int n) throws IOException {
        if (!muted) {
            backend.deleteLines(n);
        }
    }

    @Override
    public void moveCursorUp(int n) throws IOException {
        if (!muted) {
            backend.moveCursorUp(n);
        }
    }

    @Override
    public void moveCursorDown(int n) throws IOException {
        if (!muted) {
            backend.moveCursorDown(n);
        }
    }

    @Override
    public void moveCursorRight(int n) throws IOException {
        if (!muted) {
            backend.moveCursorRight(n);
        }
    }

    @Override
    public void moveCursorLeft(int n) throws IOException {
        if (!muted) {
            backend.moveCursorLeft(n);
        }
    }

    @Override
    public void eraseToEndOfLine() throws IOException {
        if (!muted) {
            backend.eraseToEndOfLine();
        }
    }

    @Override
    public void carriageReturn() throws IOException {
        if (!muted) {
            backend.carriageReturn();
        }
    }

    @Override
    public void setWindowTitle(String title) throws IOException {
        if (!muted) {
            backend.setWindowTitle(title);
        }
    }

    @Override
    public void saveWindowTitle() throws IOException {
        if (!muted) {
            backend.saveWindowTitle();
        }
    }

    @Override
    public void restoreWindowTitle() throws IOException {
        if (!muted) {
            backend.restoreWindowTitle();
        }
    }

    @Override
    public void writeRaw(byte[] data) throws IOException {
        if (!muted) {
            backend.writeRaw(data);
        }
    }

    @Override
    public void writeRaw(String data) throws IOException {
        if (!muted) {
            backend.writeRaw(data);
        }
    }

    @Override
    public void writeRaw(CharSequence data) throws IOException {
        if (!muted) {
            backend.writeRaw(data);
        }
    }

    // A read or peek of the backend.
    @FunctionalInterface
    private interface Read {
        int within(int timeoutMs) throws IOException;
    }
}
