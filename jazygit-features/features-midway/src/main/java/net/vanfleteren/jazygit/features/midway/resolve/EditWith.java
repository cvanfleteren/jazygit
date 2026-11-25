package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.features.changes.edit.EditorSettings;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * What editing a file in conflict opens it with, as {@code e} does (ADR 145): for the resolve dialog to say the
 * command it runs (ADR 200).
 */
public sealed interface EditWith {

    /**
     * What editing the file at {@code path} opens it with in {@code model}: the one editor set to open files, a choice
     * of those when several are set, or git's editor when none is.
     */
    static EditWith of(Model model, String path) {
        Editors editors =
                model.preferences().settings().get(EditorSettings.EDITORS).files();
        if (editors.isEmpty()) {
            return new GitEditor();
        }
        return editors.all().size() == 1
                ? editors.all()
                        .head()
                        .fileCommandLine(".", path, 1)
                        .<EditWith>map(Program::new)
                        .getOrElse(GitEditor::new)
                : new Chosen();
    }

    /**
     * The one editor set to open files, by the command that opens the file at its first line, the worktree being
     * {@code .}.
     */
    record Program(List<String> commandLine) implements EditWith {}

    /**
     * One of the several editors set to open files, which the user chooses.
     */
    record Chosen() implements EditWith {}

    /**
     * Git's editor, {@code git var GIT_EDITOR}, as no editor is set to open files.
     */
    record GitEditor() implements EditWith {}
}
