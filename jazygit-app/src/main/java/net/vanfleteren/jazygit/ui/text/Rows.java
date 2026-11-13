package net.vanfleteren.jazygit.ui.text;

import dev.tamboui.text.Line;
import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The lines of what an area of the content panel shows, each drawn when it shows rather than kept drawn. A diff of tens
 * of thousands of lines has a dozen spans on each, with their text: drawn all at once and kept, they took most of what
 * the diff took, while an area only ever shows a screenful of them. What draws a line of a diff holds no more than
 * what it is drawn from, which the diff keeps anyway. The lines that show are kept drawn ({@link #keeping}), so a frame
 * that shows them again, or only moves the cursor over them, does not style them again.
 *
 * @param lines what draws each line, in order
 * @param drawn the lines kept drawn, by index
 */
public record Rows(Vector<Supplier<Line>> lines, Map<Integer, Line> drawn) {

    /**
     * {@code lines}, drawn already.
     */
    public static Rows of(Seq<Line> lines) {
        return drawn(lines.map(Rows::kept));
    }

    /**
     * The lines {@code lines} draws.
     */
    public static Rows drawn(Seq<Supplier<Line>> lines) {
        return new Rows(lines.toVector(), HashMap.empty());
    }

    /**
     * What draws {@code line}, drawn already.
     */
    public static Supplier<Line> kept(Line line) {
        return () -> line;
    }

    public int size() {
        return lines.size();
    }

    /**
     * Line {@code index}, drawn.
     */
    public Line get(int index) {
        return drawn.get(index).getOrElse(() -> lines.get(index).get());
    }

    /**
     * These lines with those from {@code from} up to {@code until} kept drawn, and no others.
     */
    public Rows keeping(int from, int until) {
        return new Rows(lines, HashMap.ofEntries(Vector.range(from, until).map(index -> Tuple.of(index, get(index)))));
    }

    /**
     * These lines with line {@code index} drawn as {@code change} makes it of what it was, once it shows.
     */
    public Rows update(int index, UnaryOperator<Line> change) {
        Rows was = this;
        return new Rows(lines.update(index, () -> change.apply(was.get(index))), drawn.remove(index));
    }

    /**
     * These lines after {@code before}.
     */
    public Rows after(Rows before) {
        return drawn(lines.prependAll(before.lines));
    }

    /**
     * All the lines, drawn: what text that is not shown in an area takes.
     */
    public List<Line> all() {
        return Vector.range(0, size()).map(this::get).toList();
    }

    /**
     * How wide the widest line is. Each is drawn to be measured, on the threads of the common pool, as a line is
     * measured a character at a time and a diff can have tens of thousands; none of them is kept.
     */
    public int widest() {
        return lines.toJavaParallelStream()
                .mapToInt(line -> line.get().width())
                .max()
                .orElse(0);
    }
}
