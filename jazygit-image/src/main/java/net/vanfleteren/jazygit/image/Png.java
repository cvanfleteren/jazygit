package net.vanfleteren.jazygit.image;

import io.vavr.collection.List;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A PNG read into its pixels: every colour type and bit depth, the transparency of {@code tRNS}, and interlaced or
 * not (ADR 84). Its chunks' checksums are not checked, and chunks it does not need are skipped.
 */
final class Png {

    // The first bytes of a PNG, each a character.
    private static final String SIGNATURE = "\u0089PNG\r\n\u001a\n";
    // The seven passes of Adam7.
    private static final List<Pass> ADAM7 = List.of(
            new Pass(0, 0, 8, 8),
            new Pass(4, 0, 8, 8),
            new Pass(0, 4, 4, 8),
            new Pass(2, 0, 4, 4),
            new Pass(0, 2, 2, 4),
            new Pass(1, 0, 2, 2),
            new Pass(0, 1, 1, 2));
    // An image that is not interlaced, as one pass.
    private static final List<Pass> WHOLE = List.of(new Pass(0, 0, 1, 1));

    // The most image data that is inflated: as much as 4096 by 4096 pixels of RGBA of 8 bits.
    private static final long MAX_RAW = 64L * 1024 * 1024 + 64 * 1024;

    private Png() {}

    static boolean is(byte[] bytes) {
        return bytes.length >= SIGNATURE.length()
                && new String(bytes, 0, SIGNATURE.length(), StandardCharsets.ISO_8859_1).equals(SIGNATURE);
    }

    /**
     * The pixels of {@code bytes}, a PNG, as long as it has no more than {@code maxPixels}; it throws
     * {@link IllegalArgumentException} for what it cannot read.
     */
    static Raster read(byte[] bytes, long maxPixels) {
        ByteBuffer in = ByteBuffer.wrap(bytes);
        in.position(SIGNATURE.length());
        Header header = null;
        int[] palette = new int[0];
        byte[] transparency = null;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        boolean ended = false;
        while (!ended && in.remaining() >= 12) {
            int length = in.getInt();
            String type = new String(bytes, in.position(), 4, StandardCharsets.US_ASCII);
            in.position(in.position() + 4);
            if (length < 0 || length > in.remaining() - 4) {
                throw new IllegalArgumentException("a chunk is cut off");
            }
            byte[] chunk = new byte[length];
            in.get(chunk);
            in.getInt();
            switch (type) {
                case "IHDR" -> header = Header.of(chunk, maxPixels);
                case "PLTE" -> palette = palette(chunk);
                case "tRNS" -> transparency = chunk;
                case "IDAT" -> data.writeBytes(chunk);
                case "IEND" -> ended = true;
                default -> {
                    // Not needed to show it.
                }
            }
        }
        if (header == null) {
            throw new IllegalArgumentException("no header");
        }
        Inflated raw = inflate(data.toByteArray(), header.rawSize());
        int[] argb = new int[header.width() * header.height()];
        Colours colours = new Colours(header, palette, transparency);
        int offset = 0;
        for (Pass pass : header.interlaced() ? ADAM7 : WHOLE) {
            int columns = (header.width() - pass.x() + pass.dx() - 1) / pass.dx();
            int rows = (header.height() - pass.y() + pass.dy() - 1) / pass.dy();
            if (columns > 0 && rows > 0) {
                offset = image(raw, offset, columns, rows, header, colours, argb, pass);
            }
        }
        return new Raster(header.width(), header.height(), argb);
    }

    /**
     * Reads an image of {@code columns} and {@code rows}, whole or a pass of Adam7, from {@code raw} at
     * {@code offset} into the pixels it is part of; it says where the next one starts.
     */
    private static int image(
            Inflated raw, int offset, int columns, int rows, Header header, Colours colours, int[] argb, Pass pass) {
        int stride = (columns * header.bitsPerPixel() + 7) / 8;
        int step = Math.max(1, header.bitsPerPixel() / 8);
        byte[] previous = new byte[stride];
        byte[] line = new byte[stride];
        int at = offset;
        for (int row = 0; row < rows; row++) {
            if (at + 1 + stride > raw.length()) {
                throw new IllegalArgumentException("the image data is cut off");
            }
            int filter = raw.bytes()[at];
            System.arraycopy(raw.bytes(), at + 1, line, 0, stride);
            at += 1 + stride;
            unfilter(filter, line, previous, step);
            int y = pass.y() + row * pass.dy();
            for (int column = 0; column < columns; column++) {
                argb[y * header.width() + pass.x() + column * pass.dx()] = colours.at(line, column);
            }
            byte[] swap = previous;
            previous = line;
            line = swap;
        }
        return at;
    }

    private static void unfilter(int filter, byte[] line, byte[] previous, int step) {
        for (int i = 0; i < line.length; i++) {
            int left = i >= step ? line[i - step] & 0xff : 0;
            int up = previous[i] & 0xff;
            int upLeft = i >= step ? previous[i - step] & 0xff : 0;
            int add = switch (filter) {
                case 0 -> 0;
                case 1 -> left;
                case 2 -> up;
                case 3 -> (left + up) / 2;
                case 4 -> paeth(left, up, upLeft);
                default -> throw new IllegalArgumentException("filter " + filter);
            };
            line[i] = (byte) (line[i] + add);
        }
    }

    private static int paeth(int left, int up, int upLeft) {
        int estimate = left + up - upLeft;
        int toLeft = Math.abs(estimate - left);
        int toUp = Math.abs(estimate - up);
        int toUpLeft = Math.abs(estimate - upLeft);
        return toLeft <= toUp && toLeft <= toUpLeft ? left : toUp <= toUpLeft ? up : upLeft;
    }

    /**
     * {@code data} inflated into as many bytes as {@code expected}, which the header says there are at most, and
     * how many of them it filled.
     */
    private static Inflated inflate(byte[] data, long expected) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            byte[] raw = new byte[(int) expected];
            int filled = 0;
            while (!inflater.finished() && filled < raw.length) {
                int read = inflater.inflate(raw, filled, raw.length - filled);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                filled += read;
            }
            return new Inflated(raw, filled);
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("the image data is corrupt", e);
        } finally {
            inflater.end();
        }
    }

    private static int[] palette(byte[] chunk) {
        int[] colours = new int[chunk.length / 3];
        for (int i = 0; i < colours.length; i++) {
            colours[i] =
                    0xff000000 | (chunk[3 * i] & 0xff) << 16 | (chunk[3 * i + 1] & 0xff) << 8 | chunk[3 * i + 2] & 0xff;
        }
        return colours;
    }

    /**
     * The image data, inflated: its first {@code length} bytes.
     */
    private record Inflated(byte[] bytes, int length) {}

    /**
     * Where a pass starts, and how far it steps, in columns and rows.
     */
    private record Pass(int x, int y, int dx, int dy) {}

    private record Header(int width, int height, int bitDepth, int colourType, boolean interlaced) {

        static Header of(byte[] chunk, long maxPixels) {
            ByteBuffer in = ByteBuffer.wrap(chunk);
            Header header = new Header(in.getInt(), in.getInt(), in.get(), in.get(), in.get(12) == 1);
            if (header.width() <= 0 || header.height() <= 0) {
                throw new IllegalArgumentException("no pixels");
            }
            if ((long) header.width() * header.height() > maxPixels) {
                throw new IllegalArgumentException("too many pixels");
            }
            if (!header.valid()) {
                throw new IllegalArgumentException(
                        "colour type " + header.colourType() + " of depth " + header.bitDepth());
            }
            if (header.rawSize() > MAX_RAW) {
                throw new IllegalArgumentException("too much image data");
            }
            return header;
        }

        // Whether its colour type has its bit depth.
        boolean valid() {
            return switch (colourType) {
                case 0 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
                case 3 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
                case 2, 4, 6 -> bitDepth == 8 || bitDepth == 16;
                default -> false;
            };
        }

        int channels() {
            return switch (colourType) {
                case 0, 3 -> 1;
                case 2 -> 3;
                case 4 -> 2;
                case 6 -> 4;
                default -> 0;
            };
        }

        int bitsPerPixel() {
            return channels() * bitDepth;
        }

        // The filtered image data at most, a byte of filter for each row of each pass included.
        long rawSize() {
            return ((long) width * bitsPerPixel() + 7) / 8 * height + height * 2L + 64;
        }
    }

    /**
     * The colour of each pixel of a line, by its colour type.
     */
    private record Colours(Header header, int[] palette, byte[] transparency) {

        int at(byte[] line, int column) {
            int depth = header.bitDepth();
            return switch (header.colourType()) {
                case 0 -> {
                    int grey = sample(line, column, 0, 1, depth);
                    int shade = scale(grey, depth);
                    yield transparent(grey, 0, 0) ? 0 : 0xff000000 | shade << 16 | shade << 8 | shade;
                }
                case 2 -> {
                    int red = sample(line, column, 0, 3, depth);
                    int green = sample(line, column, 1, 3, depth);
                    int blue = sample(line, column, 2, 3, depth);
                    yield transparent(red, green, blue)
                            ? 0
                            : 0xff000000 | scale(red, depth) << 16 | scale(green, depth) << 8 | scale(blue, depth);
                }
                case 3 -> {
                    int index = sample(line, column, 0, 1, depth);
                    int colour = index < palette.length ? palette[index] : 0xff000000;
                    int alpha = transparency != null && index < transparency.length ? transparency[index] & 0xff : 0xff;
                    yield alpha << 24 | colour & 0xffffff;
                }
                case 4 -> {
                    int shade = scale(sample(line, column, 0, 2, depth), depth);
                    yield scale(sample(line, column, 1, 2, depth), depth) << 24 | shade << 16 | shade << 8 | shade;
                }
                default ->
                    scale(sample(line, column, 3, 4, depth), depth) << 24
                            | scale(sample(line, column, 0, 4, depth), depth) << 16
                            | scale(sample(line, column, 1, 4, depth), depth) << 8
                            | scale(sample(line, column, 2, 4, depth), depth);
            };
        }

        // Whether a grey or truecolour pixel is the one colour tRNS makes transparent, compared at its own depth.
        private boolean transparent(int first, int second, int third) {
            if (transparency == null) {
                return false;
            }
            ByteBuffer key = ByteBuffer.wrap(transparency);
            return header.colourType() == 0
                    ? transparency.length >= 2 && (key.getShort(0) & 0xffff) == first
                    : transparency.length >= 6
                            && (key.getShort(0) & 0xffff) == first
                            && (key.getShort(2) & 0xffff) == second
                            && (key.getShort(4) & 0xffff) == third;
        }

        private static int sample(byte[] line, int column, int channel, int channels, int depth) {
            if (depth == 16) {
                int at = (column * channels + channel) * 2;
                return (line[at] & 0xff) << 8 | line[at + 1] & 0xff;
            }
            if (depth == 8) {
                return line[column * channels + channel] & 0xff;
            }
            int bit = (column * channels + channel) * depth;
            return (line[bit / 8] & 0xff) >> (8 - depth - bit % 8) & (1 << depth) - 1;
        }

        // A sample of {@code depth} bits as eight.
        private static int scale(int value, int depth) {
            return switch (depth) {
                case 16 -> value >> 8;
                case 8 -> value;
                default -> value * 255 / ((1 << depth) - 1);
            };
        }
    }
}
