package net.vanfleteren.jazygit.lib.image;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * An image as the Kitty graphics protocol is sent it (ADR 188): its pixels as RGBA, 8 bits each, row by row,
 * compressed with zlib and then in base64, as {@code f=32,o=z} says. Only these pixels, read by jazygit itself, reach
 * the terminal, never the bytes of the image as it was written.
 *
 * @param width  how many pixels wide
 * @param height how many pixels high
 * @param rgba   its pixels, as RGBA, compressed with zlib, in base64
 */
public record Sharp(int width, int height, String rgba) {

    /**
     * {@code argb}, {@code width} by {@code height} pixels row by row, as the protocol is sent it.
     */
    static Sharp of(int width, int height, int[] argb) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream zlib = new DeflaterOutputStream(bytes)) {
            byte[] row = new byte[width * 4];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = argb[y * width + x];
                    row[x * 4] = (byte) (pixel >> 16);
                    row[x * 4 + 1] = (byte) (pixel >> 8);
                    row[x * 4 + 2] = (byte) pixel;
                    row[x * 4 + 3] = (byte) (pixel >>> 24);
                }
                zlib.write(row);
            }
        } catch (IOException e) {
            // A ByteArrayOutputStream does not fail.
            throw new UncheckedIOException(e);
        }
        return new Sharp(width, height, Base64.getEncoder().encodeToString(bytes.toByteArray()));
    }

    /**
     * Its pixels as ARGB, row by row, as they were before they were compressed, for Sixel to paint (ADR 188).
     */
    public int[] argb() {
        byte[] bytes;
        try (InflaterInputStream zlib = new InflaterInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(rgba)))) {
            bytes = zlib.readAllBytes();
        } catch (IOException e) {
            // Only what of() compressed is inflated.
            throw new UncheckedIOException(e);
        }
        int[] argb = new int[width * height];
        for (int i = 0; i < argb.length && i * 4 + 3 < bytes.length; i++) {
            argb[i] = (bytes[i * 4 + 3] & 0xff) << 24
                    | (bytes[i * 4] & 0xff) << 16
                    | (bytes[i * 4 + 1] & 0xff) << 8
                    | bytes[i * 4 + 2] & 0xff;
        }
        return argb;
    }
}
