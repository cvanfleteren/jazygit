package net.vanfleteren.jazygit.github.show;

import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Showing the pull request of the selected branch, or the one selected on the pull requests screen; or straight
 * the files the pull request of the selected branch changes.
 */
public final class ShowPullRequestAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> Scopes.PULL_REQUESTS.foldLeft(
                    keys.add(
                                    Scopes.BRANCHES,
                                    Binding.onSelected("o", "help.branches.pullRequest", PullRequestMsg.Requested::new))
                            .add(
                                    Scopes.BRANCHES,
                                    Binding.onSelected("F", "help.branches.files", PullRequestMsg.FilesRequested::new)),
                    (added, scope) -> added.add(
                                    scope,
                                    Binding.onSelected("o", "help.prs.show", PullRequestMsg.SummaryRequested::new))
                            .add(
                                    scope,
                                    Binding.onSelected(
                                            "F", "help.prs.files", PullRequestMsg.SummaryFilesRequested::new))),
            PullRequestDialogs::popups);

    private ShowPullRequestAction() {}
}
