package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * A popup the checks of a pull request open from, and that Escape on them goes back to: the pull request dialog. The
 * checks name what they need of it, so they need not know the dialog.
 */
public interface OpensChecks extends Popup {

    /**
     * The branch on GitHub whose checks it opens, in {@code model}; none while it has none to show.
     */
    Option<RemoteBranch> checksBranch(Model model);
}
