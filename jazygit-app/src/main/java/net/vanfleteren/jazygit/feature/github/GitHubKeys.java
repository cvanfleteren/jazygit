package net.vanfleteren.jazygit.feature.github;

import net.vanfleteren.jazygit.feature.github.checkout.CheckoutPullRequestMsg;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.prs.PrOverviewMsg;

/**
 * The keys for working with pull requests.
 */
public final class GitHubKeys {

    private GitHubKeys() {}

    public static Keymap register(Keymap keys) {
        return Scopes.PULL_REQUESTS.foldLeft(
                keys.add(
                        Scopes.BRANCHES,
                        Binding.onSelected("o", "help.branches.pullRequest", PullRequestMsg.Requested::new)),
                (added, scope) -> added.add(
                        scope,
                        Binding.onSelected("o", "help.prs.show", PullRequestMsg.SummaryRequested::new),
                        Binding.onSelected(" ", "help.prs.checkout", CheckoutPullRequestMsg.Requested::new),
                        Binding.always("r", "help.prs.refresh", PrOverviewMsg.Refresh::new)));
    }
}
