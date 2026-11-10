package net.vanfleteren.jazygit.git.model;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.FileDiff;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;

/**
 * A version of a file a diff changed, shown in place of its diff (ADR 102): the new one, or the original. It is made
 * from the diff of the file read with the whole file as context, which holds both.
 */
public enum FileVersion {
    NEW,
    ORIGINAL;

    private static final String SUBPROJECT = "Subproject commit ";

    /**
     * Whether {@code line} is a line of this version: a line of context is in both, an added line in the new one, a
     * removed one in the original. A note is in none: {@link #of} keeps it with the line before it.
     */
    public boolean has(DiffLine line) {
        return switch (line.kind()) {
            case CONTEXT -> true;
            case ADDED -> this == NEW;
            case REMOVED -> this == ORIGINAL;
            case NOTE -> false;
        };
    }

    /**
     * The number of {@code line} in this version, unless it is not in it.
     */
    public Option<Integer> number(DiffLine line) {
        return this == NEW ? line.newNumber() : line.oldNumber();
    }

    /**
     * Whether a file changed as {@code type} says has this version: an added file has no original, a deleted one no
     * new version. An untracked file is an added one.
     */
    public boolean in(ChangeType type) {
        return this == NEW ? type != ChangeType.DELETED : type != ChangeType.ADDED && type != ChangeType.UNTRACKED;
    }

    /**
     * This version of {@code whole}, a file read with the whole file as context: its lines in this version as one hunk
     * without a header, a note after a line kept kept with it, each line with its words and tokens. A version without
     * lines has no hunk.
     */
    public ParsedFile of(ParsedFile whole) {
        List<ParsedLine> kept = whole.hunks()
                .flatMap(ParsedHunk::lines)
                .foldLeft(Tuple.of(List.<ParsedLine>empty(), false), (done, line) -> {
                    boolean note = line.line().kind() == DiffLine.Kind.NOTE;
                    boolean keeps = note ? done._2 : has(line.line());
                    return Tuple.of(keeps ? done._1.prepend(line) : done._1, keeps);
                })
                ._1
                .reverse();
        List<ParsedHunk> hunks =
                kept.isEmpty() ? List.empty() : List.of(new ParsedHunk(new Hunk("", kept.map(ParsedLine::line)), kept));
        FileDiff file = whole.file();
        return new ParsedFile(
                new FileDiff(
                        file.type(),
                        file.path(),
                        file.origin(),
                        file.mode(),
                        file.binary(),
                        hunks.map(ParsedHunk::hunk)),
                hunks);
    }

    /**
     * What a file changed as {@code type} says shows after {@code shown}, none being its diff: its diff, then its new
     * version, then its original, then its diff again, each version it does not have left out.
     */
    public static Option<FileVersion> after(Option<FileVersion> shown, ChangeType type) {
        List<Option<FileVersion>> cycle = List.of(Option.<FileVersion>none(), Option.some(NEW), Option.some(ORIGINAL))
                .filter(version -> version.forAll(one -> one.in(type)));
        return cycle.get((cycle.indexOf(shown) + 1) % cycle.size());
    }

    /**
     * Whether {@code file} has versions to show: lines of a file, numbered, in no conflict. A binary file, one whose
     * mode only changed or that was renamed without changes, a conflicted file and a submodule, whose diff is a
     * {@code Subproject commit} line for each side, have none.
     */
    public static boolean shown(FileDiff file) {
        List<DiffLine> lines = file.hunks().flatMap(Hunk::lines);
        return !file.binary()
                && file.type() != ChangeType.CONFLICTED
                && !file.hunks().isEmpty()
                && file.hunks().forAll(Hunk::numbered)
                && !lines.filter(line -> line.kind() != DiffLine.Kind.NOTE)
                        .forAll(line -> line.kind() != DiffLine.Kind.CONTEXT
                                && line.text().startsWith(SUBPROJECT));
    }
}
