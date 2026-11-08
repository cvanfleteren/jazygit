package net.vanfleteren.jazygit.feature.copy;

import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.DiffCursorAt;
import net.vanfleteren.jazygit.state.DiffLines;
import net.vanfleteren.jazygit.state.Expansion;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Notice;
import net.vanfleteren.jazygit.state.ScrolledContent;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Copying what the cursor of a rich diff is on (ADR 94): its line, the lines of its range, or the path of the file
 * whose bar it is on.
 */
public final class CopyUpdate {

    private CopyUpdate() {}

    public static Next update(Model model, CopyMsg msg) {
        return switch (msg) {
            case CopyMsg.Asked(DiffCursorAt at) ->
                copy(model, at).map(cmd -> new Next(model, List.of(cmd))).getOrElse(() -> Next.of(model));
            case CopyMsg.Done(Notice notice) -> Next.of(model.inform(notice));
        };
    }

    /**
     * What copies what the cursor {@code at} is on; none on what is neither a line nor the bar of a file.
     */
    static Option<CopyCmd> copy(Model model, DiffCursorAt at) {
        return switch (at.cursor()) {
            case Anchor.File(String path) -> Option.of(new CopyCmd(path, new Notice.PathCopied(path)));
            case Anchor.OnLine cursor ->
                lines(model, at)
                        .between(from(at, cursor), cursor)
                        .map(range -> new CopyCmd(
                                range.map(line -> line.line().text()).mkString("\n"), new Notice.Copied(range.size())));
            default -> Option.none();
        };
    }

    /**
     * Where the lines copied start: at the mark of an open range, else at the line of the cursor.
     */
    private static Anchor.OnLine from(DiffCursorAt at, Anchor.OnLine cursor) {
        return at.mark()
                .filter(Anchor.OnLine.class::isInstance)
                .map(Anchor.OnLine.class::cast)
                .getOrElse(cursor);
    }

    /**
     * The lines the cursor {@code at} stops on in the diff its area shows, with the lines expanded in that area
     * (ADR 77); none for what is no diff.
     */
    private static DiffLines lines(Model model, DiffCursorAt at) {
        ScrolledContent content = at.at().content();
        Option<Expansion> expansion = model.repoView().expanded().get(at.area()).filter(kept -> kept.holdsFor(content));
        return diff(content)
                .map(text -> expansion
                        .map(expanded -> expanded.folded(
                                        text.parsed(), model.preferences().context())
                                .diff())
                        .getOrElse(text.parsed()))
                .map(parsed -> DiffLines.of(parsed.diff()))
                .getOrElse(() -> new DiffLines(Vector.empty()));
    }

    /**
     * The diff {@code content} shows, if it is one the rich view draws.
     */
    private static Option<DiffText> diff(ScrolledContent content) {
        return switch (content) {
            case ScrolledContent.Diff(DiffText diff) -> Option.of(diff);
            case ScrolledContent.CommitChanges(var commit, DiffText changes) -> Option.of(changes);
            case ScrolledContent.StashChanges(var sha, DiffText changes) -> Option.of(changes);
            case ScrolledContent.FileChanges(var of, var files, DiffText diff) -> Option.of(diff);
            case ScrolledContent.BranchLog _, ScrolledContent.PullRequest _ -> Option.none();
        };
    }
}
