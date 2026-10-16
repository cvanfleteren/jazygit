package net.vanfleteren.jazygit;

import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.tui.TuiConfig;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jline.terminal.TerminalBuilder;

/**
 * The terminal backend of the app, which can hand the terminal over to another program, like an editor, and take it
 * back once that is done.
 *
 * <p>While the terminal is handed over, nothing is drawn and no key is read. The app keeps running meanwhile: what it
 * draws is dropped, and drawn anew once it has the terminal back. Keys are read by polling the terminal rather than
 * through JLine, as JLine leaves a thread blocked on reading it after every read that timed out, which would take the
 * first key typed in the other program. On Windows, whose console cannot be polled, the keys are read through JLine.
 *
 * <p>{@link #leave} and {@link #comeBack} are called on the render thread, which also draws, so a frame is never
 * half drawn when the terminal changes hands.
 */
final class HandOverBackend extends JLineBackend {

    // What a read returns when no key came in time, as for JLine.
    private static final int NO_KEY = -2;
    private static final long POLL_MILLIS = 10;
    private static final boolean POLLING =
            !System.getProperty("os.name", "").toLowerCase().startsWith("windows");

    // Not closed: that would close the standard input of the process.
    private final InputStream keys = new FileInputStream(FileDescriptor.in);
    // Held while a key is read, so none is read once the terminal is handed over.
    private final Lock reading = new ReentrantLock();
    private volatile boolean keysPaused;
    private volatile boolean muted;
    private int peeked = NO_KEY;

    private HandOverBackend(org.jline.terminal.Terminal terminal) {
        super(terminal);
    }

    /**
     * On the terminal of this process.
     */
    static HandOverBackend system() throws IOException {
        return new HandOverBackend(
                TerminalBuilder.builder().system(true).jansi(true).build());
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
            super.disableBracketedPaste();
        }
        if (config.mouseCapture()) {
            super.disableMouseCapture();
        }
        super.showCursor();
        if (config.alternateScreen()) {
            super.leaveAlternateScreen();
        }
        if (config.rawMode()) {
            super.disableRawMode();
        }
        super.flush();
        muted = true;
    }

    /**
     * Takes the terminal back, set up as {@code config} says, and reads keys again. The screen is still to be drawn
     * anew.
     */
    void comeBack(TuiConfig config) throws IOException {
        muted = false;
        if (config.rawMode()) {
            super.enableRawMode();
        }
        if (config.alternateScreen()) {
            super.enterAlternateScreen();
        }
        if (config.hideCursor()) {
            super.hideCursor();
        }
        if (config.mouseCapture()) {
            super.enableMouseCapture(config.mouseMotion());
        }
        if (config.bracketedPaste()) {
            super.enableBracketedPaste();
        }
        super.flush();
        keysPaused = false;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            // Made by this backend, so also closed by it.
            jlineTerminal().close();
        }
    }

    @Override
    public int read(int timeoutMs) throws IOException {
        if (!POLLING) {
            return super.read(timeoutMs);
        }
        int key = peeked != NO_KEY ? peeked : poll(timeoutMs);
        peeked = NO_KEY;
        return key;
    }

    @Override
    public int peek(int timeoutMs) throws IOException {
        if (!POLLING) {
            return super.peek(timeoutMs);
        }
        if (peeked == NO_KEY) {
            peeked = poll(timeoutMs);
        }
        return peeked;
    }

    /**
     * The next key typed within {@code timeoutMs}, as a code point.
     */
    private int poll(int timeoutMs) throws IOException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (true) {
            reading.lock();
            try {
                if (!keysPaused && keys.available() > 0) {
                    return codePoint(keys.read());
                }
            } finally {
                reading.unlock();
            }
            long left = (deadline - System.nanoTime()) / 1_000_000L;
            if (left <= 0) {
                return NO_KEY;
            }
            try {
                Thread.sleep(Math.min(POLL_MILLIS, left));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
        }
    }

    /**
     * The code point that starts with the byte {@code first} of its UTF-8 encoding; the bytes after it come with it.
     * A byte that starts no valid encoding is returned as it is.
     */
    private int codePoint(int first) throws IOException {
        int following = first >= 0xF0 ? 3 : first >= 0xE0 ? 2 : first >= 0xC0 ? 1 : 0;
        int codePoint = following == 0 ? first : first & (0x3F >> following);
        for (int i = 0; i < following; i++) {
            int next = keys.read();
            if ((next & 0xC0) != 0x80) {
                return first;
            }
            codePoint = codePoint << 6 | next & 0x3F;
        }
        return codePoint;
    }

    // While the terminal is handed over, what the app draws is dropped.

    @Override
    public void flush() throws IOException {
        if (!muted) {
            super.flush();
        }
    }

    @Override
    public void clear() throws IOException {
        if (!muted) {
            super.clear();
        }
    }

    @Override
    public void showCursor() throws IOException {
        if (!muted) {
            super.showCursor();
        }
    }

    @Override
    public void hideCursor() throws IOException {
        if (!muted) {
            super.hideCursor();
        }
    }

    @Override
    public void enterAlternateScreen() throws IOException {
        if (!muted) {
            super.enterAlternateScreen();
        }
    }

    @Override
    public void leaveAlternateScreen() throws IOException {
        if (!muted) {
            super.leaveAlternateScreen();
        }
    }

    @Override
    public void enableRawMode() throws IOException {
        if (!muted) {
            super.enableRawMode();
        }
    }

    @Override
    public void disableRawMode() throws IOException {
        if (!muted) {
            super.disableRawMode();
        }
    }

    @Override
    public void enableMouseCapture() throws IOException {
        if (!muted) {
            super.enableMouseCapture();
        }
    }

    @Override
    public void enableMouseCapture(boolean motion) throws IOException {
        if (!muted) {
            super.enableMouseCapture(motion);
        }
    }

    @Override
    public void disableMouseCapture() throws IOException {
        if (!muted) {
            super.disableMouseCapture();
        }
    }

    @Override
    public void beginSynchronizedUpdate() throws IOException {
        if (!muted) {
            super.beginSynchronizedUpdate();
        }
    }

    @Override
    public void endSynchronizedUpdate() throws IOException {
        if (!muted) {
            super.endSynchronizedUpdate();
        }
    }

    @Override
    public void scrollUp(int lines) throws IOException {
        if (!muted) {
            super.scrollUp(lines);
        }
    }

    @Override
    public void scrollDown(int lines) throws IOException {
        if (!muted) {
            super.scrollDown(lines);
        }
    }

    @Override
    public void insertLines(int n) throws IOException {
        if (!muted) {
            super.insertLines(n);
        }
    }

    @Override
    public void deleteLines(int n) throws IOException {
        if (!muted) {
            super.deleteLines(n);
        }
    }

    @Override
    public void moveCursorUp(int n) throws IOException {
        if (!muted) {
            super.moveCursorUp(n);
        }
    }

    @Override
    public void moveCursorDown(int n) throws IOException {
        if (!muted) {
            super.moveCursorDown(n);
        }
    }

    @Override
    public void moveCursorRight(int n) throws IOException {
        if (!muted) {
            super.moveCursorRight(n);
        }
    }

    @Override
    public void moveCursorLeft(int n) throws IOException {
        if (!muted) {
            super.moveCursorLeft(n);
        }
    }

    @Override
    public void eraseToEndOfLine() throws IOException {
        if (!muted) {
            super.eraseToEndOfLine();
        }
    }

    @Override
    public void carriageReturn() throws IOException {
        if (!muted) {
            super.carriageReturn();
        }
    }

    @Override
    public void writeRaw(byte[] data) throws IOException {
        if (!muted) {
            super.writeRaw(data);
        }
    }

    @Override
    public void writeRaw(String data) {
        if (!muted) {
            super.writeRaw(data);
        }
    }
}
