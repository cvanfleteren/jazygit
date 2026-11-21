package net.vanfleteren.jazygit.kernel.content.diff;

import dev.tamboui.text.Line;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn.Mark;
import net.vanfleteren.jazygit.kernel.ui.content.Halves;
import net.vanfleteren.jazygit.kernel.ui.text.Rows;

/**
 * What draws a line, the stops of the cursor that start on it, if any do, and how wide it is at most, when that is
 * known without drawing it: a line of a diff is no wider than its file, so a long diff is measured without drawing each
 * of its lines only to throw it away. With what it is searched in (ADR 126): the code of a line of a diff, without
 * drawing it; all the text of any other line, drawn already. A line is {@link Unified} or a row of the {@link Split}
 * view: only a row of the split view has halves, or is pinned.
 */
sealed interface Marked permits Marked.Unified, Marked.Split {

    Supplier<Line> line();

    List<Mark> marks();

    List<Anchor> places();

    Option<Integer> width();

    Supplier<Rows.Source> source();

    /**
     * Where the halves of the row are, if it is a row of the split view that has them (ADR 139).
     */
    Option<Halves> halves();

    /**
     * How many columns at the start of the line stay where they are when the area scrolls sideways: none,
     * {@link Drawn#PINNED} for all of them.
     */
    int kept();

    /**
     * This line changed by {@code change} when it is drawn.
     */
    Marked mapped(UnaryOperator<Line> change);

    /**
     * This line with its stops on {@code side} (ADR 139).
     */
    Marked onSide(DiffSide side);

    /**
     * This line as a row of the split view: one without halves is as wide as the area, and pinned (ADR 139).
     */
    Split split();

    /**
     * The lines {@code marked}, drawn when they show, with their stops and places in order. How wide the widest is,
     * is worked out from how wide each is at most, on the threads of the common pool, as {@link Rows#widest}
     * measures, for the lines drawn for it.
     */
    static Drawn drawn(List<Marked> marked) {
        return new Drawn(
                Rows.drawn(marked.map(Marked::line), marked.map(Marked::source)),
                marked.zipWithIndex().flatMap(line -> line._1().marks().map(mark -> new Drawn.Stop(mark, line._2()))),
                marked.zipWithIndex()
                        .flatMap(line -> line._1().places().map(anchor -> new Drawn.Place(anchor, line._2()))),
                () -> marked.toJavaParallelStream()
                        .mapToInt(Marked::measured)
                        .max()
                        .orElse(0),
                marked.exists(line -> line.halves().isDefined())
                        ? marked.map(Marked::halves).toVector()
                        : Vector.empty(),
                marked.exists(line -> line.kept() > 0)
                        ? marked.map(Marked::kept).toVector()
                        : Vector.empty(),
                Option.none(),
                Option.none());
    }

    /**
     * How wide the line is, at most: drawn to be measured when that is not known.
     */
    default int measured() {
        return width().getOrElse(() -> line().get().width());
    }

    /**
     * A line without a stop, drawn already.
     */
    static Unified plain(Line line) {
        return of(Rows.kept(line), Option.none());
    }

    /**
     * The line {@code line} draws, which {@code mark} starts on, if it is a stop.
     */
    static Unified of(Supplier<Line> line, Option<Mark> mark) {
        return of(line, mark, List.empty());
    }

    /**
     * The line {@code line} draws, which {@code mark} starts on, if it is a stop, with the lines of a version rendered
     * it is the first row of the block of, {@code places}.
     */
    static Unified of(Supplier<Line> line, Option<Mark> mark, List<Anchor> places) {
        return new Unified(line, mark.toList(), places, Option.none(), () -> Rows.Source.of(line.get()), 0);
    }

    /**
     * A line of the unified view, or any line but a row of the split view. A line of a version shown blamed keeps its
     * gutter, the column of its blame and its numbers, where it is (ADR 147).
     *
     * @param kept how many columns at the start of the line stay where they are when the area scrolls sideways
     */
    record Unified(
            Supplier<Line> line,
            List<Mark> marks,
            List<Anchor> places,
            Option<Integer> width,
            Supplier<Rows.Source> source,
            int kept)
            implements Marked {

        @Override
        public Option<Halves> halves() {
            return Option.none();
        }

        @Override
        public Unified mapped(UnaryOperator<Line> change) {
            Supplier<Line> changed = () -> change.apply(line.get());
            return new Unified(changed, marks, places, Option.none(), () -> Rows.Source.of(changed.get()), kept);
        }

        @Override
        public Unified onSide(DiffSide side) {
            return new Unified(line, marks.map(mark -> mark.onSide(side)), places, width, source, kept);
        }

        @Override
        public Split split() {
            return new Split(line, marks, places, width, source, Option.none());
        }

        /**
         * This line with its first {@code columns} columns kept where they are when the area scrolls sideways.
         */
        Unified keeping(int columns) {
            return new Unified(line, marks, places, width, source, columns);
        }
    }

    /**
     * A row of a file drawn by the split view (ADR 139). One with {@code halves} has a stop for each of them that holds
     * a line; one without is as wide as the area, as the bar of its file, and is pinned: it does not scroll sideways,
     * as the halves scroll each on their own.
     */
    record Split(
            Supplier<Line> line,
            List<Mark> marks,
            List<Anchor> places,
            Option<Integer> width,
            Supplier<Rows.Source> source,
            Option<Halves> halves)
            implements Marked {

        @Override
        public int kept() {
            return halves.isEmpty() ? Drawn.PINNED : 0;
        }

        @Override
        public Split mapped(UnaryOperator<Line> change) {
            Supplier<Line> changed = () -> change.apply(line.get());
            return new Split(changed, marks, places, Option.none(), () -> Rows.Source.of(changed.get()), halves);
        }

        @Override
        public Split onSide(DiffSide side) {
            return new Split(line, marks.map(mark -> mark.onSide(side)), places, width, source, halves);
        }

        @Override
        public Split split() {
            return this;
        }
    }
}
