package net.vanfleteren.jazygit.ui;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import net.vanfleteren.jazygit.image.Picture;

/**
 * The cells of a {@link Picture} in half blocks (ADR 84): the upper half of a cell is one pixel, its lower half the
 * one under it. A pixel that is less than half opaque is not drawn, so the background shows there.
 */
final class HalfBlocks {

    private static final String UPPER = "▀";
    private static final String LOWER = "▄";
    private static final int OPAQUE = 128;

    private HalfBlocks() {}

    /**
     * The style and symbol of the {@code n}th cell of {@code picture}, row by row; a blank one past its end.
     */
    static Tuple2<Style, String> cell(Picture picture, int n) {
        int column = n % picture.width();
        int row = n / picture.width();
        if (row >= picture.rows()) {
            return Tuple.of(Style.EMPTY, " ");
        }
        int top = picture.at(column, 2 * row);
        int bottom = picture.at(column, 2 * row + 1);
        boolean upper = top >>> 24 >= OPAQUE;
        boolean lower = bottom >>> 24 >= OPAQUE;
        if (upper && lower) {
            return Tuple.of(Style.EMPTY.fg(colour(top)).bg(colour(bottom)), UPPER);
        }
        if (upper) {
            return Tuple.of(Style.EMPTY.fg(colour(top)), UPPER);
        }
        return lower ? Tuple.of(Style.EMPTY.fg(colour(bottom)), LOWER) : Tuple.of(Style.EMPTY, " ");
    }

    private static Color colour(int argb) {
        return Color.rgb(argb >> 16 & 0xff, argb >> 8 & 0xff, argb & 0xff);
    }
}
