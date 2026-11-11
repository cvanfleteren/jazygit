package net.vanfleteren.jazygit.feature.copy;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Version;
import net.vanfleteren.jazygit.state.operation.Notice;

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
     * What copies what the cursor {@code at} is on; none on what is neither a line nor the bar of a file, and none in
     * a version rendered, whose rows are no lines of the file (ADR 103).
     */
    static Option<CopyCmd> copy(Model model, DiffCursorAt at) {
        if (model.repoView()
                .version(at.area(), at.at().content())
                .flatMap(Version::rendering)
                .isDefined()) {
            return Option.none();
        }
        return switch (at.cursor()) {
            case Anchor.File(String path) -> Option.of(new CopyCmd(path, new Notice.PathCopied(path)));
            case Anchor.OnLine cursor ->
                model.diffLines(at, at.at().content().richDiff())
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
}
