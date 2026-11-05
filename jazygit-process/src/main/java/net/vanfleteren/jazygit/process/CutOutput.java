package net.vanfleteren.jazygit.process;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * A program's output, read as UTF-8 text up to a {@link Limit}. Once what came is longer than that, the rest is
 * dropped as it comes, and the program is stopped: what is kept never grows much beyond the limit, however much the
 * program prints (ADR 79).
 */
final class CutOutput extends OutputStream {

    private final Limit limit;
    private final CharsetDecoder decoder = StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
    private final CharBuffer decoded = CharBuffer.allocate(8192);
    private final StringBuilder text = new StringBuilder();
    // The bytes of a character the last write left unfinished.
    private ByteBuffer unfinished = ByteBuffer.allocate(0);
    private long ends;
    private boolean over;
    private boolean finished;
    private Runnable stop = () -> {};

    CutOutput(Limit limit) {
        this.limit = limit;
    }

    /**
     * Runs {@code stop} once what came is longer than the limit; at once, if it already is.
     */
    synchronized void whenOver(Runnable stop) {
        this.stop = stop;
        if (over) {
            stop.run();
        }
    }

    @Override
    public void write(int b) {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        if (over || finished) {
            return;
        }
        ByteBuffer in = ByteBuffer.allocate(unfinished.remaining() + len)
                .put(unfinished)
                .put(b, off, len)
                .flip();
        decode(in, false);
        unfinished = ByteBuffer.allocate(in.remaining()).put(in).flip();
    }

    /**
     * What was kept: all of it, or cut off within the limit. A character the output ended in the middle of is
     * replaced.
     */
    synchronized Limited limited() {
        if (!over && !finished) {
            decode(unfinished, true);
            decoder.flush(decoded);
            append();
        }
        finished = true;
        return limit.cut(text.toString());
    }

    /**
     * The whole lines kept so far, cut off, as when the program did not finish; what comes after is dropped.
     */
    synchronized Limited wholeLines() {
        finished = true;
        String kept = limit.cut(text.toString()).text();
        return new Limited(kept.substring(0, kept.lastIndexOf('\n') + 1), true);
    }

    private void decode(ByteBuffer in, boolean last) {
        boolean full = true;
        while (full && !over) {
            full = decoder.decode(in, decoded, last).isOverflow();
            append();
        }
    }

    private void append() {
        decoded.flip();
        int from = text.length();
        text.append(decoded);
        decoded.clear();
        for (int at = from; at < text.length(); at++) {
            if (text.charAt(at) == '\n') {
                ends++;
            }
        }
        if (limit.over(text.length(), ends, text.isEmpty() || text.charAt(text.length() - 1) == '\n')) {
            over = true;
            stop.run();
        }
    }
}
