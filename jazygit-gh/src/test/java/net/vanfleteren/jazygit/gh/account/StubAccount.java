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
 * {@link Account} for tests: gh 2.102.0, logged in to no account, opening everything with the desktop's browser
 * without running anything. Fakes override what they need.
 */
public interface StubAccount extends Account {

    @Override
    default Either<GhError, String> version(String executable) {
        return Either.right("2.102.0");
    }

    /**
     * Logged in to no account.
     */
    @Override
    default Either<GhError, List<GhLogin>> logins() {
        return Either.right(List.empty());
    }

    @Override
    default Option<GhAccount> current() {
        return Option.none();
    }

    @Override
    default Option<ChosenBrowser> ghBrowser(String host) {
        return Option.of(new ChosenBrowser(Browser.XDG_OPEN, ChosenBrowser.Source.DESKTOP));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> openPullRequest(PrNumber number) {
        return Either.right(List.of(ExecutedCommand.of("gh", "pr", "view", String.valueOf(number), "--web")));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> openPullRequest(String url) {
        return Either.right(List.of(ExecutedCommand.of("gh", "pr", "view", url, "--web")));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> openInBrowser(PullRequestLink link) {
        return Either.right(List.of(ExecutedCommand.of(Browser.XDG_OPEN, link.url())));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> openLink(String url) {
        return Either.right(List.of(ExecutedCommand.of(Browser.XDG_OPEN, url)));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> browse(GhBranch branch) {
        return Either.right(List.of(ExecutedCommand.of("gh", "browse", "--branch", branch.name())));
    }
}
