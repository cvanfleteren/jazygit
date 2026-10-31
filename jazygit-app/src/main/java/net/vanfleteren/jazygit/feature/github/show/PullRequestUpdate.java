package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.feature.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.feature.diff.CommitFilesUpdate;
import net.vanfleteren.jazygit.feature.fetch.FetchUpdate;
import net.vanfleteren.jazygit.feature.github.browse.BrowsePullRequestCmd;
import net.vanfleteren.jazygit.feature.github.create.CreatePullRequestUpdate;
import net.vanfleteren.jazygit.feature.github.show.PullRequestCmd.*;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.prs.PrOverviewUpdate;

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
            // A pull request of another repository is opened in the browser, as gh acts on this one; so is one
            // while it is not known yet which repository this is, which does no harm.
            case PullRequestMsg.SummaryRequested(var pullRequest)
            when model.prOverview().ofThisRepository(pullRequest).getOrElse(false) ->
                show(model, new BranchName(pullRequest.headBranch()), pullRequest.pullRequest(), false);
            case PullRequestMsg.SummaryRequested(var pullRequest) ->
                Update.start(model.clearError(), new BrowsePullRequestCmd(pullRequest));
            // The branches pane shows the pull request as it was just read too, also when the dialog is closed.
            // How long its checks take is learned from them too (ADR 32).
            // A read that a later one was taken before, or that started before a change, is not taken: it would show
            // older details, or the change undone, here and on the branch; failing, it says nothing of them (ADR 34).
            case PullRequestMsg.DetailsLoaded(int number, long read, var details)
            when !model.gitHub().current(number, read) -> Next.of(notTaken(model, number, read));
            case PullRequestMsg.DetailsFailed(int number, long read, var failure)
            when !model.gitHub().current(number, read) -> Next.of(notTaken(model, number, read));
            case PullRequestMsg.DetailsLoaded(int number, long read, var details) ->
                filesIfWanted(
                        model,
                        CheckTimesUpdate.read(
                                Next.of(shown(
                                        model.mapGitHub(gitHub ->
                                                gitHub.withRead(details).withReadTaken(number, read)),
                                        number,
                                        read,
                                        Either.right(details))),
                                details.repository(),
                                details.checks()));
            case PullRequestMsg.DetailsFailed(int number, long read, var failure) ->
                Next.of(shown(
                        model.mapGitHub(gitHub -> gitHub.withReadTaken(number, read)),
                        number,
                        read,
                        Either.left(failure)));
            // The dialog stays open, so the pull request can still be merged or closed from it.
            case PullRequestMsg.OpenConfirmed() ->
                model.popup(PullRequestPopup.class)
                        .map(popup -> Update.start(
                                model.clearError(),
                                new OpenPullRequest(
                                        popup.branch(), popup.pullRequest().number())))
                        .getOrElse(() -> Next.of(model));
            // The dialog closes, and the files show in the files pane of the repository screen, which the focus goes
            // to; Escape there gives it to the branches pane (ADR 55).
            case PullRequestMsg.FilesChosen() ->
                model.popup(PullRequestPopup.class)
                        .flatMap(PullRequestPopup::commits)
                        .map(commits -> files(model.withoutPopup(PullRequestPopup.class), commits))
                        .getOrElse(() -> Next.of(model));
            case PullRequestMsg.MergeChosen(MergeMethod method) ->
                Update.confirm(
                        model,
                        PullRequestPopup.class,
                        popup -> Option.when(
                                mergeable(model, popup),
                                () -> new MergePullRequest(
                                        popup.branch(), popup.pullRequest().number(), popup.head(), method)));
            case PullRequestMsg.MergeAsked(MergePullRequest cmd, var status, var commands) ->
                MergeUpdate.asked(model, cmd, status, commands);
            case PullRequestMsg.MergeTick() -> MergeUpdate.tick(model);
            case PullRequestMsg.MergeChecked(int number, String id, var status) ->
                MergeUpdate.checked(model, number, id, status);
            case PullRequestMsg.MergeCheckFailed(int number, String id, var failure) ->
                MergeUpdate.checkFailed(model, number, id);
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
            // Done like any operation, which reads the pull requests again. The dialog shows the change at once, and
            // a read of it that started before is not taken (ADR 34).
            case PullRequestMsg.DraftSet(SetDraft cmd, var commands) -> {
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                yield PrOverviewUpdate.reloadIfShown(new Next(
                        mapShown(
                                done.model().mapGitHub(gitHub -> gitHub.withChanged(cmd.number())),
                                cmd.number(),
                                popup -> popup.withDraft(cmd.draft())),
                        done.cmds()));
            }
            case PullRequestMsg.Ended(ChangingCmd cmd, var commands) -> {
                // Done like any operation. The pull request is no longer open, which reading them again confirms.
                // A merge ends through MergeAsked instead.
                Next done = OperationUpdate.succeeded(model, cmd, commands);
                Model ended = done.model()
                        .mapGitHub(gitHub -> gitHub.withPullRequests(gitHub.pullRequests()
                                .map(open -> open.filterValues(pr -> pr.number() != cmd.number()))));
                // A read that is still running may have missed it, and would show it open again.
                yield PrOverviewUpdate.reloadIfShown(
                        FetchUpdate.requested(PullRequestsUpdate.loadAnyway(new Next(ended, done.cmds()))));
            }
            // Only the dialog itself is read again, not the one kept under a popup opened from it. R while it reads by
            // itself shows that read as asked for, rather than starting a second one.
            case PullRequestMsg.RefreshChosen() ->
                model.popup(PullRequestPopup.class)
                        .filter(popup -> popup.reading() == Reading.BY_ITSELF
                                && readRunning(model, popup)
                                && !busy(model, popup))
                        .map(popup -> Next.of(model.openPopup(popup.asked())))
                        .getOrElse(() -> readAgain(model, popup -> true, Reading.ASKED));
            case PullRequestMsg.Tick() -> readAgain(model, popup -> popup.dueByItself(model.now()), Reading.BY_ITSELF);
        };
    }

    /**
     * Whether the pull request {@code popup} shows can be merged, as far as is known: also not while GitHub is
     * merging it already.
     */
    static boolean mergeable(Model model, PullRequestPopup popup) {
        return popup.mergeable() && !model.gitHub().merging(popup.pullRequest().number());
    }

    /**
     * Whether the pull request {@code popup} shows can be turned into a draft, or marked ready for review: it is
     * open, and its branch is not busy, e.g. with doing that already. Nor while it is read again as the user asked,
     * which would show a spinner and then not be taken. A read that started before the change is not taken once it is
     * done (ADR 34).
     */
    static boolean draftChangeable(Model model, PullRequestPopup popup) {
        return popup.open() && popup.reading() != Reading.ASKED && !busy(model, popup);
    }

    /**
     * Whether {@code R} reads the pull request {@code popup} shows again: when it can be read again, or the dialog
     * reads it by itself, which then shows as asked for.
     */
    static boolean refreshChoosable(Model model, PullRequestPopup popup) {
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
        return popup.refreshing() && model.gitHub().current(popup.number(), popup.read());
    }

    /**
     * {@code model} giving out the next read of the details of pull request {@code number}, and that read (ADR 34).
     */
    public static Tuple2<Model, LoadPullRequestDetails> read(Model model, int number) {
        return Tuple.of(
                model.mapGitHub(GitHubState::withReadGivenOut),
                new LoadPullRequestDetails(number, model.gitHub().nextRead()));
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
    private static Model notTaken(Model model, int number, long read) {
        return mapShown(model, number, popup -> popup.notTaken(read));
    }

    private static boolean busy(Model model, PullRequestPopup popup) {
        return model.jobs().busy().containsKey(new BranchRef.Local(popup.branch()));
    }

    /**
     * {@code model} showing what read {@code read} brought about pull request {@code number}; unchanged when it is
     * no longer shown, as the user closed it, or opened another one, while it was being read.
     */
    private static Model shown(Model model, int number, long read, Either<Failure, PullRequestDetails> result) {
        return mapShown(model, number, popup -> popup.with(result, read));
    }

    /**
     * {@code model} with the dialog of pull request {@code number} as {@code change} makes it: the one that is open,
     * or the one the open popup goes back to, like a comment on it, so that one is not out of date then. Unchanged
     * when no popup shows it.
     */
    private static Model mapShown(Model model, int number, Function<PullRequestPopup, PullRequestPopup> change) {
        return model.popup(ShowsPullRequest.class)
                .filter(popup -> popup.number() == number)
                .map(popup -> model.openPopup(popup.withPullRequestPopup(change.apply(popup.pullRequestPopup()))))
                .getOrElse(model);
    }

    /**
     * Shows the pull request of the branch, reading what there is to know about it. A branch without one gets one:
     * the user is asked to push it first when it is not on its remote, or not with all its commits, and then which
     * branch it merges into.
     */
    private static Next requested(Model model, BranchName branch) {
        return model.idleBranch(branch)
                .map(known -> model.gitHub()
                        .pullRequest(known)
                        .map(pr -> show(model, branch, pr, false))
                        // Without the pull requests it is unknown whether there is one to open.
                        .getOrElse(() -> model.gitHub().pullRequestsFailure().isDefined()
                                        || model.gitHub().pullRequests().isEmpty()
                                ? Next.of(model.fail(model.gitHub()
                                        .pullRequestsFailure()
                                        .<AppError>map(AppError.PullRequestsUnavailable::new)
                                        .getOrElse(AppError.PullRequestsNotRead::new)))
                                : CreatePullRequestUpdate.askPushed(model, known)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Shows the files the branch changes (ADR 61). With an open pull request, those of the pull request, at the
     * commits the pull requests were listed with; when those are not known, once its dialog read them, which it shows
     * meanwhile, and which stays, to say so, when they cannot be shown. Without one, as far as is known, those against
     * its base. An error shown before goes, as the files are asked for anew.
     */
    private static Next filesRequested(Model model, BranchName branch) {
        Model cleared = model.clearError();
        return cleared.repo()
                .branch(branch)
                .flatMap(known -> cleared.gitHub().pullRequest(known))
                .map(pr -> pr.commits()
                        .flatMap(listed -> commits(pr.number(), listed))
                        .map(commits -> files(cleared, commits))
                        .getOrElse(() -> show(cleared, branch, pr, true)))
                .getOrElse(() -> CommitFilesUpdate.againstBase(cleared, branch));
    }

    /**
     * The files of the pull request at {@code commits}, in the files pane of the repository screen, which the focus
     * goes to; Escape there gives it to the branches pane (ADR 55).
     */
    private static Next files(Model model, PullRequestCommits commits) {
        return CommitFilesUpdate.update(
                model.withScreen(Screen.REPOSITORY),
                new CommitFilesMsg.Opened(new CommitFilesView.Changes.OfPullRequest(commits), Scopes.BRANCHES.id()));
    }

    /**
     * The commits pull request {@code number}, listed with {@code commits}, is diffed between; none when GitHub named
     * one that is no sha.
     */
    private static Option<PullRequestCommits> commits(int number, PullRequest.Commits commits) {
        return Sha.parse(commits.base().oid())
                .flatMap(base -> Sha.parse(commits.head().oid())
                        .map(head -> new PullRequestCommits(number, commits.repository(), commits.host(), base, head)));
    }

    /**
     * {@code shown} going on to the files of the pull request when its dialog in {@code before} waited to show them,
     * as f on its branch asked. Only the read it waits for gives it its details, and so the commits to list them at,
     * and taking that read ends the waiting: a read of another pull request, or another read of it, finds no commits,
     * and lists nothing.
     */
    private static Next filesIfWanted(Model before, Next shown) {
        return before.popup(PullRequestPopup.class)
                .filter(PullRequestPopup::filesWanted)
                .map(popup -> {
                    Next files = update(shown.model(), new PullRequestMsg.FilesChosen());
                    return new Next(files.model(), shown.cmds().appendAll(files.cmds()));
                })
                .getOrElse(shown);
    }

    /**
     * Shows {@code pullRequest}, from {@code branch}, reading what there is to know about it; then the files it
     * changes in its place when {@code files}.
     */
    private static Next show(Model model, BranchName branch, PullRequest pullRequest, boolean files) {
        return read(model, pullRequest.number()).apply((given, load) -> {
            PullRequestPopup loading = PullRequestPopup.loading(branch, pullRequest, model.now(), load.read());
            return Next.of(
                    Update.ask(given, files ? loading.wantingFiles() : loading).model(), load);
        });
    }
}
