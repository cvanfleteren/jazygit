package net.vanfleteren.jazygit.feature.github;

import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.prs.PrOverviewMsg;

/**
 * The keys for working with pull requests.
 */
public final class PullRequestKeys {

    private PullRequestKeys() {}

    public static Keymap register(Keymap keys) {
        return keys.add(
                        Scopes.BRANCHES,
                        Binding.onSelected("o", "help.branches.pullRequest", PullRequestMsg.Requested::new))
                .add(
                        Scopes.PR_REPOSITORY,
                        Binding.onSelected("o", "help.prs.show", PullRequestMsg.SummaryRequested::new),
                        Binding.always("r", "help.prs.refresh", PrOverviewMsg.Refresh::new));
    }
}
