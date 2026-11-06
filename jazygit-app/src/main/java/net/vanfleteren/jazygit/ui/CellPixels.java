package net.vanfleteren.jazygit.ui;

import io.vavr.control.Option;

/**
 * How many pixels of the screen a cell of the terminal is, wide and high, which Sixel draws an image in (ADR 87).
 */
public record CellPixels(int width, int height) {

    /**
     * The cell {@code JAZYGIT_CELL_PIXELS} says it is, as {@code 10x20}.
     */
    public static Option<CellPixels> parse(Option<String> said) {
        return said.map(String::strip)
                .filter(text -> text.matches("[1-9][0-9]{0,2}x[1-9][0-9]{0,2}"))
                .map(text -> text.split("x"))
                .map(parts -> new CellPixels(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
    }

    /**
     * A cell of a screen {@code columns} by {@code rows} cells and {@code width} by {@code height} pixels, as the
     * terminal tells the system; none when it does not.
     */
    public static Option<CellPixels> of(int columns, int rows, int width, int height) {
        return columns > 0 && rows > 0 && width >= columns && height >= rows
                ? Option.of(new CellPixels(width / columns, height / rows))
                : Option.none();
    }
}
