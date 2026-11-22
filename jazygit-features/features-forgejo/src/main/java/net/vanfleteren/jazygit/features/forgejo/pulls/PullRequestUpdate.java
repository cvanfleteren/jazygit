package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeHome;
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
            // A list of a home that is no longer it, as one read while the user chose another, is left out.
            case PullRequestMsg.Listed(var home, var open, var failure) ->
                Next.of(
                        ForgeHome.of(model).contains(home)
                                ? model.mapPart(ForgejoPullRequests.PART, prs -> prs.listed(home, open, failure))
                                : model);
            case PullRequestMsg.Requested(BranchName branch) -> requested(model, branch);
            case PullRequestMsg.Read(long read, var result) -> Next.of(read(model, read, result));
            case PullRequestMsg.OpenChosen() -> openChosen(model);
            case PullRequestMsg.RefreshChosen() -> refreshChosen(model);
            case PullRequestMsg.MergeChosen(var style) -> MergeUpdate.chosen(model, style);
            case PullRequestMsg.Merged(var cmd, var merged) -> MergeUpdate.merged(model, cmd, merged);
            case PullRequestMsg.MergeFailed(var cmd, var failure) -> MergeUpdate.failed(model, cmd, failure);
            case PullRequestMsg.AlreadyScheduled(var cmd) -> MergeUpdate.alreadyScheduled(model, cmd);
            case PullRequestMsg.MergeCancelled(var cmd) -> MergeUpdate.cancelled(model, cmd, false);
            case PullRequestMsg.NoLongerScheduled(var cmd) -> MergeUpdate.cancelled(model, cmd, true);
            // A warning, in the command log: the user chose the remote (ADR 179). It comes late, so it does not take
            // the place of what an operation left there, an error or a notice; then it is not given.
            case PullRequestMsg.PlainHttpChecked(var base) ->
                Next.of(base.filter(over -> model.outcome().error().isEmpty()
                                && model.outcome().notice().isEmpty())
                        .map(over -> model.inform(new PlainHttpNotice(over)))
                        .getOrElse(model));
        };
    }

    /**
     * {@code model} reading the open pull requests again, as Forgejo turned on, after a fetch or a push.
     */
    public static Next list(Model model) {
        return ForgeHome.of(model)
                .map(home -> Next.of(model, new ReadPullRequests(home)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code model} once Forgejo turned on for the repository opened: its pull requests read afresh, as the
     * repository may be another one, with other remotes under the same names, and whether fj's token goes to the home
     * unencrypted told, once for the opening (ADR 179).
     */
    public static Next turnedOn(Model model) {
        Model afresh = model.withPart(ForgejoPullRequests.PART, ForgejoPullRequests.NONE);
        return ForgeHome.of(afresh)
                .map(home -> Next.of(afresh, new ReadPullRequests(home), new CheckPlainHttp(home)))
                .getOrElse(() -> Next.of(afresh));
    }

    /**
     * Shows the pull request of {@code branch}, when it is idle: the one of the home that merges the branch it is
     * pushed to (ADR 177). Without one, one is created (ADR 178); without the list of the home, why it is not known,
     * and it is read again only when the list is of another home.
     */
    private static Next requested(Model model, BranchName branch) {
        ForgejoPullRequests prs = model.part(ForgejoPullRequests.PART);
        return model.idleBranch(branch)
                .flatMap(known -> ForgeHome.of(model)
                        .map(home -> prs.of(known, home)
                                .map(pr -> show(model, known.name(), home, known.pushTarget(), pr))
                                .getOrElse(() -> unknown(model, known, prs, home))))
                .getOrElse(() -> Next.of(model));
    }

    private static Next unknown(Model model, Branch branch, ForgejoPullRequests prs, RemoteName home) {
        if (prs.read(home)) {
            return CreatePullRequestUpdate.start(model, branch, home);
        }
        Model failed = model.fail(prs.failure()
                .filter(failure -> prs.home().contains(home))
                .<PullRequestsError>map(PullRequestsError.Unavailable::new)
                .getOrElse(PullRequestsError.NotRead::new));
        // Without a list yet, the one of the home is being read, as Forgejo turned on or the home changed; only a list
        // of another home, which a change of the home sets aside first, would be read again here.
        return prs.home().exists(other -> !other.equals(home))
                ? Next.of(failed, new ReadPullRequests(home))
                : Next.of(failed);
    }

    private static Next show(Model model, BranchName local, RemoteName home, RemoteBranch head, ForgejoPullRequest pr) {
        Model given = model.mapPart(ForgejoPullRequests.PART, ForgejoPullRequests::givingOutRead);
        long read = given.part(ForgejoPullRequests.PART).reads();
        return PopupUpdate.ask(given, ForgejoPullRequestPopup.loading(local, home, head, pr, read))
                .and(new LoadPullRequest(home, pr.number(), read));
    }

    /**
     * {@code model} once read {@code read} brought {@code result}: the dialog that waits for it shows it, as what is
     * kept of a merge scheduled says (ADR 181), which keeps what it shows; unchanged when none does, as it was closed
     * or read again since.
     */
    private static Model read(Model model, long read, Either<Failure, ShownPullRequest> result) {
        ForgejoPullRequests prs = model.part(ForgejoPullRequests.PART);
        Either<Failure, ShownPullRequest> seen = result.map(prs::shown);
        return model.popup(ForgejoPullRequestPopup.class)
                .filter(popup -> popup.read() == read)
                .map(popup -> model.withPart(ForgejoPullRequests.PART, seen.fold(_ -> prs, prs::read))
                        .openPopup(popup.with(seen, read)))
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
                .map(popup -> readAgain(model, popup))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Reads pull request {@code number} again when the dialog shows it, as after its merge was scheduled or cancelled
     * (ADR 181): also while it is read, as that read may have started before; the dialog then waits for this one.
     */
    static Next readAgain(Next next, long number) {
        return next.then(model -> model.popup(ForgejoPullRequestPopup.class)
                .filter(popup -> popup.pullRequest().number() == number)
                .map(popup -> readAgain(model, popup))
                .getOrElse(() -> Next.of(model)));
    }

    private static Next readAgain(Model model, ForgejoPullRequestPopup popup) {
        Model given = model.mapPart(ForgejoPullRequests.PART, ForgejoPullRequests::givingOutRead);
        long read = given.part(ForgejoPullRequests.PART).reads();
        return Next.of(
                given.openPopup(popup.readingAgain(read)),
                new LoadPullRequest(popup.home(), popup.pullRequest().number(), read));
    }

    /**
     * Whether the pull request {@code popup} shows can be read again now.
     */
    static boolean refreshable(ForgejoPullRequestPopup popup) {
        return !popup.reading();
    }
}
