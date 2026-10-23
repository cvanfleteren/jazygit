package net.vanfleteren.jazygit.feature.branch.rename;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking for the new name of {@code branch}.
 *
 * @param name the new name as far as it was typed; it starts out as the name the branch has
 */
public record RenameBranchPopup(BranchName branch, String name) implements TextPopup {

    public RenameBranchPopup(BranchName branch) {
        this(branch, branch.value());
    }

    @Override
    public String text() {
        return name;
    }

    @Override
    public RenameBranchPopup withText(String text) {
        return new RenameBranchPopup(branch, text);
    }
}
