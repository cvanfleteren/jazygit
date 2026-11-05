package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.DiffContext;
import net.vanfleteren.jazygit.git.model.DiffText;
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
                .map(expanded -> expanded.whole()
                        .toOption()
                        .flatMap(whole -> whole.parsed()
                                .files()
                                .find(read -> read.file().path().equals(path)))
                        .filter(whole -> Folding.agrees(whole, file))
                        .map(whole -> new FoldedFile(
                                Folding.fold(whole, context.lines(), expanded.lines()),
                                new Fold(Option.of(Folding.length(whole)), false, false)))
                        .getOrElse(() -> new FoldedFile(
                                file,
                                new Fold(
                                        Option.none(),
                                        expanded.whole() instanceof Loadable.Loading,
                                        expanded.whole().isFailed()))))
                .getOrElse(() -> new FoldedFile(file, Fold.UNKNOWN));
    }

    /**
     * What is expanded in a file.
     *
     * @param read  how the file is read whole, with the whole file as context
     * @param whole the file read whole
     * @param lines the lines of the new file expanded, in the order they were
     */
    @With
    public record Expanded(Cmd.ReadWholeFile read, Loadable<DiffText> whole, List<Folding.Lines> lines) {}

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
     * What is known of a file of a diff: how many lines its new file has, once it was read whole, and whether it is
     * being read whole, or failed to be.
     */
    public record Fold(Option<Integer> length, boolean reading, boolean failed) {

        public static final Fold UNKNOWN = new Fold(Option.none(), false, false);
    }

    private record FoldedFile(ParsedFile file, Fold fold) {}
}
