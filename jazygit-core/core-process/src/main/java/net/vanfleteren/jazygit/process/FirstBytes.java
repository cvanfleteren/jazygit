package net.vanfleteren.jazygit.process;

import java.io.ByteArrayOutputStream;

/**
 * The first {@code max} bytes written to it; what comes after is dropped, so a program that prints without end
 * cannot fill the memory with it.
 */
final class FirstBytes extends ByteArrayOutputStream {

    private final int max;
    // How many bytes came, those dropped included.
    private long came;
    private Runnable stop = () -> {};

    FirstBytes(int max) {
        this.max = max;
    }

    /**
     * Runs {@code stop} once more than {@code max} bytes came; at once, if they already did.
     */
    synchronized void whenOver(Runnable stop) {
        this.stop = stop;
        if (over()) {
            stop.run();
        }
    }

    /**
     * Whether more than {@code max} bytes came, of which only the first were kept.
     */
    synchronized boolean over() {
        return came > max;
    }

    @Override
    public synchronized void write(int b) {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        boolean wasOver = over();
        came += len;
        super.write(b, off, Math.min(len, Math.max(0, max - count)));
        if (over() && !wasOver) {
            stop.run();
        }
    }
}
