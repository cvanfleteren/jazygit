package net.vanfleteren.jazygit.feature.commit.search;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.CommitSearchMsg;

/**
 * Searching the commits by their message, author, the paths they change or the text they add or remove. The commits
 * pane takes the keys of the line the search is typed in, and Escape to clear it, itself.
 */
public final class CommitSearchAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(
            keys -> keys.add(Scopes.COMMITS, Binding.always("/", "help.commits.search", CommitSearchMsg.Opened::new)));

    private CommitSearchAction() {}
}
