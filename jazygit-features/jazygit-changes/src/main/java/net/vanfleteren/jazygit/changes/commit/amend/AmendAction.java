package net.vanfleteren.jazygit.changes.commit.amend;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Amending the last commit, from the files, or the selected commit, from the commits.
 */
public final class AmendAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(Scopes.FILES, Binding.always("A", "changes.help.files.amend", AmendMsg.Requested::new))
                    .add(
                            Scopes.COMMITS,
                            Binding.onSelected(
                                    "A",
                                    "changes.help.commits.amend",
                                    commit -> new AmendMsg.Requested(Option.of(commit.sha())))),
            AmendDialogs::popups);

    private AmendAction() {}
}
