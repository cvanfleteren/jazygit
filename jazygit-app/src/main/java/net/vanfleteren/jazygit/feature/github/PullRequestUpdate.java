package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.fetch.FetchUpdate;
import net.vanfleteren.jazygit.feature.github.PullRequestCmd.*;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.prs.PrOverviewUpdate;

/**
 * Creating, opening and showing the pull requests of the branches.
 */
public final class PullRequestUpdate {

    private PullRequestUpdate() {}

    public static Next update(Model model, PullRequestMsg msg) {
        return switch (msg) {
            case PullRequestMsg.Requested(Branch.Name branch) -> requested(model, branch);
            // A pull request of another repository is opened in the browser, as gh acts on this one.
            case PullRequestMsg.SummaryRequested(var pullRequest) ->
                model.prOverview().ofThisRepository(pullRequest)
                        ? show(model, new Branch.Name(pullRequest.headBranch()), pullRequest.pullRequest())
                        : Update.start(model.clearError(), new BrowsePullRequestCmd(pullRequest));
            case PullRequestMsg.BaseChosen(Branch.Name base) ->
                model.popup(BasePopup.class)
                        .flatMap(popup -> model.branch(popup.branch()))
                        // The branch can have another name on its remote; gh needs that one.
                        .map(known -> Next.of(
                                model.withoutPopup(BasePopup.class),
                                new ReadTitle(known.name(), known.pushTarget().branch(), base, baseRef(model, base))))
                        .getOrElse(() -> Next.of(model.withoutPopup(BasePopup.class)));
            case PullRequestMsg.BaseCancelled() -> Next.of(model.withoutPopup(BasePopup.class));
            // Without a commit of its own to name it after, it is named after the branch, as gh would.
            case PullRequestMsg.TitleRead(var branch, var head, var base, var subject) ->
                Next.of(model.openPopupIfFree(
                        new NewPullRequestPopup(branch, head, base, subject.getOrElse(branch::value))));
            // A blank title keeps the popup open, as GitHub needs one.
            case PullRequestMsg.CreateConfirmed(NewPullRequest pullRequest) ->
                pullRequest.title().isBlank()
                        ? Next.of(model)
                        : Update.confirm(
                                model,
                                NewPullRequestPopup.class,
                                popup -> model.branch(popup.branch())
                                        .map(known -> new CreatePullRequest(
                                                popup.branch(),
                                                popup.head(),
                                                popup.base(),
                                                new NewPullRequest(
                                                        pullRequest.title().strip(),
                                                        pullRequest.body(),
                                                        pullRequest.draft()))));
            case PullRequestMsg.CreateCancelled() -> Next.of(model.withoutPopup(NewPullRequestPopup.class));
            case PullRequestMsg.Created(CreatePullRequest cmd, var pullRequest, var commands) -> {
                // Done like any operation. The new pull request shows at once, as GitHub may not list it yet;
                // reading the pull requests then gives its checks and review.
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                Model shown = pullRequest
                        .map(pr -> done.model()
                                .withPullRequests(done.model()
                                        .pullRequests()
                                        .map(open -> open.put(new Branch.Name(pr.headBranch()), pr))))
                        .getOrElse(done.model());
                // A read that is still running may have missed it, so this one is started anyway. GitHub may have
                // changed the remote too, which a fetch brings in.
                yield FetchUpdate.requested(PullRequestsUpdate.loadAnyway(new Next(shown, done.cmds())));
            }
            case PullRequestMsg.CheckoutRequested(var pullRequest) -> checkout(model, pullRequest);
            case PullRequestMsg.DetailsLoaded(int number, var details) ->
                Next.of(shown(model, number, Either.right(details)));
            case PullRequestMsg.DetailsFailed(int number, var failure) ->
                Next.of(shown(model, number, Either.left(failure)));
            // The dialog stays open, so the pull request can still be merged or closed from it.
            case PullRequestMsg.OpenConfirmed() ->
                model.popup(PullRequestPopup.class)
                        .map(popup -> Update.start(
                                model.clearError(),
                                new OpenPullRequest(
                                        popup.branch(), popup.pullRequest().number())))
                        .getOrElse(() -> Next.of(model));
            case PullRequestMsg.MergeChosen(MergeMethod method) ->
                Update.confirm(
                        model,
                        PullRequestPopup.class,
                        popup -> Option.when(
                                popup.mergeable(),
                                () -> new MergePullRequest(
                                        popup.branch(), popup.pullRequest().number(), method)));
            case PullRequestMsg.CloseChosen() ->
                Update.confirm(
                        model,
                        PullRequestPopup.class,
                        popup -> Option.when(
                                popup.open(),
                                () -> new ClosePullRequest(
                                        popup.branch(), popup.pullRequest().number())));
            // The dialog stays open, to show the pull request as it now is.
            case PullRequestMsg.DraftChosen() ->
                model.popup(PullRequestPopup.class)
                        .filter(popup -> draftChangeable(model, popup))
                        .flatMap(popup -> popup.draft()
                                .map(draft -> Update.start(
                                        model.clearError(),
                                        new SetDraft(
                                                popup.branch(),
                                                popup.pullRequest().number(),
                                                !draft))))
                        .getOrElse(() -> Next.of(model));
            // Done like any operation, which reads the pull requests again. The dialog shows the change at once.
            case PullRequestMsg.DraftSet(SetDraft cmd, var commands) -> {
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                yield PrOverviewUpdate.reloadIfShown(new Next(
                        done.model()
                                .popup(PullRequestPopup.class)
                                .filter(popup -> popup.pullRequest().number() == cmd.number())
                                .map(popup -> done.model().openPopup(popup.withDraft(cmd.draft())))
                                .getOrElse(done.model()),
                        done.cmds()));
            }
            case PullRequestMsg.Ended(ChangingCmd cmd, var commands) -> {
                // Done like any operation. The pull request is no longer open, which reading them again confirms;
                // a merge changed the base branch on the remote, which a fetch brings in.
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                Model ended = done.model()
                        .withPullRequests(done.model()
                                .pullRequests()
                                .map(open -> open.filterValues(pr -> pr.number() != cmd.number())));
                // A read that is still running may have missed it, and would show it open again.
                yield PrOverviewUpdate.reloadIfShown(
                        FetchUpdate.requested(PullRequestsUpdate.loadAnyway(new Next(ended, done.cmds()))));
            }
            case PullRequestMsg.InfoClosed() -> Next.of(model.withoutPopup(PullRequestPopup.class));
        };
    }

    /**
     * Whether the pull request {@code popup} shows can be turned into a draft, or marked ready for review: it is
     * open, and its branch is not busy, e.g. with doing that already.
     */
    static boolean draftChangeable(Model model, PullRequestPopup popup) {
        return popup.open() && !model.busy().containsKey(new BranchRef.Local(popup.branch()));
    }

    /**
     * {@code model} showing what was read about pull request {@code number}; unchanged when it is no longer
     * shown, as the user closed it, or opened another one, while it was being read.
     */
    private static Model shown(Model model, int number, Either<Failure, PullRequestDetails> read) {
        return model.popup(PullRequestPopup.class)
                .filter(popup -> popup.pullRequest().number() == number)
                .map(popup -> model.openPopup(popup.with(read)))
                .getOrElse(model);
    }

    /**
     * Shows the pull request of the branch, reading what there is to know about it. A branch without one gets one, once it is pushed: the user is asked
     * which branch it merges into.
     */
    private static Next requested(Model model, Branch.Name branch) {
        return model.idleBranch(branch)
                .map(known -> model.pullRequest(known)
                        .map(pr -> show(model, branch, pr))
                        // Without the pull requests it is unknown whether there is one to open.
                        .getOrElse(() -> model.pullRequestsFailure().isDefined()
                                        || model.pullRequests().isEmpty()
                                ? Next.of(model.fail(model.pullRequestsFailure()
                                        .<AppError>map(AppError.PullRequestsUnavailable::new)
                                        .getOrElse(AppError.PullRequestsNotRead::new)))
                                : known.remoteUpstream().isEmpty() || known.ahead() > 0
                                        ? Next.of(model.fail(new AppError.NotPushed(branch)))
                                        : Update.ask(model, new BasePopup(branch, bases(model, branch)))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Checks out {@code pullRequest} in place, if it is of the repository and the working tree has no changes to
     * bring along; untracked files do not count, as git only stops for those that are in the way.
     */
    private static Next checkout(Model model, PullRequestSummary pullRequest) {
        String name = pullRequest.repository() + "#" + pullRequest.number();
        boolean changed = Update.files(model).exists(file -> file.type() != ChangeType.UNTRACKED);
        return !model.prOverview().ofThisRepository(pullRequest)
                ? Next.of(model.fail(new AppError.OtherRepository(name)))
                : changed
                        ? Next.of(model.fail(new AppError.ChangesBeforeCheckout(name)))
                        : Update.start(model.clearError(), new CheckoutPullRequestCmd(pullRequest));
    }

    /**
     * Shows {@code pullRequest}, from {@code branch}, reading what there is to know about it.
     */
    private static Next show(Model model, Branch.Name branch, PullRequest pullRequest) {
        return Next.of(
                Update.ask(model, PullRequestPopup.loading(branch, pullRequest)).model(),
                new LoadPullRequestDetails(pullRequest.number()));
    }

    /**
     * The branch {@code base} is on its remote, which a pull request merges into; the local one when it has no
     * upstream there.
     */
    private static BranchRef baseRef(Model model, Branch.Name base) {
        return model.branch(base)
                .flatMap(Branch::remoteUpstream)
                .map(upstream -> BranchRef.of(upstream.branch()))
                .getOrElse(() -> new BranchRef.Local(base));
    }

    /**
     * The branches a pull request from {@code branch} can merge into: the others that are on their remote, the
     * default branch first.
     */
    static List<Branch.Name> bases(Model model, Branch.Name branch) {
        return model.branches()
                .toOption()
                .getOrElse(List.empty())
                .filter(b -> b.hasRemote() && !b.name().equals(branch))
                .sortBy(b -> !b.isDefault())
                .map(Branch::name);
    }
}
