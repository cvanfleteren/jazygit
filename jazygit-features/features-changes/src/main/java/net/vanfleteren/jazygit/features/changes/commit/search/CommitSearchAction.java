package net.vanfleteren.jazygit.features.changes.commit.search;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Searching the commits by their message, author, the paths they change or the text they add or remove. The commits
 * pane takes the keys of the line the search is typed in, and Escape to clear it, itself.
 */
public final class CommitSearchAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys ->
            keys.add(Scopes.COMMITS, Binding.always("/", "changes.help.commits.search", CommitSearchMsg.Opened::new)));

    private CommitSearchAction() {}
}
