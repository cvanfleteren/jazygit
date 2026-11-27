package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.forge.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPopup;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPort;
import net.vanfleteren.jazygit.features.forge.checks.ChecksRead;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.features.github.state.PendingChecks;
import net.vanfleteren.jazygit.kernel.state.Failure;

/**
 * The checks popup of GitHub, for the tests of the checks flow on GitHub.
 */
final class GitHubChecksFixtures {

    private GitHubChecksFixtures() {}

    /**
     * The checks popup of {@code branch}, whose checks are those of {@code onGitHub}, while they are first read from
     * {@code now}.
     */
    static ChecksPopup loading(BranchRef branch, RemoteBranch onGitHub, Instant now) {
        return ChecksPopup.loading(
                GitHubChecksPort.PORT, new ChecksPort.Opening(branch, onGitHub, Option.none(), Option.none()), now);
    }

    /**
     * Likewise, opened from the pull request dialog {@code from}.
     */
    static ChecksPopup loading(BranchRef branch, RemoteBranch onGitHub, Instant now, PullRequestPopup from) {
        return ChecksPopup.loading(
                GitHubChecksPort.PORT, new ChecksPort.Opening(branch, onGitHub, Option.none(), Option.of(from)), now);
    }

    /**
     * What the checks flow reads of the checks of {@code branch} among {@code pending}.
     */
    static Either<Failure, ChecksRead> readOf(RemoteBranch branch, PendingChecks pending) {
        return ((ChecksMsg.Read) LoadChecks.loaded(branch, pending)).read();
    }
}
