package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * What the pull requests on Forgejo do (ADR 176): their list is read when Forgejo turns on and after a fetch, and the
 * pull request of a branch is shown from it, read again as the user asks, and opened in the browser.
 */
public final class PullRequestUpdate {

    private PullRequestUpdate() {}

    public static Next update(Model model, PullRequestMsg msg) {
        return switch (msg) {
            case PullRequestMsg.Listed(var open, var failure) ->
                Next.of(model.mapPart(ForgejoPullRequests.PART, prs -> prs.listed(open, failure)));
            case PullRequestMsg.Requested(BranchName branch) -> requested(model, branch);
            case PullRequestMsg.Read(long read, var result) -> Next.of(read(model, read, result));
            case PullRequestMsg.OpenChosen() -> openChosen(model);
            case PullRequestMsg.RefreshChosen() -> refreshChosen(model);
        };
    }

    /**
     * {@code model} reading the open pull requests again, as Forgejo turned on, after a fetch or a push.
     */
    public static Next list(Model model) {
        return Next.of(model, new ReadPullRequests());
    }

    /**
     * Shows the pull request of {@code branch}, when it is idle: the one that merges the branch it follows on a
     * remote on Forgejo. Without one, the user is told so; without the list, why it is not known.
     */
    private static Next requested(Model model, BranchName branch) {
        ForgejoPullRequests prs = model.part(ForgejoPullRequests.PART);
        return model.idleBranch(branch)
                .map(known -> known.remoteUpstream()
                        .map(upstream -> prs.of(known)
                                .map(pr -> show(model, upstream, pr))
                                .getOrElse(() -> unknown(model, known, prs)))
                        .getOrElse(() -> Next.of(model.inform(new PullRequestNotice.NotOnForgejo(branch)))))
                .getOrElse(() -> Next.of(model));
    }

    private static Next unknown(Model model, Branch branch, ForgejoPullRequests prs) {
        if (prs.open().isEmpty()) {
            return Next.of(model.fail(prs.failure()
                    .<PullRequestsError>map(PullRequestsError.Unavailable::new)
                    .getOrElse(PullRequestsError.NotRead::new)));
        }
        return Next.of(model.inform(new PullRequestNotice.None(branch.name())));
    }

    private static Next show(Model model, RemoteBranch upstream, ForgejoPullRequest pr) {
        Model given = model.mapPart(ForgejoPullRequests.PART, ForgejoPullRequests::givingOutRead);
        long read = given.part(ForgejoPullRequests.PART).reads();
        return PopupUpdate.ask(given, ForgejoPullRequestPopup.loading(upstream, pr, read))
                .and(new LoadPullRequest(upstream.remote(), pr.number(), read));
    }

    /**
     * {@code model} once read {@code read} brought {@code result}: the dialog that waits for it shows it; unchanged
     * when none does, as it was closed or read again since.
     */
    private static Model read(Model model, long read, Either<Failure, ShownPullRequest> result) {
        return model.popup(ForgejoPullRequestPopup.class)
                .map(popup -> model.openPopup(popup.with(result, read)))
                .getOrElse(model);
    }

    private static Next openChosen(Model model) {
        return model.popup(ForgejoPullRequestPopup.class)
                .map(popup -> OperationUpdate.start(model.clearError(), new OpenPullRequestCmd(popup.url())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Reads the pull request shown again, unless it is being read.
     */
    private static Next refreshChosen(Model model) {
        return model.popup(ForgejoPullRequestPopup.class)
                .filter(popup -> !popup.reading())
                .map(popup -> {
                    Model given = model.mapPart(ForgejoPullRequests.PART, ForgejoPullRequests::givingOutRead);
                    long read = given.part(ForgejoPullRequests.PART).reads();
                    return Next.of(
                            given.openPopup(popup.readingAgain(read)),
                            new LoadPullRequest(
                                    popup.branch().remote(), popup.pullRequest().number(), read));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Whether the pull request {@code popup} shows can be read again now.
     */
    static boolean refreshable(ForgejoPullRequestPopup popup) {
        return !popup.reading();
    }
}
