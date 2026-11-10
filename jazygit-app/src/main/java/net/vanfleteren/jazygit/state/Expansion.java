package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.DiffContext;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.Folding;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;

/**
 * The lines the user expanded in the diff an area of the content panel shows, around its hunks (ADR 77).
 *
 * @param content what the area showed when the first of them was expanded
 * @param files   what is expanded in each file, by its path
 */
@With
public record Expansion(ScrolledContent content, Map<String, Expanded> files) {

    /**
     * Nothing expanded yet in the diff of {@code content}.
     */
    public static Expansion of(ScrolledContent content) {
        return new Expansion(content, HashMap.empty());
    }

    /**
     * Whether these hold for an area that shows {@code shown}: the same changes, read again or not, and for the
     * working tree any of its diffs, whose files they are found in by their path.
     */
    public boolean holdsFor(ScrolledContent shown) {
        return content.sameChanges(shown)
                || (content instanceof ScrolledContent.Diff && shown instanceof ScrolledContent.Diff);
    }

    /**
     * {@code diff} with each file read whole folded to {@code context} lines around its changes and the lines
     * expanded in it, unless the file read whole does not have the changes {@code diff} has; with what is known of
     * the length of each file, and whether it is being read.
     */
    public Folded folded(ParsedDiff diff, DiffContext context) {
        List<FoldedFile> folded = diff.files().map(file -> folded(file, context));
        return new Folded(
                new ParsedDiff(diff.preamble(), folded.map(FoldedFile::file)),
                folded.toMap(fold -> fold.file().file().path(), FoldedFile::fold));
    }

    private FoldedFile folded(ParsedFile file, DiffContext context) {
        String path = file.file().path();
        return files.get(path)
                .map(expanded -> switch (expanded.whole()) {
                    case Loadable.Loading<DiffText>() -> new FoldedFile(file, new Fold(Option.none(), Read.READING));
                    case Loadable.Failed<DiffText>(var failure) ->
                        new FoldedFile(file, new Fold(Option.none(), Read.FAILED));
                    case Loadable.Loaded<DiffText>(DiffText whole) -> folded(file, whole, expanded, context);
                })
                .getOrElse(() -> new FoldedFile(file, Fold.UNKNOWN));
    }

    /**
     * {@code file} folded from {@code whole}, the diff of it read with the whole file as context, unless that does not
     * have the changes {@code file} has. A read cut at the lines jazygit reads of a diff tells no length: when its
     * changes are all before the cut, the file is folded up to there; else it cannot be.
     */
    private FoldedFile folded(ParsedFile file, DiffText whole, Expanded expanded, DiffContext context) {
        String path = file.file().path();
        return whole.parsed()
                .files()
                .find(read -> read.file().path().equals(path))
                .filter(read -> Folding.agrees(read, file))
                .map(read -> new FoldedFile(
                        Folding.fold(read, context.lines(), expanded.lines()),
                        whole.truncated()
                                ? new Fold(Option.none(), Read.CUT, Option.of(Folding.length(read)))
                                : new Fold(Option.of(Folding.length(read)), Read.WHOLE)))
                .getOrElse(() -> new FoldedFile(
                        file, whole.truncated() ? new Fold(Option.none(), Read.TOO_LONG) : Fold.UNKNOWN));
    }

    /**
     * What is expanded in a file.
     *
     * @param read  how the file is read whole, with the whole file as context
     * @param whole the file read whole
     * @param lines the lines of the new file expanded, in the order they were
     */
    @With
    public record Expanded(ReadWholeFile read, Loadable<DiffText> whole, List<Folding.Lines> lines) {}

    /**
     * A diff as it is drawn, its files folded, and what is known of each of them, by its path.
     */
    public record Folded(ParsedDiff diff, Map<String, Fold> files) {

        /**
         * {@code diff} as git gives it, nothing of its files known.
         */
        public static Folded of(ParsedDiff diff) {
            return new Folded(diff, HashMap.empty());
        }

        /**
         * What is known of the file with the path {@code path}.
         */
        public Fold fold(String path) {
            return files.get(path).getOrElse(Fold.UNKNOWN);
        }
    }

    /**
     * What is known of a file of a diff: how many lines its new file has, once it was read whole, how far it was read
     * whole, and, when its read was {@link Read#CUT cut}, how many lines of its new file the read has: up to there,
     * what it leaves out can still be shown; and the version of it shown in place of its diff, if one is, with how many
     * digits the line numbers of that diff have, so the gutter of the version is as wide (ADR 102).
     */
    public record Fold(
            Option<Integer> length, Read read, Option<Integer> reach, Option<FileVersion> version, int digits) {

        public static final Fold UNKNOWN = new Fold(Option.none(), Read.NOT);

        /**
         * What is known of a file whose read, if any, was not cut.
         */
        public Fold(Option<Integer> length, Read read) {
            this(length, read, Option.none());
        }

        /**
         * What is known of a file shown as its diff.
         */
        public Fold(Option<Integer> length, Read read, Option<Integer> reach) {
            this(length, read, reach, Option.none(), 0);
        }

        /**
         * A file shown as {@code version}, read whole up to a cut if {@code cut}, in place of a diff whose line numbers
         * have {@code digits} digits: it leaves no line out.
         */
        public static Fold of(FileVersion version, boolean cut, int digits) {
            return new Fold(Option.none(), cut ? Read.CUT : Read.WHOLE, Option.none(), Option.of(version), digits);
        }

        /**
         * Whether the lines from {@code from} on are past where the read of the file was cut, so none of them can be
         * shown.
         */
        public boolean pastTheCut(int from) {
            return read == Read.CUT && reach.forAll(lines -> from > lines);
        }
    }

    /**
     * How far a file of a diff was read whole.
     */
    public enum Read {
        /**
         * Not, or not with the changes the diff shows, as when it was read before the file changed.
         */
        NOT,
        /**
         * It is being read.
         */
        READING,
        /**
         * Reading it failed.
         */
        FAILED,
        /**
         * It was read whole, and is folded.
         */
        WHOLE,
        /**
         * It was read up to the lines jazygit reads of a diff, its changes all before them, and is folded up to there.
         */
        CUT,
        /**
         * It was read up to the lines jazygit reads of a diff, and has changes after them: it cannot be folded.
         */
        TOO_LONG
    }

    private record FoldedFile(ParsedFile file, Fold fold) {}
}
