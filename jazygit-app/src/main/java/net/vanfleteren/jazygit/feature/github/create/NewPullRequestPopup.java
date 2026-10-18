package net.vanfleteren.jazygit.feature.github.create;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking what the new pull request of {@code branch} into {@code base} says: its title, its body, and whether it
 * is a draft.
 *
 * @param head  the name the branch has on its remote, which gh needs
 * @param title the title it starts out with: that of the oldest commit not on {@code base}
 */
public record NewPullRequestPopup(Branch.Name branch, Branch.Name head, Branch.Name base, String title)
        implements Popup {}
