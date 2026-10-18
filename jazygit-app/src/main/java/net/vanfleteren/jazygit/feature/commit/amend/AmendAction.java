package net.vanfleteren.jazygit.feature.commit.amend;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.CommitAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Amending the last commit, from the files, or the selected commit, from the commits.
 */
public final class AmendAction {

    public static final CommitAction ACTION = new CommitAction(
            keys -> keys.add(Scopes.FILES, Binding.always("A", "help.files.amend", AmendMsg.Requested::new))
                    .add(
                            Scopes.COMMITS,
                            Binding.onSelected(
                                    "A",
                                    "help.commits.amend",
                                    commit -> new AmendMsg.Requested(Option.of(commit.sha())))),
            AmendDialogs::popups);

    private AmendAction() {}
}
