package net.vanfleteren.jazygit.feature.github.show;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Showing the pull request of the selected branch, or the one selected on the pull requests screen.
 */
public final class ShowPullRequestAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> Scopes.PULL_REQUESTS.foldLeft(
                    keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected("o", "help.branches.pullRequest", PullRequestMsg.Requested::new)),
                    (added, scope) -> added.add(
                            scope, Binding.onSelected("o", "help.prs.show", PullRequestMsg.SummaryRequested::new))),
            PullRequestDialogs::popups);

    private ShowPullRequestAction() {}
}
