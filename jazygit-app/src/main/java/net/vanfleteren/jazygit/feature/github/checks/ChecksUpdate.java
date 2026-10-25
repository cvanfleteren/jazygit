package net.vanfleteren.jazygit.feature.github.checks;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.CheckTimesUpdate;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.PendingChecks;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Showing the checks on the tip of a branch one by one, with how long they took or run, and reading them again by
 * hand and every minute, as the pull request dialog does (ADR 19, ADR 32).
 */
public final class ChecksUpdate {

    private ChecksUpdate() {}

    public static Next update(Model model, ChecksMsg msg) {
        return switch (msg) {
            case ChecksMsg.Requested(BranchRef branch) ->
                onGitHub(model, branch)
                        .map(remote -> Next.of(
                                Update.ask(model, ChecksPopup.loading(branch, remote))
                                        .model(),
                                new LoadChecks(remote)))
                        .getOrElse(() -> Next.of(model));
            // How long checks take is learned from them too.
            case ChecksMsg.Loaded(RemoteBranch branch, PendingChecks checks) ->
                CheckTimesUpdate.read(
                        Next.of(shown(model, branch, Either.right(checks))),
                        checks.repository(),
                        checks.of(branch.branch()));
            case ChecksMsg.Failed(RemoteBranch branch, Failure failure) ->
                Next.of(shown(model, branch, Either.left(failure)));
            case ChecksMsg.RefreshChosen(), ChecksMsg.Tick() ->
                model.popup(ChecksPopup.class)
                        .filter(ChecksPopup::refreshable)
                        .map(popup -> Next.of(model.openPopup(popup.readingAgain()), new LoadChecks(popup.onGitHub())))
                        .getOrElse(() -> Next.of(model));
            case ChecksMsg.Closed() -> Next.of(model.withoutPopup(ChecksPopup.class));
        };
    }

    /**
     * The branch on GitHub whose checks {@code branch} shows: itself when remote, else the one it follows; none when
     * that is not on GitHub, or has no checks.
     */
    private static Option<RemoteBranch> onGitHub(Model model, BranchRef branch) {
        return (switch (branch) {
                    case BranchRef.Local(BranchName name) ->
                        model.repo()
                                .branches()
                                .toOption()
                                .flatMap(branches ->
                                        branches.find(local -> local.name().equals(name)))
                                .flatMap(Branch::remoteUpstream);
                    case BranchRef.Remote(RemoteBranch remote) -> Option.of(remote);
                })
                .filter(remote -> model.gitHub().checks(remote).exists(state -> state != Checks.NONE));
    }

    /**
     * {@code model} showing what was read of the checks on {@code branch}; unchanged when they are no longer shown.
     */
    private static Model shown(Model model, RemoteBranch branch, Either<Failure, PendingChecks> read) {
        return model.popup(ChecksPopup.class)
                .filter(popup -> popup.onGitHub().equals(branch))
                .map(popup -> model.openPopup(popup.with(read)))
                .getOrElse(model);
    }
}
