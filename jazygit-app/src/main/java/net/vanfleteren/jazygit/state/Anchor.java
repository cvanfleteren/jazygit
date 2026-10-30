package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;

/**
 * What a stop of the cursor in a rich diff is, by which it is found again in another drawing of the diff (ADR 60).
 */
public sealed interface Anchor {

    /**
     * What comes before the first file: the preamble, or the header, message and {@code --stat} of a whole commit.
     */
    record Lead() implements Anchor {}

    /**
     * The bar of the file with the path {@code path}.
     */
    record File(String path) implements Anchor {}

    /**
     * The line with the number {@code number} on {@code side} of the file with the path {@code path}.
     */
    record OnLine(String path, Side side, int number) implements Anchor {}

    /**
     * The review thread with the id {@code id}, which was after {@code line} when the anchor was made, if after one:
     * where the cursor goes when the thread is gone. It is found again by its id, wherever it is now
     * ({@link DiffStops#nearest}).
     */
    record Thread(String id, Option<OnLine> line) implements Anchor {}

    /**
     * The anchor of {@code line} of the file with the path {@code path}, if it is a stop: a removed line by its number
     * in the old file, an added or a context line by its number in the new one, as GitHub takes a context line; none
     * for a note, or a line without that number.
     */
    static Option<OnLine> of(String path, DiffLine line) {
        return switch (line.kind()) {
            case REMOVED -> line.oldNumber().map(number -> new OnLine(path, Side.LEFT, number));
            case ADDED, CONTEXT -> line.newNumber().map(number -> new OnLine(path, Side.RIGHT, number));
            case NOTE -> Option.none();
        };
    }
}
