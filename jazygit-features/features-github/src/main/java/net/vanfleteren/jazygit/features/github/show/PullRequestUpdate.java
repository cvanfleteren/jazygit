package net.vanfleteren.jazygit.features.github.show;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.core.gh.model.MergeMethod;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesUpdate;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPopup;
import net.vanfleteren.jazygit.features.github.browse.BrowsePullRequestCmd;
import net.vanfleteren.jazygit.features.github.checks.timings.CheckTimesUpdate;
import net.vanfleteren.jazygit.features.github.create.GitHubCreatePullRequestUpdate;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.features.github.prs.PrOverviewUpdate;
import net.vanfleteren.jazygit.features.github.prs.PullRequestReads;
import net.vanfleteren.jazygit.features.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.features.github.state.*;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Events;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.operation.*;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Showing the pull requests of the branches, and merging, closing, opening and drafting them from there; a
 * branch without one gets one created.
 */
public final class PullRequestUpdate {

    private PullRequestUpdate() {}

    public static Next update(Model model, PullRequestMsg msg) {
        return switch (msg) {
            case PullRequestMsg.Requested(BranchName branch) -> requested(model, branch);
            case PullRequestMsg.FilesRequested(BranchName branch) -> filesRequested(model, branch);
            case PullRequestMsg.SummaryRequested(var pullRequest) -> summaryRequested(model, pullRequest);
            case PullRequestMsg.SummaryFilesRequested(var pullRequest) -> summaryFilesRequested(model, pullRequest);
            // A read that a later one was taken before, or that started before a change, is not taken: it would show
            // older details, or the change undone, here and on the branch; failing, it says nothing of them (ADR 34).
            case PullRequestMsg.DetailsLoaded(PrNumber number, long read, var details)
            when !model.part(GitHubState.PART).current(number, read) -> Next.of(notTaken(model, number, read));
            case PullRequestMsg.DetailsFailed(PrNumber number, long read, var failure)
            when !model.part(GitHubState.PART).current(number, read) -> Next.of(notTaken(model, number, read));
            case PullRequestMsg.DetailsLoaded(PrNumber number, long read, var details) ->
                detailsLoaded(model, number, read, details);
            case PullRequestMsg.DetailsFailed(PrNumber number, long read, var failure) ->
                detailsFailed(model, number, read, failure);
            case PullRequestMsg.OpenConfirmed() -> openConfirmed(model);
            case PullRequestMsg.FilesChosen() -> filesChosen(model);
            case PullRequestMsg.MergeChosen(MergeMethod method) -> mergeChosen(model, method);
            case PullRequestMsg.MergeAsked(MergePullRequest cmd, var status, var commands) ->
                MergeUpdate.asked(model, cmd, status, commands);
            case PullRequestMsg.AutoMergeSet(AutoMergeCmd cmd, var commands) -> autoMergeSet(model, cmd, commands);
            case PullRequestMsg.AutoMergeFailed(AutoMergeCmd cmd, Failure failure) ->
                autoMergeFailed(model, cmd, failure);
            case PullRequestMsg.MergeTick() -> MergeUpdate.tick(model);
            case PullRequestMsg.MergeChecked(PrNumber number, String id, var status) ->
                MergeUpdate.checked(model, number, id, status);
            case PullRequestMsg.MergeCheckFailed(PrNumber number, String id, var failure) ->
                MergeUpdate.checkFailed(model, number, id);
            case PullRequestMsg.CloseChosen() -> closeChosen(model);
            case PullRequestMsg.DraftChosen() -> draftChosen(model);
            case PullRequestMsg.DraftSet(SetDraft cmd, var commands) -> draftSet(model, cmd, commands);
            case PullRequestMsg.Ended(ChangingCmd cmd, var commands) -> ended(model, cmd, commands);
            case PullRequestMsg.RefreshChosen() -> refreshChosen(model);
            case PullRequestMsg.Tick() -> readAgain(model, popup -> popup.dueByItself(model.now()), Reading.BY_ITSELF);
        };
    }

    /**
     * Shows a pull request listed on the pull requests screen. One of another repository is opened in the browser,
     * as gh acts on this one; so is one while it is not known yet which repository this is, which does no harm.
     */
    private static Next summaryRequested(Model model, PullRequestSummary pullRequest) {
        return PrOverview.ofThisRepository(model, pullRequest).getOrElse(false)
                ? show(
                        model,
                        FromGitHub.branch(pullRequest.headBranch()),
                        pullRequest.pullRequest(),
                        pullRequest.fromFork(),
                        false)
                : OperationUpdate.start(model.clearError(), new BrowsePullRequestCmd(pullRequest));
    }

    /**
     * Shows the files a pull request listed on the pull requests screen changes. They are listed in the files pane,
     * which only has those of this repository; one of another repository is opened in the browser.
     */
    private static Next summaryFilesRequested(Model model, PullRequestSummary pullRequest) {
        return PrOverview.ofThisRepository(model, pullRequest).getOrElse(false)
                ? files(
                        model.clearError(),
                        FromGitHub.branch(pullRequest.headBranch()),
                        pullRequest.pullRequest(),
                        pullRequest.fromFork())
                : OperationUpdate.start(model.clearError(), new BrowsePullRequestCmd(pullRequest));
    }

    /**
     * Shows the details read {@code read} brought of pull request {@code number}. The branches pane shows the pull
     * request as it was just read too, also when the dialog is closed. How long its checks take is learned from them
     * too (ADR 32).
     */
    private static Next detailsLoaded(Model model, PrNumber number, long read, PullRequestDetails details) {
        return filesIfWanted(
                model,
                CheckTimesUpdate.read(
                        Next.of(shown(
                                model.mapPart(
                                        GitHubState.PART,
                                        gitHub -> gitHub.withRead(details, read).withReadTaken(number, read)),
                                number,
                                read,
                                Either.right(details))),
                        details.repository(),
                        details.checks()));
    }

    /**
     * Shows that read {@code read} of pull request {@code number} failed.
     */
    private static Next detailsFailed(Model model, PrNumber number, long read, Failure failure) {
        return Next.of(shown(
                model.mapPart(GitHubState.PART, gitHub -> gitHub.withReadTaken(number, read)),
                number,
                read,
                Either.left(failure)));
    }

    /**
     * Opens the pull request shown in the browser. The dialog stays open, so the pull request can still be merged
     * or closed from it.
     */
    private static Next openConfirmed(Model model) {
        return model.popup(PullRequestPopup.class)
                .map(popup -> OperationUpdate.start(
                        model.clearError(),
                        new OpenPullRequest(popup.branch(), popup.pullRequest().number())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The dialog closes, and the files show in the files pane of the repository screen, which the focus goes to;
     * Escape there gives it to the branches pane (ADR 55).
     */
    private static Next filesChosen(Model model) {
        return model.popup(PullRequestPopup.class)
                .flatMap(popup -> popup.commits()
                        .map(commits -> files(
                                model.withoutPopup(PullRequestPopup.class), popup.branch(), commits, popup.fromFork())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * What the merge key of {@code method} does to the pull request shown, when it can be merged (ADR 112): merging it
     * now closes the dialog, as GitHub merges it in the background (ADR 24); turning its auto-merge on or off keeps
     * the dialog open, which shows the pull request read again once that is done.
     */
    private static Next mergeChosen(Model model, MergeMethod method) {
        return model.popup(PullRequestPopup.class)
                .filter(popup -> mergeChoosable(model, popup, method))
                .flatMap(popup -> popup.readDetails().map(pr -> switch (MergeKey.of(pr, method)) {
                    case MergeKey.Now() ->
                        PopupUpdate.confirm(
                                model,
                                PullRequestPopup.class,
                                shown -> Option.of(new MergePullRequest(
                                        shown.branch(), shown.pullRequest().number(), shown.head(), method)));
                    case MergeKey.WhenReady() ->
                        OperationUpdate.start(
                                model.clearError(),
                                new EnableAutoMerge(
                                        popup.branch(),
                                        popup.number(),
                                        pr.conversation().reviewing().pullRequest(),
                                        method,
                                        popup.head()));
                    case MergeKey.Cancel() ->
                        OperationUpdate.start(
                                model.clearError(),
                                new DisableAutoMerge(
                                        popup.branch(),
                                        popup.number(),
                                        pr.conversation().reviewing().pullRequest()));
                    case MergeKey.Disabled(var _) -> Next.of(model);
                }))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Done like any operation, which reads the pull requests again; then the pull request shown is read again, as
     * GitHub tells what it now merges by itself. A read of it that started before is not taken (ADR 34). Turning
     * auto-merge on forgets how an earlier merge of it went, as a new merge replaces it (ADR 112).
     */
    private static Next autoMergeSet(Model model, AutoMergeCmd cmd, List<ExecutedCommand> commands) {
        return PrOverviewUpdate.reloadIfShown(OperationUpdate.succeeded(model, cmd, commands)
                        .map(done -> done.mapPart(
                                GitHubState.PART,
                                gitHub -> forgettingMerge(gitHub, cmd).withChanged(cmd.number()))))
                .then(done -> readAgainOf(done, cmd.number()));
    }

    /**
     * Shown as any failed operation; then the pull request shown is read again, as GitHub may have refused for what
     * changed since it was read, like it being mergeable now, which its keys then do (ADR 112).
     */
    private static Next autoMergeFailed(Model model, AutoMergeCmd cmd, Failure failure) {
        return OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                .map(done -> done.mapPart(GitHubState.PART, gitHub -> gitHub.withChanged(cmd.number())))
                .then(done -> readAgainOf(done, cmd.number()));
    }

    private static GitHubState forgettingMerge(GitHubState gitHub, AutoMergeCmd cmd) {
        return cmd instanceof EnableAutoMerge
                ? gitHub.withMerges(gitHub.merges().filterValues(merge -> !merge.merges(cmd.number())))
                : gitHub;
    }

    /**
     * {@code model} reading the pull request {@code number} again by itself, when its dialog is open.
     */
    private static Next readAgainOf(Model model, PrNumber number) {
        return readAgain(model, popup -> popup.number().equals(number), Reading.BY_ITSELF);
    }

    /**
     * Asks to confirm closing the pull request shown, when it is open.
     */
    private static Next closeChosen(Model model) {
        return PopupUpdate.confirm(
                model,
                PullRequestPopup.class,
                popup -> Option.when(
                        popup.open(),
                        () -> new ClosePullRequest(
                                popup.branch(), popup.pullRequest().number())));
    }

    /**
     * Turns the pull request shown into a draft, or marks it ready for review. The dialog stays open, to show the
     * pull request as it now is.
     */
    private static Next draftChosen(Model model) {
        return model.popup(PullRequestPopup.class)
                .filter(popup -> draftChangeable(model, popup))
                .flatMap(popup -> popup.draft()
                        .map(draft -> OperationUpdate.start(
                                model.clearError(),
                                new SetDraft(popup.branch(), popup.pullRequest().number(), !draft))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Done like any operation, which reads the pull requests again. The dialog shows the change at once, and a read
     * of it that started before is not taken (ADR 34).
     */
    private static Next draftSet(Model model, SetDraft cmd, List<ExecutedCommand> commands) {
        return PrOverviewUpdate.reloadIfShown(OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> mapShown(
                        done.mapPart(GitHubState.PART, gitHub -> gitHub.withChanged(cmd.number())),
                        cmd.number(),
                        popup -> popup.withDraft(cmd.draft()))));
    }

    /**
     * Done like any operation. The pull request is no longer open, which reading them again confirms. A merge ends
     * through MergeAsked instead.
     */
    private static Next ended(Model model, ChangingCmd cmd, List<ExecutedCommand> commands) {
        Next ended = OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> done.mapPart(
                        GitHubState.PART,
                        gitHub -> gitHub.withPullRequests(gitHub.pullRequests()
                                .map(open ->
                                        open.filterValues(pr -> !pr.number().equals(cmd.number()))))));
        // A read that is still running may have missed it, and would show it open again.
        return PrOverviewUpdate.reloadIfShown(
                Events.happened(PullRequestReads.loadAnyway(ended), new KernelEvent.RemoteChanged()));
    }

    /**
     * Reads the pull request shown again. Only the dialog itself is read again, not the one kept under a popup opened
     * from it. R while it reads by itself shows that read as asked for, rather than starting a second one.
     */
    private static Next refreshChosen(Model model) {
        return model.popup(PullRequestPopup.class)
                .filter(popup ->
                        popup.reading() == Reading.BY_ITSELF && readRunning(model, popup) && !busy(model, popup))
                .map(popup -> Next.of(model.openPopup(popup.asked())))
                .getOrElse(() -> readAgain(model, popup -> true, Reading.ASKED));
    }

    /**
     * Whether the pull request {@code popup} shows can be merged, as far as is known: also not while GitHub is
     * merging it already.
     */
    static boolean mergeable(Model model, PullRequestPopup popup) {
        return popup.mergeable()
                && !model.part(GitHubState.PART).merging(popup.pullRequest().number());
    }

    /**
     * Whether the merge key of {@code method} does something to the pull request {@code popup} shows (ADR 112): it
     * is not disabled by what GitHub says of it, and merging it now or when ready needs it to be mergeable. Turning
     * its auto-merge off only needs it open, as GitHub leaves auto-merge on when a conflict appears. Turning auto-merge
     * on or off waits while its branch is busy, as with doing that already.
     */
    public static boolean mergeChoosable(Model model, PullRequestPopup popup, MergeMethod method) {
        return popup.mergeKey(method).exists(key -> switch (key) {
            case MergeKey.Now() -> mergeable(model, popup);
            case MergeKey.WhenReady() -> mergeable(model, popup) && !busy(model, popup);
            case MergeKey.Cancel() -> popup.open() && !busy(model, popup);
            case MergeKey.Disabled(var _) -> false;
        });
    }

    /**
     * Whether the pull request {@code popup} shows can be turned into a draft, or marked ready for review: it is
     * open, and its branch is not busy, e.g. with doing that already. Nor while it is read again as the user asked,
     * which would show a spinner and then not be taken. A read that started before the change is not taken once it is
     * done (ADR 34).
     */
    public static boolean draftChangeable(Model model, PullRequestPopup popup) {
        return popup.open() && popup.reading() != Reading.ASKED && !busy(model, popup);
    }

    /**
     * Whether {@code R} reads the pull request {@code popup} shows again: when it can be read again, or the dialog
     * reads it by itself, which then shows as asked for.
     */
    public static boolean refreshChoosable(Model model, PullRequestPopup popup) {
        return refreshable(model, popup) || popup.reading() == Reading.BY_ITSELF && !busy(model, popup);
    }

    /**
     * Whether the pull request {@code popup} shows can be read again: once the first read is done, no other that is
     * still taken runs, and its branch is not busy, e.g. with turning it into a draft, which a read running meanwhile
     * could miss, and then show undone.
     */
    static boolean refreshable(Model model, PullRequestPopup popup) {
        return popup.details().isDefined() && !readRunning(model, popup) && !busy(model, popup);
    }

    /**
     * Whether the pull request {@code popup} shows reads, by a read that is still taken when it comes back.
     */
    private static boolean readRunning(Model model, PullRequestPopup popup) {
        return popup.refreshing() && model.part(GitHubState.PART).current(popup.number(), popup.read());
    }

    /**
     * {@code model} giving out the next read of the details of pull request {@code number}, and that read (ADR 34).
     */
    public static Tuple2<Model, LoadPullRequestDetails> read(Model model, PrNumber number) {
        return Tuple.of(
                model.mapPart(GitHubState.PART, GitHubState::withReadGivenOut),
                new LoadPullRequestDetails(number, model.part(GitHubState.PART).nextRead()));
    }

    /**
     * {@code model} reading the pull request shown again, as {@code reading} tells, when it can be and {@code due};
     * else as it is.
     */
    private static Next readAgain(Model model, Predicate<PullRequestPopup> due, Reading reading) {
        return model.popup(PullRequestPopup.class)
                .filter(popup -> refreshable(model, popup) && due.test(popup))
                .map(popup -> read(model, popup.number())
                        .apply((given, load) ->
                                Next.of(given.openPopup(popup.readingAgain(reading, model.now(), load.read())), load)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code model} once read {@code read} of pull request {@code number} came back too late to be taken: the dialog
     * that shows it, if one does, no longer reads when it waited for that read.
     */
    private static Model notTaken(Model model, PrNumber number, long read) {
        return mapShown(model, number, popup -> popup.notTaken(read));
    }

    private static boolean busy(Model model, PullRequestPopup popup) {
        return model.jobs().busy().containsKey(new BranchRef.Local(popup.branch()));
    }

    /**
     * {@code model} showing what read {@code read} brought about pull request {@code number}, its checks no less far
     * than they were shown to be (ADR 89); unchanged when it is no longer shown, as the user closed it, or opened
     * another one, while it was being read.
     */
    private static Model shown(Model model, PrNumber number, long read, Either<Failure, PullRequestDetails> result) {
        return mapShown(model, number, popup -> popup.reaching(model).with(result, read));
    }

    /**
     * {@code model} with the dialog of pull request {@code number} as {@code change} makes it: the one that is open,
     * or the one the open popup goes back to, like a comment on it, its checks or a review, so that one is not out of date
     * then. Unchanged when no popup shows it.
     */
    private static Model mapShown(Model model, PrNumber number, Function<PullRequestPopup, PullRequestPopup> change) {
        return model.popup(ShowsPullRequest.class)
                .filter(popup -> popup.number().equals(number))
                .map(popup -> model.openPopup(popup.withPullRequestPopup(change.apply(popup.pullRequestPopup()))))
                .orElse(() -> model.popup(ChecksPopup.class)
                        .flatMap(checks -> checks.from()
                                .filter(PullRequestPopup.class::isInstance)
                                .map(PullRequestPopup.class::cast)
                                .filter(dialog -> dialog.pullRequest().number().equals(number))
                                .map(dialog -> model.openPopup(checks.withFrom(Option.of(change.apply(dialog)))))))
                // A popup opened from the dialog that follows it, as a review follows whether it is a draft (ADR 100).
                .orElse(() -> model.popup(FollowsPullRequest.class)
                        .flatMap(follower -> follower.from()
                                .filter(from -> from.number().equals(number))
                                .map(from -> follower.followedIn(model, change.apply(from.pullRequestPopup())))))
                .getOrElse(model);
    }

    /**
     * Shows the pull request of the branch, reading what there is to know about it. A branch without one gets one:
     * the user is asked to push it first when it is not on its remote, or not with all its commits, and then which
     * branch it merges into.
     */
    private static Next requested(Model model, BranchName branch) {
        GitHubState gitHub = model.part(GitHubState.PART);
        return model.idleBranch(branch)
                .map(known -> gitHub.pullRequest(known)
                        .map(pr -> show(model, branch, pr, gitHub.fromFork(pr), false))
                        // Without the pull requests it is unknown whether there is one to open.
                        .getOrElse(() -> gitHub.pullRequestsFailure().isDefined()
                                        || gitHub.pullRequests().isEmpty()
                                ? Next.of(model.fail(gitHub.pullRequestsFailure()
                                        .<AppError>map(PullRequestsError.Unavailable::new)
                                        .getOrElse(PullRequestsError.NotRead::new)))
                                : GitHubCreatePullRequestUpdate.askPushed(model, known)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Shows the files the branch changes (ADR 61). With an open pull request, those of the pull request. Without one,
     * as far as is known, those against its base. An error shown before goes, as the files are asked for anew.
     */
    private static Next filesRequested(Model model, BranchName branch) {
        Model cleared = model.clearError();
        return cleared.repo()
                .branch(branch)
                .flatMap(known -> cleared.part(GitHubState.PART).pullRequest(known))
                .map(pr -> files(cleared, branch, pr, false))
                .getOrElse(() -> CommitFilesUpdate.againstBase(cleared, branch));
    }

    /**
     * Shows the files {@code pullRequest}, of {@code branch}, of a fork when {@code fromFork}, changes: at the commits
     * it was listed with; when those are not known, once its dialog read them, which it shows meanwhile, and which
     * stays, to say so, when they cannot be shown.
     */
    private static Next files(Model model, BranchName branch, PullRequest pullRequest, boolean fromFork) {
        return pullRequest
                .commits()
                .map(listed -> commits(pullRequest.number(), listed))
                .map(commits -> files(model, branch, commits, fromFork))
                .getOrElse(() -> show(model, branch, pullRequest, fromFork, true));
    }

    /**
     * The files of the pull request at {@code commits}, of {@code branch}, in the files pane of the repository screen,
     * which the focus goes to; Escape there gives it to the branches pane (ADR 55). They are edited in the working
     * tree of that branch, when it is the local branch of the pull request (ADR 192).
     */
    private static Next files(Model model, BranchName branch, PullRequestCommits commits, boolean fromFork) {
        return CommitFilesUpdate.update(
                model.withScreen(Screen.REPOSITORY),
                new CommitFilesMsg.Opened(
                        new Changes.OfPullRequest(commits, local(model, branch, commits.number(), fromFork)),
                        Scopes.BRANCHES.pane()));
    }

    /**
     * {@code branch}, when it is the local branch of the pull request numbered {@code number}: for a pull request from
     * a fork, when {@code fromFork}, only one GitHub's part finds that pull request of, as the pull requests screen
     * names it by its head branch in the fork, often {@code main}; else any local branch of that name, also once the
     * pull request is closed (ADR 192).
     */
    private static Option<BranchName> local(Model model, BranchName branch, PrNumber number, boolean fromFork) {
        GitHubState gitHub = model.part(GitHubState.PART);
        return model.repo()
                .branch(branch)
                .filter(known -> !fromFork
                        || gitHub.pullRequest(known).exists(pr -> pr.number().equals(number)))
                .map(known -> branch);
    }

    /**
     * The commits pull request {@code number}, listed with {@code commits}, is diffed between.
     */
    private static PullRequestCommits commits(PrNumber number, PullRequest.Commits commits) {
        return new PullRequestCommits(number, commits.repository(), commits.host(), commits.base(), commits.head());
    }

    /**
     * {@code shown} going on to the files of the pull request when its dialog in {@code before} waited to show them,
     * as F on its branch, or on it on the pull requests screen, asked. Only the read it waits for gives it its
     * details, and so the commits to list them at, and taking that read ends the waiting: a read of another pull
     * request, or another read of it, finds no commits, and lists nothing.
     */
    private static Next filesIfWanted(Model before, Next shown) {
        return before.popup(PullRequestPopup.class)
                .filter(PullRequestPopup::filesWanted)
                .map(popup -> shown.then(PullRequestUpdate::filesChosen))
                .getOrElse(shown);
    }

    /**
     * Shows {@code pullRequest}, from {@code branch}, of a fork when {@code fromFork}, reading what there is to know
     * about it; then the files it changes in its place when {@code files}.
     */
    private static Next show(Model model, BranchName branch, PullRequest pullRequest, boolean fromFork, boolean files) {
        return read(model, pullRequest.number()).apply((given, load) -> {
            PullRequestPopup opened = PullRequestPopup.loading(branch, pullRequest, model.now(), load.read());
            PullRequestPopup loading = fromFork ? opened.ofFork() : opened;
            return Next.of(
                    PopupUpdate.ask(given, files ? loading.wantingFiles() : loading)
                            .model(),
                    load);
        });
    }
}
