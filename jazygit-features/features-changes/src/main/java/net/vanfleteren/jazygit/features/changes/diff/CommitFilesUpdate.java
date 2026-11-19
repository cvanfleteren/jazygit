package net.vanfleteren.jazygit.features.changes.diff;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.gh.model.GhCommit;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.core.git.model.BranchRef;
import net.vanfleteren.jazygit.core.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.kernel.state.BaseBranchError;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.load.AheadUpdate;
import net.vanfleteren.jazygit.kernel.state.load.LoadAhead;
import net.vanfleteren.jazygit.kernel.state.load.LoadCommitFiles;
import net.vanfleteren.jazygit.kernel.state.load.LoadCommitTree;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.FilesLock;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.markdown.MarkdownImage;

/**
 * Listing the files a commit, a stash, a pull request or a branch changed in the files pane, in place of the changed files of
 * the working tree, and going back to those (ADR 44). The view moves the focus: into the files pane when they are
 * listed, and back to the pane they were opened from when they are closed. The files of a pull request come with its
 * review threads, which are read again when asked, and shown open or not (ADR 55), and can list only the files that
 * have some (ADR 57).
 */
public final class CommitFilesUpdate {

    // The most images of the review threads of a pull request that are read to be shown (ADR 84).
    private static final int MAX_PICTURES = 20;

    private CommitFilesUpdate() {}

    public static Next update(Model model, CommitFilesMsg msg) {
        return switch (msg) {
            case CommitFilesMsg.Opened(var of, var pane) -> opened(locked(model, pane), of, pane);
            // Escape moves the focus back to the list the files are of, which the app does: they stay (ADR 141), and
            // follow the list with the focus again (ADR 149).
            case CommitFilesMsg.Closed() ->
                Next.of(model.mapRepoView(repoView -> repoView.mapFiles(files -> files.withLocked(Option.none()))));
            case CommitFilesMsg.RemarksLoaded(PrNumber number, var remarks, var settled, int version) ->
                remarksRead(model, number, remarks, settled, version);
            // The review threads read before stay shown; what was sent is done with all the same. A failed read
            // older than they are changes nothing they show either (ADR 64).
            case CommitFilesMsg.RemarksFailed(PrNumber number, var failure, var settled, int version) ->
                Next.of(mapPullRequest(
                        model,
                        number,
                        view -> view.withRemarks(Option.of(view.remarks()
                                        .filter(remarks -> remarks.toOption().isDefined())
                                        .getOrElse(() -> new Loadable.Failed<>(failure))))
                                .withSending(view.sending().removeAll(settled))));
            case CommitFilesMsg.ListedCycled() ->
                Next.of(model.mapRepoView(repoView ->
                        repoView.mapFiles(files -> files.withCommitFiles(files.commitFiles()
                                .map(view -> view.remarks().isDefined()
                                        ? view.withListed(view.listed().next())
                                        : view)))));
            // Those flipped before the threads are read are as they start until then (ADR 63).
            case CommitFilesMsg.CollapsedLoaded(PrNumber number, var kept) ->
                Next.of(mapPullRequest(
                        model,
                        number,
                        view -> view.withCollapsed(view.collapsed().with(kept).readAgain(view.threads()))));
            case CommitFilesMsg.CollapsedKept() -> Next.of(model);
            case CommitFilesMsg.AllToggled() -> allToggled(model);
            case CommitFilesMsg.PictureLoaded(PrNumber number, String url, var picture) ->
                Next.of(mapPullRequest(
                        model, number, view -> view.withPictures(view.pictures().put(url, picture))));
            case CommitFilesMsg.RemarksRefreshed() ->
                new Next(
                        model,
                        List.narrow(model.repoView()
                                .files()
                                .commitFiles()
                                .toList()
                                .flatMap(CommitFilesUpdate::remarksLoad)));
        };
    }

    /**
     * {@code model} with the files pane locked anew to the list the pane {@code pane} shows: what it lists follows the
     * highlight of that list only, whichever pane has the focus (ADR 149).
     */
    private static Model locked(Model model, PaneId pane) {
        return model.mapRepoView(repoView -> repoView.mapFiles(files ->
                files.lockedTo(pane, FilesLock.In.of(repoView, pane.equals(Scopes.BRANCHES.pane())))));
    }

    /**
     * Enter on {@code of}, in the pane {@code pane}, or {@code F} on a branch: the files listed already, as they follow
     * its highlight or were opened before, stay as they are, read or being read, and are listed whole (ADR 141, 149).
     */
    private static Next opened(Model model, Changes of, PaneId pane) {
        return model.repoView()
                .files()
                .commitFiles()
                .filter(view -> view.of().equals(of))
                .map(view -> Next.of(model.mapRepoView(repoView -> repoView.mapFiles(files ->
                        files.withCommitFiles(Option.of(view.withFollowed(false).withPane(pane)))))))
                .getOrElse(() -> open(model, CommitFilesView.of(of, pane, Scopes.FILES.pane())));
    }

    /**
     * Shows {@code view}, reading its files, and what goes with them: the review threads of a pull request and those
     * collapsed, and all files of its commit when it lists them.
     */
    private static Next open(Model model, CommitFilesView view) {
        return new Next(
                model.mapRepoView(repoView -> repoView.mapFiles(files -> files.withCommitFiles(Option.of(view)))),
                List.<Cmd>of(LoadCommitFiles.of(view))
                        .appendAll(treeLoad(view))
                        .appendAll(remarksLoad(view))
                        .appendAll(collapsedLoad(view)));
    }

    /**
     * Lists all files of the commit the files listed end at, or only those changed again (ADR 128). All files are
     * read the first time they are listed, and again only when reading them failed.
     */
    private static Next allToggled(Model model) {
        return model.repoView()
                .files()
                .commitFiles()
                .map(view -> {
                    boolean all = !view.allFiles();
                    boolean reads = all && view.tree().forAll(Loadable::isFailed);
                    CommitFilesView toggled =
                            view.listingAll(all).withTree(reads ? Option.of(Loadable.loading()) : view.tree());
                    return new Next(
                            model.mapRepoView(
                                    repoView -> repoView.mapFiles(files -> files.withCommitFiles(Option.of(toggled)))),
                            reads ? List.<Cmd>of(new LoadCommitTree(view.of())) : List.empty());
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Lists the files {@code branch} changes against its base, the base of ADR 20, at the commits the branches read
     * last have them at (ADR 61). The main branch has no base to be compared with: all files of its tip are listed,
     * as committed, whatever is checked out (ADR 128). A branch without a base has nothing to be compared with. A
     * branch with an open pull request, the main branch too, shows the files of that instead, before this is asked.
     */
    public static Next againstBase(Model model, BranchName branch) {
        LoadAhead load = AheadUpdate.of(model, new BranchRef.Local(branch));
        return AheadUpdate.main(model).contains(branch)
                ? load.tip()
                        .map(tip -> open(
                                locked(model, Scopes.BRANCHES.pane()),
                                CommitFilesView.of(
                                                new Changes.OfTip(branch, tip),
                                                Scopes.BRANCHES.pane(),
                                                Scopes.FILES.pane())
                                        .withAllFiles(true)
                                        .withTree(Option.of(Loadable.loading()))))
                        .getOrElse(() -> Next.of(model))
                : load.base()
                        .map(base -> load.tip()
                                .map(tip -> update(
                                        model,
                                        new CommitFilesMsg.Opened(
                                                new Changes.OfBranch(branch, base, tip), Scopes.BRANCHES.pane())))
                                .getOrElse(() -> Next.of(model)))
                        .getOrElse(() -> Next.of(model.fail(new BaseBranchError.None(branch))));
    }

    /**
     * All files of the commit the files of {@code view} end at, when it lists them and they are still to be read.
     */
    private static Option<LoadCommitTree> treeLoad(CommitFilesView view) {
        return Option.when(
                view.allFiles() && view.tree().exists(tree -> tree instanceof Loadable.Loading<?>),
                () -> new LoadCommitTree(view.of()));
    }

    /**
     * The review threads of the pull request whose files are listed, if they are, read at the version they are at.
     */
    private static Option<CommitFilesCmd.LoadReviewRemarks> remarksLoad(CommitFilesView view) {
        return view.pullRequest()
                .map(number -> new CommitFilesCmd.LoadReviewRemarks(number, HashSet.empty(), view.version()));
    }

    /**
     * The review threads and comments of the pull request whose files are listed, if they are, collapsed or opened in
     * an earlier session (ADR 63).
     */
    private static Option<CommitFilesCmd.LoadCollapsed> collapsedLoad(CommitFilesView view) {
        return view.of() instanceof Changes.OfPullRequest(PullRequestCommits commits)
                ? Option.of(new CommitFilesCmd.LoadCollapsed(commits.repository(), new PrNumber(commits.number())))
                : Option.none();
    }

    /**
     * {@code remarks} read for pull request {@code number}, while its files are listed, after what was sent for the
     * threads {@code settled}. When its head or its base moved on since its files were read, they are read again, at
     * the commits they are at now: the threads are of those. Read at an older {@code version} than they are at now, as
     * before a comment was deleted, they are dropped, and read again for the threads {@code settled} (ADR 64).
     */
    private static Next remarksRead(
            Model model, PrNumber number, ReviewRemarks remarks, Set<ReviewThread.Id> settled, int version) {
        return model.repoView()
                .files()
                .commitFiles()
                .filter(view -> view.pullRequest().contains(number))
                .map(view -> {
                    if (version < view.version()) {
                        return Next.of(model, new CommitFilesCmd.LoadReviewRemarks(number, settled, view.version()));
                    }
                    // What was sent for a thread shows in what is read after it; a read started before it was
                    // sent may not show it, so the thread still takes no key until then (ADR 60). A thread resolved
                    // or unresolved is then as such a one starts (ADR 62).
                    CommitFilesView read = view.withRemarks(Option.of(new Loadable.Loaded<>(remarks)))
                            .withSending(view.sending().removeAll(settled))
                            .withCollapsed(view.collapsed().readAgain(remarks.threads()));
                    return moved(read, remarks)
                            .map(commits -> {
                                CommitFilesView again = CommitFilesView.of(
                                                new Changes.OfPullRequest(commits), view.pane(), view.listedIn())
                                        .withRemarks(read.remarks())
                                        .withListed(view.listed())
                                        .withCollapsed(read.collapsed())
                                        .withSending(read.sending())
                                        .withDeleting(read.deleting())
                                        .withReacting(read.reacting())
                                        .withVersion(read.version())
                                        .withPictures(read.pictures())
                                        // The same pull request, read again: its filter stays (ADR 105), and so
                                        // does whether all its files are listed, read anew at its head (ADR 128).
                                        .withFilter(read.filter())
                                        .withAllFiles(read.allFiles())
                                        .withTree(Option.when(read.allFiles(), Loadable::loading));
                                return pictures(
                                        model.mapRepoView(repoView ->
                                                repoView.mapFiles(files -> files.withCommitFiles(Option.of(again)))),
                                        again,
                                        number,
                                        List.<Cmd>of(LoadCommitFiles.of(again)).appendAll(treeLoad(again)));
                            })
                            .getOrElse(() -> pictures(
                                    model.mapRepoView(repoView ->
                                            repoView.mapFiles(files -> files.withCommitFiles(Option.of(read)))),
                                    read,
                                    number,
                                    List.empty()));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code cmds}, and reads the images of the review threads of pull request {@code number}, which {@code view}
     * lists the files of in {@code model}, that are not read or being read yet, up to {@link #MAX_PICTURES} of them,
     * to show them (ADR 84).
     */
    private static Next pictures(Model model, CommitFilesView view, PrNumber number, List<Cmd> cmds) {
        // Lazily, so that the comments are parsed only until there are enough images.
        List<String> urls = view.threads()
                .iterator()
                .flatMap(ReviewThread::comments)
                .flatMap(comment -> MarkdownImage.onGitHub(comment.body()))
                .distinct()
                .filter(url -> !view.pictures().containsKey(url))
                .take(Math.max(0, MAX_PICTURES - view.pictures().size()))
                .toList();
        return new Next(
                mapPullRequest(
                        model,
                        number,
                        open -> open.withPictures(
                                urls.foldLeft(open.pictures(), (pictures, url) -> pictures.put(url, Option.none())))),
                cmds.appendAll(urls.map(url -> new CommitFilesCmd.LoadPicture(number, url))));
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

    private static Model mapPullRequest(Model model, PrNumber number, Function<CommitFilesView, CommitFilesView> f) {
        return model.mapRepoView(repoView -> repoView.mapFiles(files -> files.withCommitFiles(
                files.commitFiles().map(view -> view.pullRequest().contains(number) ? f.apply(view) : view))));
    }
}
