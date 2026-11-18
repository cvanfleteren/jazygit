package net.vanfleteren.jazygit.state.diff;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.Blame;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedFile;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedHunk;
import net.vanfleteren.jazygit.git.model.ParsedDiff.ParsedLine;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.DiffLine;
import net.vanfleteren.jazygit.state.Loadable;

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
 * @param nodes    the nodes of a version of JSON rendered the user opened, true, or closed, false, by their paths; the
 *                 new version and the original share them, and a version read again keeps them (ADR 122)
 * @param blamed   whether a version shown as source shows who last changed each of its lines (ADR 147): the new one
 *                 and the original alike, rendered or not, until the area shows another file
 * @param blames   the blame of each version read, which a version read again drops
 */
@With
public record Version(
        ScrolledContent content,
        String path,
        Option<FileVersion> shown,
        WholeFile read,
        Loadable<DiffText> whole,
        boolean rendered,
        Map<String, Boolean> nodes,
        boolean blamed,
        Blames blames) {

    /**
     * A version shown rendered if {@code rendered}, with the nodes the user opened or closed as {@code nodes}, not
     * blamed.
     */
    public Version(
            ScrolledContent content,
            String path,
            Option<FileVersion> shown,
            WholeFile read,
            Loadable<DiffText> whole,
            boolean rendered,
            Map<String, Boolean> nodes) {
        this(content, path, shown, read, whole, rendered, nodes, false, Blames.none(false));
    }

    /**
     * A version shown rendered if {@code rendered}, none of its nodes opened or closed by the user.
     */
    public Version(
            ScrolledContent content,
            String path,
            Option<FileVersion> shown,
            WholeFile read,
            Loadable<DiffText> whole,
            boolean rendered) {
        this(content, path, shown, read, whole, rendered, HashMap.empty());
    }

    /**
     * A version shown as source.
     */
    public Version(
            ScrolledContent content, String path, Option<FileVersion> shown, WholeFile read, Loadable<DiffText> whole) {
        this(content, path, shown, read, whole, false);
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
     * The version shown blamed (ADR 147): shown as source, once the file is read whole, while it is blamed.
     */
    public Option<FileVersion> blaming() {
        return showing().filter(ignored -> blamed && !rendered);
    }

    /**
     * The blame of the version shown blamed: loading until it is read, as before its read is started.
     */
    public Option<Loadable<Blame>> blame() {
        return blaming().map(version -> blames.read().get(version).getOrElse(Loadable::loading));
    }

    /**
     * The version shown blamed whose blame is not read yet, nor being read, nor failed to be read.
     */
    public Option<FileVersion> unblamed() {
        return blaming().filter(version -> !blames.read().containsKey(version));
    }

    /**
     * This blamed, or not, as {@code on} says. Blamed, its blames read with whitespace not ignored as
     * {@code ignoreWhitespace} says are dropped, and the blame of the version shown if it failed, so that it is read
     * again.
     */
    public Version blamed(boolean on, boolean ignoreWhitespace) {
        if (!on) {
            return withBlamed(false);
        }
        Blames kept = blames.readIgnoring(ignoreWhitespace);
        return withBlamed(true)
                .withBlames(showing()
                        .filter(version -> kept.read().get(version).exists(Loadable::isFailed))
                        .map(kept::without)
                        .getOrElse(kept));
    }

    /**
     * The last line of the version shown to blame: that of a version cut where its read was cut (ADR 79), and none,
     * all of them, for one that is not.
     */
    public Option<Integer> lastLine() {
        return whole.toOption().exists(DiffText::truncated) ? text().map(Vector::size) : Option.none();
    }

    /**
     * The lines of the version shown, once the file is read whole: what a version rendered is rendered from.
     */
    public Option<Vector<String>> text() {
        return showing()
                .flatMap(version -> file().map(whole -> version.of(whole)
                        .hunks()
                        .flatMap(ParsedHunk::lines)
                        .map(ParsedLine::line)
                        .filter(line -> line.kind() != DiffLine.Kind.NOTE)
                        .map(DiffLine::text)
                        .toVector()));
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
                                                        rendered,
                                                        nodes)
                                                .withBlame(drawnBlame())))))
                .getOrElse(folded);
    }

    /**
     * The blame the version shown blamed is drawn with: none when it is not blamed, or its blame failed to be read, as
     * the title says.
     */
    public Option<Blamed> drawnBlame() {
        return blame().filter(read -> !read.isFailed())
                .map(read -> new Blamed(
                        read,
                        showing().contains(FileVersion.NEW)
                                ? own(this.read.source(), read.toOption())
                                : HashSet.empty()));
    }

    /**
     * The line of the blame of the version shown blamed that the cursor {@code on} is on, once the blame is read: none
     * on what is no line, as the bar of the file.
     */
    public Option<Blame.Line> blamedLine(Anchor on) {
        return blame().flatMap(Loadable::toOption)
                .flatMap(read -> showing()
                        .flatMap(shown -> file().flatMap(whole -> shown.of(whole)
                                .hunks()
                                .flatMap(ParsedHunk::lines)
                                .map(ParsedLine::line)
                                .find(line -> Anchor.of(path, line).exists(on::equals))
                                .flatMap(shown::number)))
                        .flatMap(read::at));
    }

    /**
     * The line of the blame the cursor {@code on} is on, if Enter opens the files of its commit (ADR 147): one blamed
     * on a commit, not one not committed yet, nor one of the commit whose version this is.
     */
    public Option<Blame.Line> opening(Anchor on) {
        return blamedLine(on)
                .filter(line -> line.commit().committed())
                .filter(line -> drawnBlame()
                        .forAll(blame -> !blame.own().contains(line.commit().sha())));
    }

    /**
     * The commits whose new version of a file read as {@code source} says is shown, with {@code blame}: a line blamed on
     * them is of the changes shown, and tinted as changed already (ADR 147). A commit, or a stash: the stash itself, its
     * index and its untracked files, the commits of its index and its untracked files told by the messages git gives
     * them, which it does not translate. None for the working tree, a branch, a pull request, or a file without changes.
     */
    static Set<Sha> own(WholeFile.Source source, Option<Blame> blame) {
        return switch (source) {
            case WholeFile.Source.OfCommit(Sha sha, var file) -> HashSet.of(sha);
            case WholeFile.Source.OfStash(Sha sha, var file) -> stash(sha, blame);
            case WholeFile.Source.OfFiles(Changes of, var file) ->
                switch (of) {
                    case Changes.OfCommit(Sha sha) -> HashSet.of(sha);
                    case Changes.OfTip(var branch, Sha sha) -> HashSet.of(sha);
                    case Changes.OfStash(Sha sha) -> stash(sha, blame);
                    case Changes.OfBranch _, Changes.OfPullRequest _ -> HashSet.empty();
                };
            case WholeFile.Source.WorkingTree _, WholeFile.Source.OnDisk _, WholeFile.Source.AtCommit _ ->
                HashSet.empty();
        };
    }

    private static Set<Sha> stash(Sha sha, Option<Blame> blame) {
        return HashSet.of(sha)
                .addAll(blame.toList()
                        .flatMap(Blame::lines)
                        .map(Blame.Line::commit)
                        .filter(commit -> commit.summary().startsWith("index on ")
                                || commit.summary().startsWith("untracked files on "))
                        .map(Blame.Commit::sha));
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
