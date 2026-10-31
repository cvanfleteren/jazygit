package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.AheadUpdate;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.CommitFilesView;
import net.vanfleteren.jazygit.state.CommitFilesView.Changes;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Listing the files a commit, a stash, a pull request or a branch changed in the files pane, in place of the changed files of
 * the working tree, and going back to those (ADR 44). The view moves the focus: into the files pane when they are
 * listed, and back to the pane they were opened from when they are closed. The files of a pull request come with its
 * review threads, which are read again when asked, and shown open or not (ADR 55), and can list only the files that
 * have some (ADR 57).
 */
public final class CommitFilesUpdate {

    private CommitFilesUpdate() {}

    public static Next update(Model model, CommitFilesMsg msg) {
        return switch (msg) {
            case CommitFilesMsg.Opened(var of, var pane) -> {
                CommitFilesView view = CommitFilesView.of(of, pane, Scopes.FILES.id());
                yield new Next(
                        model.mapRepoView(repoView -> repoView.withCommitFiles(Option.of(view))),
                        List.<Cmd>of(view.load()).appendAll(remarksLoad(view)));
            }
            case CommitFilesMsg.Closed() ->
                Next.of(model.mapRepoView(repoView -> repoView.withCommitFiles(Option.none())));
            case CommitFilesMsg.RemarksLoaded(int number, var remarks, var settled) ->
                remarksRead(model, number, remarks, settled);
            // The review threads read before stay shown; what was sent is done with all the same.
            case CommitFilesMsg.RemarksFailed(int number, var failure, var settled) ->
                Next.of(mapPullRequest(
                        model,
                        number,
                        view -> view.withRemarks(Option.of(view.remarks()
                                        .filter(remarks -> remarks.toOption().isDefined())
                                        .getOrElse(() -> new Loadable.Failed<>(failure))))
                                .withSending(view.sending().removeAll(settled))));
            case CommitFilesMsg.ListedCycled() ->
                Next.of(model.mapRepoView(repoView -> repoView.withCommitFiles(repoView.commitFiles()
                        .map(view -> view.remarks().isDefined()
                                ? view.withListed(view.listed().next())
                                : view))));
            case CommitFilesMsg.RemarksRefreshed() ->
                new Next(
                        model,
                        List.narrow(model.repoView().commitFiles().toList().flatMap(CommitFilesUpdate::remarksLoad)));
        };
    }

    /**
     * Lists the files {@code branch} changes against its base, the base of ADR 20, at the commits the branches read
     * last have them at (ADR 61). The main branch has no base to be compared with, nor has a branch without one.
     */
    public static Next againstBase(Model model, BranchName branch) {
        Cmd.LoadAhead load = AheadUpdate.of(model, new BranchRef.Local(branch));
        return AheadUpdate.main(model).contains(branch)
                ? Next.of(model.fail(new AppError.IsBaseBranch(branch)))
                : load.base()
                        .map(base -> load.tip()
                                .map(tip -> update(
                                        model,
                                        new CommitFilesMsg.Opened(
                                                new Changes.OfBranch(branch, base, tip), Scopes.BRANCHES.id())))
                                .getOrElse(() -> Next.of(model)))
                        .getOrElse(() -> Next.of(model.fail(new AppError.NoBaseBranch(branch))));
    }

    /**
     * The review threads of the pull request whose files are listed, if they are.
     */
    private static Option<CommitFilesCmd.LoadReviewRemarks> remarksLoad(CommitFilesView view) {
        return view.pullRequest().map(CommitFilesCmd.LoadReviewRemarks::new);
    }

    /**
     * {@code remarks} read for pull request {@code number}, while its files are listed, after what was sent for the
     * threads {@code settled}. When its head or its base moved on since its files were read, they are read again, at
     * the commits they are at now: the threads are of those.
     */
    private static Next remarksRead(Model model, int number, ReviewRemarks remarks, Set<String> settled) {
        return model.repoView()
                .commitFiles()
                .filter(view -> view.pullRequest().contains(number))
                .map(view -> {
                    // What was sent for a thread shows in what is read after it; a read started before it was
                    // sent may not show it, so the thread still takes no key until then (ADR 60). A thread resolved
                    // or unresolved is then as such a one starts (ADR 62).
                    CommitFilesView read = view.withRemarks(Option.of(new Loadable.Loaded<>(remarks)))
                            .withSending(view.sending().removeAll(settled))
                            .withCollapsed(view.collapsed().readAgain(view.threads(), remarks.threads()));
                    return moved(read, remarks)
                            .map(commits -> {
                                CommitFilesView again = CommitFilesView.of(
                                                new Changes.OfPullRequest(commits), view.pane(), view.listedIn())
                                        .withRemarks(read.remarks())
                                        .withListed(view.listed())
                                        .withCollapsed(read.collapsed())
                                        .withSending(read.sending());
                                return Next.of(
                                        model.mapRepoView(repoView -> repoView.withCommitFiles(Option.of(again))),
                                        again.load());
                            })
                            .getOrElse(() ->
                                    Next.of(model.mapRepoView(repoView -> repoView.withCommitFiles(Option.of(read)))));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The commits of the pull request {@code view} lists the files of, as {@code remarks} were read at, when they are
     * others than its files were read at, while it is open: a merged or closed one stays at the commits it was read at.
     */
    private static Option<PullRequestCommits> moved(CommitFilesView view, ReviewRemarks remarks) {
        return remarks.state() == PullRequestDetails.State.OPEN
                        && view.of() instanceof Changes.OfPullRequest(PullRequestCommits commits)
                ? sha(remarks.base())
                        .flatMap(base -> sha(remarks.head())
                                .map(head -> new PullRequestCommits(
                                        commits.number(), commits.repository(), commits.host(), base, head)))
                        .filter(read -> !read.equals(commits))
                : Option.none();
    }

    private static Option<Sha> sha(Option<GhCommit> commit) {
        return commit.flatMap(read -> Sha.parse(read.oid()));
    }

    private static Model mapPullRequest(Model model, int number, Function<CommitFilesView, CommitFilesView> f) {
        return model.mapRepoView(repoView -> repoView.withCommitFiles(
                repoView.commitFiles().map(view -> view.pullRequest().contains(number) ? f.apply(view) : view)));
    }
}
