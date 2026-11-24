package net.vanfleteren.jazygit.features.changes.edit;

import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * What keeps a file from being opened in an editor.
 */
public sealed interface EditError extends AppError {

    /**
     * A file of the files of a branch or a pull request was asked to be opened, but the working tree is not of
     * {@code branch}: the file on disk is another branch's (ADR 192).
     *
     * @param branch the branch, as the user knows it: its name, or the number of a pull request whose branch is not
     *               known
     */
    record NotCheckedOut(String branch) implements EditError {

        @Override
        public Phrase phrase() {
            return Phrase.of("changes.error.edit.notCheckedOut", branch);
        }
    }
}
