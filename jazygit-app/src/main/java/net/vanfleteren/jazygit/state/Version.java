package net.vanfleteren.jazygit.state;

import io.vavr.collection.Vector;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;

/**
 * Which version of its one file an area of the content panel shows in place of its diff (ADR 102): the new one, the
 * original, or none, the diff. It is kept while the area shows the same file, and dropped once another item is
 * highlighted.
 *
 * @param content what the area showed when the version was first asked for
 * @param path    the path of the file, the new one of a rename
 * @param shown   the version shown; none for the diff
 * @param read    how the file is read whole, with whitespace not ignored
 * @param whole   the file read whole
 * @param rendered whether a version shows rendered rather than as source (ADR 103); while the diff is shown, whether
 *                 the next version shown is
 */
@With
public record Version(
        ScrolledContent content,
        String path,
        Option<FileVersion> shown,
        ReadVersion read,
        Loadable<DiffText> whole,
        boolean rendered) {

    /**
     * A version shown as source.
     */
    public Version(
            ScrolledContent content,
            String path,
            Option<FileVersion> shown,
            ReadVersion read,
            Loadable<DiffText> whole) {
        this(content, path, shown, read, whole, false);
    }

    /**
     * The version kept for the area with the id {@code area} while it shows {@code content}, if it holds for that.
     */
    public static Option<Version> in(Model model, String area, ScrolledContent content) {
        return model.repoView().versions().get(area).filter(version -> version.holdsFor(content));
    }

    /**
     * Whether this holds for an area that shows {@code shown}: the same changes, read again or not, or for the working
     * tree another of its diffs, of this file only.
     */
    public boolean holdsFor(ScrolledContent shown) {
        return (content.sameChanges(shown)
                        || (content instanceof ScrolledContent.Diff && shown instanceof ScrolledContent.Diff))
                && shown.richDiff()
                        .map(diff -> diff.parsed().files())
                        .exists(files ->
                                files.size() == 1 && files.head().file().path().equals(path));
    }

    /**
     * The file read whole, once it is.
     */
    public Option<ParsedFile> file() {
        return whole.toOption()
                .flatMap(read ->
                        read.parsed().files().find(file -> file.file().path().equals(path)));
    }

    /**
     * Whether reading the file whole failed, or read no such file, as when git printed nothing: then there is no
     * version to show, and asking for one again reads it again.
     */
    public boolean failed() {
        return whole.isFailed() || (whole.toOption().isDefined() && file().isEmpty());
    }

    /**
     * The version shown, once the file is read whole; none while the diff is shown, or the file is still read.
     */
    public Option<FileVersion> showing() {
        return shown.filter(ignored -> file().isDefined());
    }

    /**
     * The version shown rendered (ADR 103), once the file is read whole; none while the diff or a version as source is
     * shown, or the file is still read.
     */
    public Option<FileVersion> rendering() {
        return showing().filter(ignored -> rendered);
    }

    /**
     * {@code folded} with this file as the version shown, if one is, and what is known of it: no line left out,
     * whether its read was cut, and whether it is rendered (ADR 103).
     */
    public Expansion.Folded applied(Expansion.Folded folded) {
        return showing()
                .flatMap(version -> file().map(whole -> new Expansion.Folded(
                        new ParsedDiff(
                                folded.diff().preamble(),
                                folded.diff()
                                        .files()
                                        .map(file -> file.file().path().equals(path) ? version.of(whole) : file)),
                        folded.files()
                                .put(
                                        path,
                                        Expansion.Fold.of(
                                                version,
                                                this.whole.toOption().exists(DiffText::truncated),
                                                folded.diff()
                                                        .files()
                                                        .find(file -> file.file()
                                                                .path()
                                                                .equals(path))
                                                        .map(Version::digits)
                                                        .getOrElse(1),
                                                rendered)))))
                .getOrElse(folded);
    }

    /**
     * How many digits the widest line number of {@code file}, old or new, has: what the gutter of its diff is made for.
     */
    static int digits(ParsedFile file) {
        return file.hunks()
                .flatMap(ParsedHunk::lines)
                .flatMap(line ->
                        line.line().oldNumber().toList().appendAll(line.line().newNumber()))
                .max()
                .map(number -> String.valueOf(number).length())
                .getOrElse(1);
    }

    /**
     * Where the cursor on {@code at} goes once {@code to} shows in place of what showed: a line of the file read whole
     * that is not in it to the next line that is, or to its last one when none follows; a marker of lines left out
     * as the line it starts at. What is no line stays, as does a line shown in both.
     */
    public Anchor moved(Anchor at, Option<FileVersion> to) {
        Anchor line = at instanceof Anchor.Gap(String gapPath, int from, var until) && gapPath.equals(path)
                ? new Anchor.OnLine(path, Side.RIGHT, from)
                : at;
        if (!(line instanceof Anchor.OnLine on) || !on.path().equals(path) || to.isEmpty()) {
            return at;
        }
        FileVersion version = to.get();
        return file().map(whole -> {
                    Vector<DiffLine> lines = whole.hunks()
                            .flatMap(ParsedHunk::lines)
                            .map(ParsedLine::line)
                            .toVector();
                    int index = lines.indexWhere(
                            diffLine -> Anchor.of(path, diffLine).contains(on));
                    if (index < 0) {
                        return at;
                    }
                    return lines.drop(index)
                            .find(version::has)
                            .orElse(() -> lines.take(index).findLast(version::has))
                            .flatMap(kept -> Anchor.of(path, kept))
                            .<Anchor>map(anchor -> anchor)
                            .getOrElse(at);
                })
                .getOrElse(at);
    }
}
