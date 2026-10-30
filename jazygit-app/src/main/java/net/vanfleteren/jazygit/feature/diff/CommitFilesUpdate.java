package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.CommitFilesView;
import net.vanfleteren.jazygit.state.CommitFilesView.Changes;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Listing the files a commit, a stash or a pull request changed in the files pane, in place of the changed files of
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
            case CommitFilesMsg.RemarksLoaded(int number, var remarks) -> remarksRead(model, number, remarks);
            // The review threads read before stay shown.
            case CommitFilesMsg.RemarksFailed(int number, var failure) ->
                Next.of(mapPullRequest(
                        model,
                        number,
                        view -> view.withRemarks(Option.of(view.remarks()
                                .filter(remarks -> remarks.toOption().isDefined())
                                .getOrElse(() -> new Loadable.Failed<>(failure))))));
            case CommitFilesMsg.RemarksToggled() ->
                Next.of(model.mapRepoView(repoView -> repoView.withCommitFiles(repoView.commitFiles()
                        .map(view -> view.remarks().isDefined() ? view.withOpened(!view.opened()) : view))));
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
     * The review threads of the pull request whose files are listed, if they are.
     */
    private static Option<CommitFilesCmd.LoadReviewRemarks> remarksLoad(CommitFilesView view) {
        return view.pullRequest().map(CommitFilesCmd.LoadReviewRemarks::new);
    }

    /**
     * {@code remarks} read for pull request {@code number}, while its files are listed. When its head or its base
     * moved on since its files were read, they are read again, at the commits they are at now: the threads are of
     * those.
     */
    private static Next remarksRead(Model model, int number, ReviewRemarks remarks) {
        return model.repoView()
                .commitFiles()
                .filter(view -> view.pullRequest().contains(number))
                .map(view -> {
                    CommitFilesView read = view.withRemarks(Option.of(new Loadable.Loaded<>(remarks)));
                    return moved(read, remarks)
                            .map(commits -> {
                                CommitFilesView again = CommitFilesView.of(
                                                new Changes.OfPullRequest(commits), view.pane(), view.listedIn())
                                        .withRemarks(read.remarks())
                                        .withOpened(view.opened())
                                        .withListed(view.listed());
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
