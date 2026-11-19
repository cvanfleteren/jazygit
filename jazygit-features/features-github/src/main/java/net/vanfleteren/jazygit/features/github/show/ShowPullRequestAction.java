package net.vanfleteren.jazygit.features.github.show;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Showing the pull request of the selected branch, or the one selected on the pull requests screen; or straight
 * the files the pull request of the selected branch changes.
 */
public final class ShowPullRequestAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> Scopes.PULL_REQUESTS.foldLeft(
                    keys.add(
                                    Scopes.BRANCHES,
                                    Binding.onSelected(
                                            "o", "github.help.branches.pullRequest", PullRequestMsg.Requested::new))
                            .add(
                                    Scopes.BRANCHES,
                                    Binding.onSelected(
                                            "F", "github.help.branches.files", PullRequestMsg.FilesRequested::new)),
                    (added, scope) -> added.add(
                                    scope,
                                    Binding.onSelected(
                                            "o", "github.help.prs.show", PullRequestMsg.SummaryRequested::new))
                            .add(
                                    scope,
                                    Binding.onSelected(
                                            "F", "github.help.prs.files", PullRequestMsg.SummaryFilesRequested::new))),
            PullRequestDialogs::popups);

    private ShowPullRequestAction() {}
}
