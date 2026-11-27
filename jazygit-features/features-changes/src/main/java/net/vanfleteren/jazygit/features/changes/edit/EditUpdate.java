package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.diff.DiffLines;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.edit.EditedFile;
import net.vanfleteren.jazygit.kernel.state.edit.FileEditor;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;

/**
 * Opening a file in an editor (ADR 145): in git's editor while the user set none to open files, at once in the one
 * set, else in the one chosen in a menu of them; as the user asks with {@code e}, or as another feature asks with
 * {@link KernelEvent.FileToOpen} (ADR 206).
 */
public final class EditUpdate {

    private EditUpdate() {}

    public static Next update(Model model, EditMsg msg) {
        return switch (msg) {
            case EditMsg.Requested(EditedFile file, Option<Phrase> title) ->
                inConflict(model, file)
                        .map(path -> Next.of(model.clearError(), new ReadFirstConflictCmd(path, title)))
                        .getOrElse(() -> open(model, file, 1, title));
            case EditMsg.ListedRequested(RepoPath path) ->
                model.repoView()
                        .files()
                        .commitFiles()
                        .map(view -> elsewhere(model, view.of())
                                .map(error -> Next.of(model.fail(error)))
                                .getOrElse(() -> open(model, EditedFile.of(path), 1)))
                        .getOrElse(() -> Next.of(model));
            case EditMsg.AtCursor(DiffCursorAt at) ->
                file(at.cursor())
                        .map(path -> open(model, EditedFile.of(path), line(model, at, at.cursor())))
                        .getOrElse(() -> Next.of(model));
            case EditMsg.FirstConflictRead(RepoPath path, Option<Integer> line, Option<Phrase> title) ->
                open(model, EditedFile.of(path), line.getOrElse(1), title);
            case EditMsg.Chosen(EditorProgram editor) ->
                model.popup(EditPopup.class)
                        .map(popup -> start(popup.back(model), editor, popup.file(), popup.line()))
                        .getOrElse(() -> Next.of(model));
            case EditMsg.Cancelled() ->
                Next.of(model.popup(EditPopup.class)
                        .map(popup -> popup.back(model))
                        .getOrElse(model));
        };
    }

    /**
     * A feature asked for a file to be opened (ADR 206): at the line it asked for, else as {@code e} opens it.
     */
    public static Next reacting(Model model, KernelEvent event) {
        return switch (event) {
            case KernelEvent.FileToOpen(EditedFile file, Option<Integer> line, Option<Phrase> title) ->
                line.map(at -> open(model, file, at, title))
                        .getOrElse(() -> update(model, new EditMsg.Requested(file, title)));
            case KernelEvent.LogRead _,
                    KernelEvent.OperationDone _,
                    KernelEvent.Fetched _,
                    KernelEvent.RepositoryOpened _,
                    KernelEvent.ScreenShown _,
                    KernelEvent.ChangesOpened _,
                    KernelEvent.Switched _,
                    KernelEvent.TabShown _ -> Next.of(model);
        };
    }

    /**
     * The path of {@code file} if it is in conflict, which opens at its first conflict, read first (ADR 201): only a
     * file of the working tree can be.
     */
    private static Option<RepoPath> inConflict(Model model, EditedFile file) {
        return switch (file) {
            case EditedFile.InWorktree(RepoPath path) ->
                Option.when(
                        model.repo()
                                .status()
                                .toOption()
                                .exists(status -> status.files()
                                        .exists(entry -> entry.path().equals(path)
                                                && entry.conflict().isDefined())),
                        path);
            case EditedFile.OnDisk _ -> Option.none();
        };
    }

    private static Next open(Model model, EditedFile file, int line) {
        return open(model, file, line, Option.none());
    }

    /**
     * Opens {@code file} at {@code line}: in git's editor without an editor set to open files, which
     * opens it at its start; else in the one set, or the one chosen of those in a menu titled {@code title}. The menu
     * goes over a popup that is open, which it goes back to, if that one is coverable; else the first editor opens it,
     * as the menu would take that popup away (ADR 206).
     */
    private static Next open(Model model, EditedFile file, int line, Option<Phrase> title) {
        return switch (FileEditor.of(model)) {
            case FileEditor.GitEditor _ ->
                OperationUpdate.start(model.clearError(), new EditCmd(List.of(file.shown())));
            case FileEditor.One(EditorProgram editor) -> start(model, editor, file, line);
            case FileEditor.Several(Editors editors)
            when model.popup().exists(popup -> !popup.coverable()) ->
                start(model, editors.all().head(), file, line);
            case FileEditor.Several(Editors editors) ->
                Next.of(model.clearError()
                        .openPopup(new EditPopup(
                                file, line, editors.lettered(), title, model.popup(), model.listCursor())));
        };
    }

    /**
     * Why the files of {@code changes} cannot be edited on disk in {@code model}, if they cannot: those of a branch or
     * a pull request only while its branch is checked out, as the file on disk is else another branch's; those of a
     * commit or a stash always, as a diff of them opens the file of the working tree too (ADR 145, 192).
     */
    static Option<EditError> elsewhere(Model model, Changes changes) {
        Option<BranchName> current = model.repo().currentBranch().map(Branch::name);
        return switch (changes) {
            case Changes.OfCommit _, Changes.OfStash _ -> Option.none();
            case Changes.OfBranch(BranchName branch, var _, var _) -> unless(current, branch);
            case Changes.OfTip(BranchName branch, var _) -> unless(current, branch);
            case Changes.OfPullRequest(var commits, var branch) ->
                branch.map(of -> unless(current, of))
                        .getOrElse(() -> Option.of(new EditError.NotCheckedOut(
                                "#" + commits.number().value())));
        };
    }

    /**
     * That {@code branch} is not checked out, unless it is the {@code current} one.
     */
    private static Option<EditError> unless(Option<BranchName> current, BranchName branch) {
        return current.contains(branch) ? Option.none() : Option.of(new EditError.NotCheckedOut(branch.value()));
    }

    private static Next start(Model model, EditorProgram editor, EditedFile file, int line) {
        return OperationUpdate.start(model.clearError(), new OpenFileCmd(editor, file, line));
    }

    /**
     * The file the stop {@code cursor} of a rich diff is in; none before the first file, or on a thread that is on no
     * line.
     */
    static Option<RepoPath> file(Anchor cursor) {
        return switch (cursor) {
            case Anchor.Lead lead -> Option.none();
            case Anchor.File file -> Option.of(file.path());
            case Anchor.OnLine on -> Option.of(on.path());
            case Anchor.Gap gap -> Option.of(gap.path());
            case Anchor.Filtered filtered -> Option.of(filtered.path());
            case Anchor.Node node -> Option.of(node.path());
            case Anchor.Thread thread -> thread.line().map(Anchor.OnLine::path);
            case Anchor.Remark comment -> file(comment.thread());
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
            case Anchor.Remark comment -> line(model, at, comment.thread());
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
    private static Vector<Integer> numbers(Vector<DiffLines.Line> lines, RepoPath path) {
        return lines.filter(line -> line.anchor().path().equals(path))
                .flatMap(line -> line.line().newNumber());
    }
}
