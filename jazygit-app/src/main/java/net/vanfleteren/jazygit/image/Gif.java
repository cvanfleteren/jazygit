package net.vanfleteren.jazygit.image;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A GIF read into the pixels of its first frame, on its whole screen, with its transparent colour (ADR 84). An
 * animation shows as its first frame.
 */
final class Gif {

    private static final int MAX_CODE_SIZE = 12;

    private Gif() {}

    static boolean is(byte[] bytes) {
        String magic = bytes.length >= 6 ? new String(bytes, 0, 6, StandardCharsets.US_ASCII) : "";
        return magic.equals("GIF87a") || magic.equals("GIF89a");
    }

    /**
     * The pixels of the first frame of {@code bytes}, a GIF, as long as it has no more than {@code maxPixels}; it
     * throws {@link IllegalArgumentException} for what it cannot read.
     */
    static Raster read(byte[] bytes, long maxPixels) {
        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        in.position(6);
        int width = in.getShort() & 0xffff;
        int height = in.getShort() & 0xffff;
        if (width == 0 || height == 0 || (long) width * height > maxPixels) {
            throw new IllegalArgumentException("no pixels, or too many");
        }
        int flags = in.get() & 0xff;
        in.position(in.position() + 2);
        int[] global = (flags & 0x80) != 0 ? colours(in, 2 << (flags & 7)) : new int[0];
        int transparent = -1;
        while (in.hasRemaining()) {
            int block = in.get() & 0xff;
            if (block == 0x21) {
                int label = in.get() & 0xff;
                if (label == 0xf9 && in.remaining() >= 6) {
                    int start = in.position();
                    boolean hasTransparency = (in.get(start + 1) & 1) != 0;
                    transparent = hasTransparency ? in.get(start + 4) & 0xff : -1;
                }
                skipSubBlocks(in);
            } else if (block == 0x2c) {
                return frame(in, width, height, global, transparent);
            } else {
                break;
            }
        }
        throw new IllegalArgumentException("no image");
    }

    private static Raster frame(ByteBuffer in, int width, int height, int[] global, int transparent) {
        int left = in.getShort() & 0xffff;
        int top = in.getShort() & 0xffff;
        int frameWidth = in.getShort() & 0xffff;
        int frameHeight = in.getShort() & 0xffff;
        // A frame is no larger than the screen it is on, whatever it says.
        if ((long) frameWidth * frameHeight > (long) width * height) {
            throw new IllegalArgumentException("a frame larger than its screen");
        }
        int flags = in.get() & 0xff;
        int[] palette = (flags & 0x80) != 0 ? colours(in, 2 << (flags & 7)) : global;
        boolean interlaced = (flags & 0x40) != 0;
        int minimum = in.get() & 0xff;
        if (minimum < 2 || minimum > 8) {
            throw new IllegalArgumentException("code size " + minimum);
        }
        byte[] indices = lzw(subBlocks(in), minimum, frameWidth * frameHeight);
        int[] argb = new int[width * height];
        int[] rows = rows(frameHeight, interlaced);
        for (int i = 0; i < frameHeight; i++) {
            int y = top + rows[i];
            for (int x = 0; x < frameWidth; x++) {
                int index = indices[i * frameWidth + x] & 0xff;
                if (y < height && left + x < width && index != transparent && index < palette.length) {
                    argb[y * width + left + x] = palette[index];
                }
            }
        }
        return new Raster(width, height, argb);
    }

    // Which row of the frame each row of its data is: in order, or in the four passes of an interlaced one.
    private static int[] rows(int height, boolean interlaced) {
        int[] rows = new int[height];
        if (!interlaced) {
            for (int i = 0; i < height; i++) {
                rows[i] = i;
            }
            return rows;
        }
        int[][] passes = {{0, 8}, {4, 8}, {2, 4}, {1, 2}};
        int at = 0;
        for (int[] pass : passes) {
            for (int row = pass[0]; row < height; row += pass[1]) {
                rows[at++] = row;
            }
        }
        return rows;
    }

    private static int[] colours(ByteBuffer in, int count) {
        int[] colours = new int[count];
        for (int i = 0; i < count; i++) {
            colours[i] = 0xff000000 | (in.get() & 0xff) << 16 | (in.get() & 0xff) << 8 | in.get() & 0xff;
        }
        return colours;
    }

    private static void skipSubBlocks(ByteBuffer in) {
        int size = in.get() & 0xff;
        while (size > 0) {
            in.position(in.position() + size);
            size = in.get() & 0xff;
        }
    }

    private static byte[] subBlocks(ByteBuffer in) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int size = in.get() & 0xff;
        while (size > 0) {
            byte[] block = new byte[size];
            in.get(block);
            data.writeBytes(block);
            size = in.hasRemaining() ? in.get() & 0xff : 0;
        }
        return data.toByteArray();
    }

    /**
     * The colour indices that {@code data} codes with LZW from codes of {@code minimum} + 1 bits, as many as
     * {@code count}; missing ones are 0.
     */
    private static byte[] lzw(byte[] data, int minimum, int count) {
        int clear = 1 << minimum;
        int end = clear + 1;
        short[] prefix = new short[1 << MAX_CODE_SIZE];
        byte[] suffix = new byte[1 << MAX_CODE_SIZE];
        byte[] stack = new byte[(1 << MAX_CODE_SIZE) + 1];
        for (int code = 0; code < clear; code++) {
            suffix[code] = (byte) code;
        }
        byte[] out = new byte[count];
        int written = 0;
        int size = minimum + 1;
        int next = end + 1;
        int previous = -1;
        byte first = 0;
        long bits = 0;
        int held = 0;
        int at = 0;
        while (written < count) {
            while (held < size && at < data.length) {
                bits |= (long) (data[at++] & 0xff) << held;
                held += 8;
            }
            if (held < size) {
                break;
            }
            int code = (int) (bits & (1 << size) - 1);
            bits >>>= size;
            held -= size;
            if (code == clear) {
                size = minimum + 1;
                next = end + 1;
                previous = -1;
                continue;
            }
            if (code == end) {
                break;
            }
            if (previous == -1) {
                if (code >= clear) {
                    throw new IllegalArgumentException("code " + code + " before any");
                }
                out[written++] = (byte) code;
                first = (byte) code;
                previous = code;
                continue;
            }
            int top = 0;
            int current = code;
            if (code >= next) {
                if (code > next) {
                    throw new IllegalArgumentException("code " + code + " out of order");
                }
                stack[top++] = first;
                current = previous;
            }
            while (current >= clear) {
                stack[top++] = suffix[current];
                current = prefix[current];
            }
            first = suffix[current];
            stack[top++] = first;
            while (top > 0 && written < count) {
                out[written++] = stack[--top];
            }
            if (next < 1 << MAX_CODE_SIZE) {
                prefix[next] = (short) previous;
                suffix[next] = first;
                next++;
                if (next == 1 << size && size < MAX_CODE_SIZE) {
                    size++;
                }
            }
            previous = code;
        }
        return out;
    }
}
