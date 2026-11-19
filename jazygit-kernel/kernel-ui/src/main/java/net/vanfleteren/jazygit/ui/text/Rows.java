package net.vanfleteren.jazygit.ui.text;

import dev.tamboui.text.Line;
import io.vavr.Tuple;
import io.vavr.Tuple2;
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
 * @param lines   what draws each line, in order
 * @param sources what gives the text each line is drawn from, which a search reads without drawing it (ADR 126)
 * @param drawn   the lines kept drawn, by index
 */
public record Rows(Vector<Supplier<Line>> lines, Vector<Supplier<Source>> sources, Map<Integer, Line> drawn) {

    /**
     * {@code lines}, drawn already.
     */
    public static Rows of(Seq<Line> lines) {
        return drawn(lines.map(Rows::kept));
    }

    /**
     * {@code lines}, drawn already, each searched in the one of {@code sources} at its index.
     */
    public static Rows of(Seq<Line> lines, Seq<Source> sources) {
        return drawn(lines.map(Rows::kept), sources.map(Rows::given));
    }

    /**
     * The lines {@code lines} draws, each searched in all of its text, drawn for that: for lines that are drawn
     * already, or cost little to draw.
     */
    public static Rows drawn(Seq<Supplier<Line>> lines) {
        return drawn(lines, lines.map(line -> () -> Source.of(line.get())));
    }

    /**
     * The lines {@code lines} draws, each searched in the text the one of {@code sources} at its index gives.
     */
    public static Rows drawn(Seq<Supplier<Line>> lines, Seq<Supplier<Source>> sources) {
        return new Rows(lines.toVector(), sources.toVector(), HashMap.empty());
    }

    /**
     * What draws {@code line}, drawn already.
     */
    public static Supplier<Line> kept(Line line) {
        return () -> line;
    }

    private static Supplier<Source> given(Source source) {
        return () -> source;
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
     * The text line {@code index} is drawn from, which a search reads (ADR 126).
     */
    public Source source(int index) {
        return sources.get(index).get();
    }

    /**
     * These lines with those from {@code from} up to {@code until} kept drawn, and no others.
     */
    public Rows keeping(int from, int until) {
        return new Rows(
                lines, sources, HashMap.ofEntries(Vector.range(from, until).map(index -> Tuple.of(index, get(index)))));
    }

    /**
     * These lines with line {@code index} drawn as {@code change} makes it of what it was, once it shows. It is still
     * searched in what it was drawn from.
     */
    public Rows update(int index, UnaryOperator<Line> change) {
        Rows was = this;
        return new Rows(lines.update(index, () -> change.apply(was.get(index))), sources, drawn.remove(index));
    }

    /**
     * These lines after {@code before}.
     */
    public Rows after(Rows before) {
        return drawn(lines.prependAll(before.lines), sources.prependAll(before.sources));
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

    /**
     * The text a line is searched in (ADR 126), and where it starts in the text of the line drawn: a line of a diff is
     * searched in its code, not in its gutter nor its marker.
     *
     * @param text what is searched
     * @param from the index of the character of the line drawn that {@code text} starts at
     */
    public record Source(String text, int from) {

        /**
         * All the text of {@code line}.
         */
        public static Source of(Line line) {
            return new Source(line.rawContent(), 0);
        }

        /**
         * {@code line}, which starts where the line drawn starts, without its first {@code prefix} characters.
         */
        public static Source cut(String line, int prefix) {
            int cut = Math.clamp(prefix, 0, line.length());
            return new Source(line.substring(cut), cut);
        }

        /**
         * How many columns of prefix the lines of a hunk with the header {@code header} have as git prints them: as
         * many as the header has {@code @} signs less one, one for {@code @@}, two for the {@code @@@} of a combined
         * diff.
         */
        public static int prefix(String header) {
            return Math.max(0, (int) header.chars().takeWhile(c -> c == '@').count() - 1);
        }

        /**
         * What the lines of a diff as git printed it, {@code lines}, are searched in: a line of a hunk without its
         * prefix ({@link #prefix}), the others whole, the header of a hunk and the lines that start a file too.
         */
        public static Vector<Source> ofDiff(Seq<String> lines) {
            return lines.foldLeft(Tuple.of(Vector.<Source>empty(), 0), Source::next)
                    ._1();
        }

        private static Tuple2<Vector<Source>, Integer> next(Tuple2<Vector<Source>, Integer> done, String line) {
            if (line.startsWith("@@")) {
                return Tuple.of(done._1().append(whole(line)), prefix(line));
            }
            if (line.startsWith("diff ") || line.startsWith("* Unmerged path ")) {
                return Tuple.of(done._1().append(whole(line)), 0);
            }
            return Tuple.of(done._1().append(cut(line, done._2())), done._2());
        }

        private static Source whole(String line) {
            return new Source(line, 0);
        }
    }
}
