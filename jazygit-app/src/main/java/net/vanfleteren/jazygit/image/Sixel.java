package net.vanfleteren.jazygit.image;

import java.util.Arrays;

/**
 * An image as Sixel draws it (ADR 87): at most 255 colours, chosen by median cut of its pixels to 5 bits a channel,
 * each pixel in the nearest of them; a pixel less than half opaque is left out, so what is under it shows.
 */
public final class Sixel {

    // Pixels less opaque than this are not drawn.
    private static final int OPAQUE = 128;
    // The most colours a Sixel image is given; terminals have 256 registers at least.
    private static final int COLOURS = 255;
    // How many runs of the same sixel are written as one, "!n" and the sixel, rather than one by one.
    private static final int RUN = 4;
    // How many colours there are to 5 bits a channel.
    private static final int BINS = 1 << 15;

    private Sixel() {}

    /**
     * {@code argb}, {@code width} by {@code height} pixels row by row, as the sequence that draws it at the cursor:
     * {@code DCS 0;1;0 q}, its colours, its pixels six rows at a time, and {@code ST}. The second parameter, 1, has the
     * terminal leave a pixel that is not drawn as it was.
     */
    public static String encode(int[] argb, int width, int height) {
        int[] binOf = new int[argb.length];
        int[] counts = new int[BINS];
        for (int i = 0; i < argb.length; i++) {
            int pixel = argb[i];
            binOf[i] = pixel >>> 24 < OPAQUE ? -1 : bin(pixel);
            if (binOf[i] >= 0) {
                counts[binOf[i]]++;
            }
        }
        int[] palette = palette(counts);
        int[] register = registers(palette, counts);
        StringBuilder out = new StringBuilder("\u001bP0;1;0q\"1;1;")
                .append(width)
                .append(';')
                .append(height);
        for (int n = 0; n < palette.length; n++) {
            int bin = palette[n];
            out.append('#')
                    .append(n)
                    .append(";2;")
                    .append(percent(bin >> 10))
                    .append(';')
                    .append(percent(bin >> 5 & 0x1f))
                    .append(';')
                    .append(percent(bin & 0x1f));
        }
        int[] index = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            index[i] = binOf[i] < 0 ? -1 : register[binOf[i]];
        }
        for (int top = 0; top < height; top += 6) {
            band(out, index, width, height, top, palette.length);
            out.append('-');
        }
        return out.append("\u001b\\").toString();
    }

    /**
     * The six rows of {@code index} from {@code top}: for each colour in them, its sixels across, then back to the
     * start of the band, {@code $}.
     */
    private static void band(StringBuilder out, int[] index, int width, int height, int top, int colours) {
        boolean[] used = new boolean[colours];
        int rows = Math.min(6, height - top);
        for (int y = top; y < top + rows; y++) {
            for (int x = 0; x < width; x++) {
                int colour = index[y * width + x];
                if (colour >= 0) {
                    used[colour] = true;
                }
            }
        }
        for (int colour = 0; colour < colours; colour++) {
            if (!used[colour]) {
                continue;
            }
            out.append('#').append(colour);
            int run = 0;
            int last = -1;
            for (int x = 0; x < width; x++) {
                int bits = 0;
                for (int row = 0; row < rows; row++) {
                    if (index[(top + row) * width + x] == colour) {
                        bits |= 1 << row;
                    }
                }
                if (bits == last) {
                    run++;
                } else {
                    run(out, last, run);
                    last = bits;
                    run = 1;
                }
            }
            run(out, last, run);
            out.append('$');
        }
    }

    private static void run(StringBuilder out, int bits, int count) {
        if (count == 0) {
            return;
        }
        char sixel = (char) ('?' + bits);
        if (count >= RUN) {
            out.append('!').append(count).append(sixel);
        } else {
            out.append(String.valueOf(sixel).repeat(count));
        }
    }

    // The colour of a pixel to 5 bits a channel, red highest.
    private static int bin(int argb) {
        return (argb >> 19 & 0x1f) << 10 | (argb >> 11 & 0x1f) << 5 | argb >> 3 & 0x1f;
    }

    // At most COLOURS colours for the pixels counted: the colours, to 5 bits a channel, are split in boxes, the box
    // with
    // the most pixels and room to split cut in two where half its pixels are on either side, along its longest side;
    // each box is then the average of its pixels.
    private static int[] palette(int[] counts) {
        java.util.List<int[]> boxes = new java.util.ArrayList<>();
        int[] all = java.util.stream.IntStream.range(0, counts.length)
                .filter(bin -> counts[bin] > 0)
                .toArray();
        if (all.length == 0) {
            return new int[0];
        }
        boxes.add(all);
        while (boxes.size() < COLOURS) {
            int[] widest = null;
            long most = 0;
            for (int[] box : boxes) {
                long pixels = 0;
                for (int bin : box) {
                    pixels += counts[bin];
                }
                if (box.length > 1 && pixels > most) {
                    most = pixels;
                    widest = box;
                }
            }
            if (widest == null) {
                break;
            }
            boxes.remove(widest);
            int channel = longest(widest);
            int[] sorted = java.util.Arrays.stream(widest)
                    .boxed()
                    .sorted(java.util.Comparator.comparingInt(bin -> channel(bin, channel)))
                    .mapToInt(Integer::intValue)
                    .toArray();
            long half = 0;
            int cut = 1;
            for (int i = 0; i < sorted.length - 1; i++) {
                half += counts[sorted[i]];
                cut = i + 1;
                if (half * 2 >= most) {
                    break;
                }
            }
            boxes.add(java.util.Arrays.copyOfRange(sorted, 0, cut));
            boxes.add(java.util.Arrays.copyOfRange(sorted, cut, sorted.length));
        }
        return boxes.stream().mapToInt(box -> average(box, counts)).toArray();
    }

    // Which channel, 0 red, 1 green, 2 blue, the colours of a box differ most in.
    private static int longest(int[] box) {
        int best = 0;
        int widest = -1;
        for (int channel = 0; channel < 3; channel++) {
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            for (int bin : box) {
                low = Math.min(low, channel(bin, channel));
                high = Math.max(high, channel(bin, channel));
            }
            if (high - low > widest) {
                widest = high - low;
                best = channel;
            }
        }
        return best;
    }

    private static int channel(int bin, int channel) {
        return bin >> 10 - 5 * channel & 0x1f;
    }

    // The colour of a box, the average of its pixels, to 5 bits a channel.
    private static int average(int[] box, int[] counts) {
        long pixels = 0;
        long[] sums = new long[3];
        for (int bin : box) {
            pixels += counts[bin];
            for (int channel = 0; channel < 3; channel++) {
                sums[channel] += (long) channel(bin, channel) * counts[bin];
            }
        }
        long total = pixels;
        return (int) Math.round((double) sums[0] / total) << 10
                | (int) Math.round((double) sums[1] / total) << 5
                | (int) Math.round((double) sums[2] / total);
    }

    // For each colour to 5 bits a channel that the image has, the nearest of the palette.
    private static int[] registers(int[] palette, int[] counts) {
        int[] register = new int[BINS];
        Arrays.fill(register, 0);
        for (int bin = 0; bin < register.length; bin++) {
            if (counts[bin] == 0) {
                continue;
            }
            int best = 0;
            int nearest = Integer.MAX_VALUE;
            for (int n = 0; n < palette.length; n++) {
                int distance = distance(bin, palette[n]);
                if (distance < nearest) {
                    nearest = distance;
                    best = n;
                }
            }
            register[bin] = best;
        }
        return register;
    }

    private static int distance(int a, int b) {
        int red = (a >> 10) - (b >> 10);
        int green = (a >> 5 & 0x1f) - (b >> 5 & 0x1f);
        int blue = (a & 0x1f) - (b & 0x1f);
        return 3 * red * red + 4 * green * green + 2 * blue * blue;
    }

    // A channel of 5 bits as Sixel's percent.
    private static int percent(int channel) {
        return Math.round(channel * 100f / 31);
    }
}
