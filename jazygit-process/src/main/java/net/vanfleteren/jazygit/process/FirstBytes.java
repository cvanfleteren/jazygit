package net.vanfleteren.jazygit.process;

import java.io.ByteArrayOutputStream;

/**
 * The first {@code max} bytes written to it; what comes after is dropped, so a program that prints without end
 * cannot fill the memory with it.
 */
final class FirstBytes extends ByteArrayOutputStream {

    private final int max;

    FirstBytes(int max) {
        this.max = max;
    }

    @Override
    public synchronized void write(int b) {
        if (count < max) {
            super.write(b);
        }
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        super.write(b, off, Math.min(len, Math.max(0, max - count)));
    }
}
