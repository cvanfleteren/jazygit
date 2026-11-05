package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Folding;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Cmd.ReadWholeFile.Source;
import net.vanfleteren.jazygit.state.Expansion;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.state.ScrolledContent;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Showing lines a diff leaves out around its hunks (ADR 77). The first expansion in a file reads it whole, as its diff
 * was read; the lines asked for show once it is, folded to the context of the moment.
 */
public final class ExpandUpdate {

    /**
     * How many lines {@code ]} and {@code [} show.
     */
    static final int STEP = 10;

    private ExpandUpdate() {}

    public static Next update(Model model, ExpandMsg msg) {
        return switch (msg) {
            case ExpandMsg.Asked(var at, var toward) ->
                at.cursor() instanceof Anchor.Gap gap
                        ? asked(model, at.area(), at.at().content(), gap, toward)
                        : Next.of(model);
        };
    }

    /**
     * Shows the lines {@code toward} says of those {@code gap} leaves out, in the area {@code area} that shows
     * {@code content}, reading the file whole first if it is not, or failed to be.
     */
    private static Next asked(
            Model model, String area, ScrolledContent content, Anchor.Gap gap, ExpandMsg.Toward toward) {
        Expansion expansion = model.repoView()
                .expanded()
                .get(area)
                .filter(kept -> kept.holdsFor(content))
                .getOrElse(() -> Expansion.of(content));
        Option<Expansion.Expanded> before = expansion.files().get(gap.path());
        boolean reads = before.forAll(file -> file.whole().isFailed());
        Option<Cmd.ReadWholeFile> read = reads
                ? source(model, content, gap.path())
                        .map(source -> new Cmd.ReadWholeFile(
                                area,
                                expansion.content(),
                                source,
                                gap.path(),
                                model.preferences().diffOptions()))
                : Option.none();
        if (reads && read.isEmpty()) {
            return Next.of(model);
        }
        Folding.Lines lines = lines(gap, toward);
        Expansion.Expanded expanded = before.map(file ->
                        read.fold(() -> file, again -> file.withRead(again).withWhole(Loadable.loading())))
                .getOrElse(() -> new Expansion.Expanded(read.get(), Loadable.loading(), List.empty()));
        Expansion.Expanded asked = expanded.withLines(expanded.lines().append(lines));
        return new Next(
                model.mapRepoView(view -> view.withExpanded(view.expanded()
                        .put(area, expansion.withFiles(expansion.files().put(gap.path(), asked))))),
                read.toList().map(Cmd.class::cast));
    }

    /**
     * The lines of the new file {@code toward} says of those {@code gap} leaves out. After the last hunk, how many
     * follow is not known, and both brackets show the next ones.
     */
    static Folding.Lines lines(Anchor.Gap gap, ExpandMsg.Toward toward) {
        int last = gap.until().map(until -> until - 1).getOrElse(Integer.MAX_VALUE);
        return switch (toward) {
            case DOWN -> new Folding.Lines(gap.from(), Math.min(last, gap.from() + STEP - 1));
            // After the last hunk there is no hunk below: the lines above it are the next ones.
            case UP ->
                gap.until().isEmpty()
                        ? new Folding.Lines(gap.from(), gap.from() + STEP - 1)
                        : new Folding.Lines(Math.max(gap.from(), last - STEP + 1), last);
            case ALL -> new Folding.Lines(gap.from(), last);
        };
    }

    /**
     * How the file with the path {@code path} of the diff {@code content} shows is read whole: as that diff was read.
     * None for what is no diff, or a file the diff does not have.
     */
    static Option<Source> source(Model model, ScrolledContent content, String path) {
        return switch (content) {
            case ScrolledContent.Diff(DiffText diff) ->
                model.repoView()
                        .fileDiff()
                        .map(Preview::shown)
                        .flatMap(shown -> shown.key()
                                .find(file -> file.path().equals(path))
                                .map(file -> new Source.WorkingTree(
                                        file,
                                        shown.value()
                                                .toOption()
                                                .exists(diffs -> diffs.staged().equals(diff)))));
            case ScrolledContent.CommitChanges(var commit, DiffText changes) ->
                changed(changes, path).map(file -> new Source.OfCommit(commit.sha(), file));
            case ScrolledContent.StashChanges(var sha, DiffText changes) ->
                changed(changes, path).map(file -> new Source.OfStash(sha, file));
            case ScrolledContent.FileChanges(var of, var files, DiffText diff) ->
                files.find(file -> file.path().equals(path))
                        .orElse(() -> changed(diff, path))
                        .map(file -> new Source.OfFiles(of, file));
            default -> Option.none();
        };
    }

    /**
     * The file with the path {@code path} as {@code diff} changed it.
     */
    private static Option<CommitFile> changed(DiffText diff, String path) {
        return diff.parsed()
                .files()
                .map(file -> file.file())
                .find(file -> file.path().equals(path))
                .map(file -> new CommitFile(file.path(), file.type(), file.origin()));
    }
}
