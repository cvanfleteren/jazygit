package net.vanfleteren.jazygit.image;

import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;

/**
 * An image as the terminal draws it, in half blocks (ADR 84): a pixel is half a cell, so a cell is a pixel wide and
 * two high. Its pixels are ARGB, row by row; one less than half opaque is not drawn.
 *
 * <p>A picture read from an image also keeps it {@link #sharp}, at as many pixels as a cell has, for a terminal that
 * draws images itself, with the Kitty graphics protocol (ADR 86).
 *
 * @param width  how many pixels wide, as many columns
 * @param height how many pixels high, twice as many rows, rounded up
 * @param argb   its pixels, row by row
 * @param sharp  the image at as many pixels as the screen shows it at, if it was read from one
 */
public record Picture(int width, int height, Vector<Integer> argb, Option<Sharp> sharp) {

    /**
     * How many pixels of an image, as a browser shows it, a pixel of its picture is, wide and high: a cell is about 8
     * of them wide and 16 high, two pixels of a picture.
     */
    public static final int SCALE = 8;
    /**
     * The most columns a picture is kept at.
     */
    public static final int MAX_COLUMNS = 100;
    /**
     * The most rows of cells a picture is kept and drawn at.
     */
    public static final int MAX_ROWS = 24;
    // The most pixels read from an image, which a 4K screenshot has.
    private static final long MAX_PIXELS = 4096L * 4096L;
    // How many pixels of the screen a cell is about, wide and high, which the sharp image is kept at at most.
    private static final int CELL_WIDTH = 8;
    private static final int CELL_HEIGHT = 16;

    /**
     * A picture of only its half-block pixels.
     */
    public Picture(int width, int height, Vector<Integer> argb) {
        this(width, height, argb, Option.none());
    }

    /**
     * {@code bytes}, a PNG or the first frame of a GIF, as a picture as large as it shows in a browser, with a pixel
     * for each {@link #SCALE} of it, but no larger than {@link #MAX_COLUMNS} and {@link #MAX_ROWS}; or why it cannot
     * be.
     */
    public static Either<String, Picture> of(byte[] bytes) {
        if (!Png.is(bytes) && !Gif.is(bytes)) {
            return Either.left("not a PNG or GIF");
        }
        try {
            return read(bytes);
        } catch (OutOfMemoryError e) {
            // What was read is let go of again; the image is only not shown.
            return Either.left("too large to read");
        }
    }

    /**
     * {@code bytes} read, one image at a time: each may take some 150 MB while it is read, of which the picture keeps
     * a few KB, and a pull request may have many.
     */
    private static synchronized Either<String, Picture> read(byte[] bytes) {
        return Try.of(() -> Png.is(bytes) ? Png.read(bytes, MAX_PIXELS) : Gif.read(bytes, MAX_PIXELS))
                .map(raster -> {
                    int width = Math.ceilDiv(raster.width(), SCALE);
                    int height = Math.ceilDiv(raster.height(), SCALE);
                    Picture picture = fitted(raster, width, height, MAX_COLUMNS, MAX_ROWS * 2);
                    return new Picture(picture.width, picture.height, picture.argb, Option.of(sharp(raster)));
                })
                .toEither()
                .mapLeft(failure -> failure.getMessage() == null ? failure.toString() : failure.getMessage());
    }

    /**
     * This picture as large as {@code width} by {@code height} pixels, but no wider than {@code columns} and no higher
     * than {@code rows}, as it is shrunk; never larger than it is.
     */
    public Picture fit(int width, int height, int columns, int rows) {
        Raster raster = new Raster(
                this.width,
                this.height,
                argb.toJavaStream().mapToInt(Integer::intValue).toArray());
        Picture fitted = fitted(raster, Math.min(width, this.width), Math.min(height, this.height), columns, rows * 2);
        return new Picture(fitted.width, fitted.height, fitted.argb, sharp);
    }

    /**
     * {@code raster} no larger than the most cells a picture takes, at as many pixels as they have: the terminal
     * shrinks it further to the cells it is drawn in.
     */
    private static Sharp sharp(Raster raster) {
        double shrink = Math.min(
                1,
                Math.min(
                        (double) MAX_COLUMNS * CELL_WIDTH / raster.width(),
                        (double) MAX_ROWS * CELL_HEIGHT / raster.height()));
        int width = Math.max(1, (int) Math.round(raster.width() * shrink));
        int height = Math.max(1, (int) Math.round(raster.height() * shrink));
        int[] pixels =
                width == raster.width() && height == raster.height() ? raster.argb() : shrunk(raster, width, height);
        return Sharp.of(width, height, pixels);
    }

    /**
     * How many rows of cells it takes.
     */
    public int rows() {
        return Math.ceilDiv(height, 2);
    }

    /**
     * The pixel at {@code x} and {@code y}, or a transparent one past its bottom.
     */
    public int at(int x, int y) {
        return y < height ? argb.get(y * width + x) : 0;
    }

    /**
     * {@code raster} at {@code width} by {@code height}, kept as wide as high as it was, and shrunk to fit in
     * {@code maxWidth} by {@code maxHeight}; at least a pixel.
     */
    private static Picture fitted(Raster raster, int width, int height, int maxWidth, int maxHeight) {
        double shrink = Math.min(1, Math.min((double) maxWidth / width, (double) maxHeight / height));
        int targetWidth = Math.max(1, (int) Math.round(width * shrink));
        int targetHeight = Math.max(1, (int) Math.round(height * shrink));
        return new Picture(targetWidth, targetHeight, Vector.ofAll(shrunk(raster, targetWidth, targetHeight)));
    }

    /**
     * {@code raster} at {@code width} by {@code height}: each pixel the average of the pixels it covers, weighed by
     * how opaque they are.
     */
    private static int[] shrunk(Raster raster, int width, int height) {
        int[] out = new int[width * height];
        for (int y = 0; y < height; y++) {
            int top = (int) ((long) y * raster.height() / height);
            int bottom = Math.max(top + 1, (int) ((long) (y + 1) * raster.height() / height));
            for (int x = 0; x < width; x++) {
                int left = (int) ((long) x * raster.width() / width);
                int right = Math.max(left + 1, (int) ((long) (x + 1) * raster.width() / width));
                out[y * width + x] = average(raster, left, right, top, bottom);
            }
        }
        return out;
    }

    private static int average(Raster raster, int left, int right, int top, int bottom) {
        long alpha = 0;
        long red = 0;
        long green = 0;
        long blue = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int pixel = raster.argb()[y * raster.width() + x];
                int a = pixel >>> 24;
                alpha += a;
                red += (long) (pixel >> 16 & 0xff) * a;
                green += (long) (pixel >> 8 & 0xff) * a;
                blue += (long) (pixel & 0xff) * a;
            }
        }
        long count = (long) (right - left) * (bottom - top);
        return alpha == 0
                ? 0
                : (int) (alpha / count) << 24
                        | (int) (red / alpha) << 16
                        | (int) (green / alpha) << 8
                        | (int) (blue / alpha);
    }
}
