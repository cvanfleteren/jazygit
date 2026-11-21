package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.diff.DiffLines;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;

/**
 * Opening a file in an editor (ADR 145): in git's editor while the user set none to open files, at once in the one
 * set, else in the one chosen in a menu of them.
 */
public final class EditUpdate {

    private EditUpdate() {}

    public static Next update(Model model, EditMsg msg) {
        return switch (msg) {
            case EditMsg.Requested(String path) -> open(model, path, 1);
            case EditMsg.AtCursor(DiffCursorAt at) ->
                file(at.cursor())
                        .map(path -> open(model, path, line(model, at, at.cursor())))
                        .getOrElse(() -> Next.of(model));
            case EditMsg.Chosen(EditorProgram editor) ->
                model.popup(EditPopup.class)
                        .map(popup -> start(model.withoutPopup(EditPopup.class), editor, popup.path(), popup.line()))
                        .getOrElse(() -> Next.of(model));
        };
    }

    /**
     * Opens the file at {@code path} at {@code line}: in git's editor without an editor set to open files, which
     * opens it at its start; else in the one set, or the one chosen of those.
     */
    private static Next open(Model model, String path, int line) {
        Editors editors =
                model.preferences().settings().get(EditorSettings.EDITORS).files();
        if (editors.isEmpty()) {
            return OperationUpdate.start(model.clearError(), new EditCmd(List.of(path)));
        }
        return editors.all().size() == 1
                ? start(model, editors.all().head(), path, line)
                : Next.of(model.clearError().openPopup(new EditPopup(path, line, editors.lettered())));
    }

    private static Next start(Model model, EditorProgram editor, String path, int line) {
        return OperationUpdate.start(model.clearError(), new OpenFileCmd(editor, path, line));
    }

    /**
     * The file the stop {@code cursor} of a rich diff is in; none before the first file, or on a thread that is on no
     * line.
     */
    static Option<String> file(Anchor cursor) {
        return switch (cursor) {
            case Anchor.Lead lead -> Option.none();
            case Anchor.File file -> Option.of(file.path());
            case Anchor.OnLine on -> Option.of(on.path());
            case Anchor.Gap gap -> Option.of(gap.path());
            case Anchor.Filtered filtered -> Option.of(filtered.path());
            case Anchor.Node node -> Option.of(node.path());
            case Anchor.Thread thread -> thread.line().map(Anchor.OnLine::path);
            case Anchor.Comment comment -> file(comment.thread());
        };
    }

    /**
     * The line of the new file the stop {@code cursor} is on, counted from 1, in the diff the cursor {@code at} is
     * in: that of a line of the new file; for a removed line, where it was; the first line a marker leaves out, or
     * the first a marker of filtered hunks hides; the first line of a file whose bar it is on.
     */
    private static int line(Model model, DiffCursorAt at, Anchor cursor) {
        return switch (cursor) {
            case Anchor.OnLine on when on.side() == DiffSide.NEW -> on.number();
            case Anchor.OnLine on ->
                removed(model.diffLines(at, at.at().content().richDiff()), on);
            case Anchor.Gap gap -> gap.from();
            case Anchor.Filtered filtered ->
                filtered.starts().headOption().map(start -> start.newLine()).getOrElse(1);
            case Anchor.Node node -> line(model, at, node.line());
            case Anchor.Thread thread ->
                thread.line().map(on -> line(model, at, on)).getOrElse(1);
            case Anchor.Comment comment -> line(model, at, comment.thread());
            case Anchor.Lead lead -> 1;
            case Anchor.File file -> 1;
        };
    }

    /**
     * Where the line {@code on} of the old file is in the new one: its own number there, as a context line on the left
     * of the split view has (ADR 139); for a removed line, the first line of the new file after it in its file, else
     * the last one before it, else the first.
     */
    private static int removed(DiffLines diff, Anchor.OnLine on) {
        Vector<DiffLines.Line> lines = diff.lines();
        int at = lines.indexWhere(line -> line.answers(on));
        if (at < 0) {
            return 1;
        }
        Vector<Integer> after = numbers(lines.drop(at), on.path());
        Vector<Integer> before = numbers(lines.take(at), on.path());
        return after.headOption().orElse(before::lastOption).getOrElse(1);
    }

    /**
     * The numbers in the new file of those of {@code lines} that are of the file with the path {@code path}.
     */
    private static Vector<Integer> numbers(Vector<DiffLines.Line> lines, String path) {
        return lines.filter(line -> line.anchor().path().equals(path))
                .flatMap(line -> line.line().newNumber());
    }
}
