package net.vanfleteren.jazygit.state.diff;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.DiffFilter;
import net.vanfleteren.jazygit.git.model.DiffFiltering;
import net.vanfleteren.jazygit.git.model.HiddenHunks;
import net.vanfleteren.jazygit.git.model.HiddenHunks.Hidden;
import net.vanfleteren.jazygit.git.model.HiddenHunks.Piece;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;

/**
 * What the diff filters hide of a diff as it is drawn (ADR 144): which hunks, judged on those git gave, and so which
 * pieces of the hunks it is drawn in. The view and the model fold a diff alike, so a range and a hunk are the same
 * in both; so they hide alike, through this.
 *
 * @param filters        the filters that hide hunks: those enabled, none when they are switched off
 * @param opened         the hunks the user opened, which show
 * @param threads        the review threads on the diff, if it is a pull request's: a hunk with one on its lines shows
 * @param keepEmptyFiles whether a file whose hunks are all hidden keeps its bar in a diff of several files, as in the
 *                       working tree, where staging a directory stages it
 */
@With
public record Filtering(
        List<DiffFilter> filters, Option<Opened> opened, List<ReviewThread> threads, boolean keepEmptyFiles) {

    /**
     * Nothing hidden.
     */
    public static final Filtering NONE = new Filtering(List.empty(), Option.none(), List.empty(), false);

    /**
     * {@code folded}, the diff {@code git} as it is drawn, with the hunks these filters hide cut out of its files'
     * hunks, and what they hide of each file. A file whose version is shown is left as it is (ADR 102). In a diff of
     * several files, one whose hunks are all hidden is left out, unless empty files are kept or all would be; it is
     * kept without hunks otherwise.
     */
    public Expansion.Folded hide(Expansion.Folded folded, ParsedDiff git) {
        if (filters.isEmpty()) {
            return folded;
        }
        List<ParsedFile> files = folded.diff().files();
        List<Option<Hiding>> hidings = files.map(file -> hiding(file, git, folded));
        boolean several = files.size() > 1;
        boolean anyLeft = hidings.zip(files).exists(pair -> pair._1().forAll(hiding -> !hiding.all()));
        List<Tuple3<ParsedFile, Option<Hiding>, Boolean>> kept = files.zip(hidings)
                .map(pair -> Tuple.of(
                        pair._1(),
                        pair._2(),
                        !(several && !keepEmptyFiles && anyLeft && pair._2().exists(Hiding::all))));
        return new Expansion.Folded(
                new ParsedDiff(
                        folded.diff().preamble(),
                        kept.filter(Tuple3::_3)
                                .map(file -> file._2()
                                        .map(hiding -> new ParsedFile(
                                                file._1().file(),
                                                hiding.all()
                                                        ? List.empty()
                                                        : hiding.pieces().map(Piece::hunk)))
                                        .getOrElse(file._1()))),
                folded.files(),
                kept.filter(Tuple3::_3)
                        .flatMap(file -> file._2()
                                .map(hiding -> Tuple.of(file._1().file().path(), hiding)))
                        .toMap(pair -> pair));
    }

    /**
     * How many hunks of {@code git} these filters hide, opened ones and those with threads left out.
     */
    public int count(ParsedDiff git) {
        return filters.isEmpty()
                ? 0
                : git.files()
                        .map(file -> hidden(file, file.file().path()).size())
                        .sum()
                        .intValue();
    }

    /**
     * What these filters hide of {@code drawn}, the file as it is drawn, judged on that file in {@code git}; none when
     * they hide nothing of it, or a version of it is shown.
     */
    private Option<Hiding> hiding(ParsedFile drawn, ParsedDiff git, Expansion.Folded folded) {
        String path = drawn.file().path();
        if (folded.fold(path).version().isDefined()) {
            return Option.none();
        }
        List<Hidden> hidden = git.files()
                .find(file -> file.file().path().equals(path))
                .map(file -> hidden(file, path))
                .getOrElse(List.empty());
        if (hidden.isEmpty()) {
            return Option.none();
        }
        List<Piece> pieces = HiddenHunks.split(drawn, hidden);
        boolean all = pieces.forAll(piece -> piece.hidden().isDefined() || unchanged(piece.hunk()));
        return Option.of(new Hiding(pieces, all, hidden.flatMap(Hidden::filters).distinct(), hidden.size()));
    }

    private List<Hidden> hidden(ParsedFile file, String path) {
        List<DiffFilter> applying = DiffFiltering.applying(filters, path);
        Option<String> origin = file.file().origin();
        List<ReviewThread> on = threads.filter(thread -> thread.isOn(path, origin));
        return applying.isEmpty()
                ? List.empty()
                : HiddenHunks.of(
                        file,
                        applying,
                        hunk -> DiffFiltering.start(hunk).exists(start -> opened.exists(kept -> kept.has(path, start)))
                                || on.exists(thread -> threadOn(thread, hunk)));
    }

    /**
     * Whether {@code thread} is on a line of {@code hunk}, or on lines that take one of its in.
     */
    private static boolean threadOn(ReviewThread thread, Hunk hunk) {
        return thread.line()
                .exists(last -> hunk.lines().exists(line -> {
                    Option<Integer> number =
                            thread.side() == ReviewThread.Side.LEFT ? line.oldNumber() : line.newNumber();
                    int first = thread.startLine().getOrElse(last);
                    return line.kind() != DiffLine.Kind.NOTE
                            && number.exists(at -> at >= Math.min(first, last) && at <= Math.max(first, last));
                }));
    }

    private static boolean unchanged(ParsedHunk hunk) {
        return hunk.lines()
                .forAll(line -> line.line().kind() == DiffLine.Kind.CONTEXT
                        || line.line().kind() == DiffLine.Kind.NOTE);
    }

    /**
     * What filters hide of a file.
     *
     * @param pieces  the hunks it is drawn in, cut around the hidden ones, each with the hunk of git's that hides it,
     *                if one does
     * @param all     whether all its changes are hidden
     * @param filters the names of the filters that hide them, in their order
     * @param count   how many of git's hunks they hide
     */
    public record Hiding(List<Piece> pieces, boolean all, List<String> filters, int count) {

        /**
         * Nothing hidden.
         */
        public static final Hiding NONE = new Hiding(List.empty(), false, List.empty(), 0);

        /**
         * The hunk of git's that hides the piece at {@code index}, the index of a hunk of the file as it is drawn.
         */
        public Option<Hidden> of(int index) {
            return all || index >= pieces.size()
                    ? Option.none()
                    : pieces.get(index).hidden();
        }

        /**
         * The pieces, as they are numbered among the hunks of the file drawn.
         */
        public Vector<Option<Hidden>> byHunk() {
            return pieces.map(Piece::hidden).toVector();
        }
    }
}
