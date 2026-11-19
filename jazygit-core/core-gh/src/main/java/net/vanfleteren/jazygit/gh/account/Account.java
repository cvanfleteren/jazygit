package net.vanfleteren.jazygit.gh.account;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The gh that runs, the accounts it is logged in to and the one it runs as, and everything it opens in the browser,
 * which is chosen by that account (ADR 85, 88, 114).
 */
public interface Account {

    /**
     * The version of the gh at {@code executable}, as {@code gh --version} prints it first.
     */
    Either<GhError, String> version(String executable);

    /**
     * The accounts gh is logged in to, on every host, whatever account the repository is configured to use.
     */
    Either<GhError, List<GhLogin>> logins();

    /**
     * The account gh runs as for the repository now, on the host its remotes give, an ssh alias resolved: the one it
     * is configured to use, else gh's active one; none when that is not known, as when a token in the environment
     * makes gh run as another (ADR 88).
     */
    Option<GhAccount> current();

    /**
     * The browser gh would open a link to {@code host} in, and what chose it; none when gh would leave it to the
     * system (ADR 85).
     */
    Option<ChosenBrowser> ghBrowser(String host);

    /**
     * Opens pull request {@code number} in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> openPullRequest(PrNumber number);

    /**
     * Opens the pull request at {@code url}, of any repository, in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> openPullRequest(String url);

    /**
     * Opens the pull request, or the review comment, {@code link} is to in the browser set for the account the
     * repository uses (ADR 88), else the one gh would open it in, started by jazygit, as gh drops the anchor of a link
     * (ADR 85). Without a browser set, on another system than Linux, gh opens the pull request, without the anchor;
     * so does it when the host is not known.
     */
    Either<GhFailure, List<ExecutedCommand>> openInBrowser(PullRequestLink link);

    /**
     * Opens {@code url}, a link to anywhere, in the browser set for the account the repository uses (ADR 88), else the
     * one gh would open a link to the repository's host in, else the one the desktop is set to, started by jazygit
     * (ADR 96). gh opens no link of its own choosing, so on a system without a desktop it knows it fails with
     * {@link GhError.NoBrowser}.
     */
    Either<GhFailure, List<ExecutedCommand>> openLink(String url);

    /**
     * Opens {@code branch}, as it is named on the remote, in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> browse(GhBranch branch);
}
