package net.vanfleteren.jazygit.kernel.state.edit;

import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * What a file is opened with (ADR 145, 206): git's editor while the user set none to open files, the one set, or the
 * one chosen of several. The feature that opens files opens it by this, and a dialog that tells what opening a file
 * runs, as the resolve dialog does (ADR 200), says it, so the two cannot differ.
 */
public sealed interface FileEditor {

    /**
     * What the editors set in {@code model} open a file with.
     */
    static FileEditor of(Model model) {
        Editors editors = model.preferences().settings().editors().files();
        if (editors.isEmpty()) {
            return new GitEditor();
        }
        return editors.all().size() == 1 ? new One(editors.all().head()) : new Several(editors);
    }

    /**
     * Git's editor, {@code git var GIT_EDITOR}, as no editor is set to open files.
     */
    record GitEditor() implements FileEditor {}

    /**
     * The one editor set to open files.
     */
    record One(EditorProgram editor) implements FileEditor {}

    /**
     * The editors set to open files, of which the user chooses one in a menu.
     */
    record Several(Editors editors) implements FileEditor {}
}
