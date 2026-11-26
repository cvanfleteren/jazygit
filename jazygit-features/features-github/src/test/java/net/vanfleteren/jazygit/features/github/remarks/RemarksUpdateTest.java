package net.vanfleteren.jazygit.features.github.remarks;

import static net.vanfleteren.jazygit.features.github.PullRequestFixtures.loadedWithTheFeature;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.CommentRights;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Loadable.Loaded;
import net.vanfleteren.jazygit.kernel.state.Loadable.Loading;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.load.LoadCommitFiles;
import net.vanfleteren.jazygit.kernel.state.load.LoadCommitTree;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.FileFilter;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.lib.image.Picture;
import org.junit.jupiter.api.Test;

/**
 * Reading the review threads of a pull request whose files are listed, which GitHub's part keeps (ADR 55, 171).
 */
class RemarksUpdateTest {

    private static final Sha SHA = new Sha("aaaa");
    private static final Changes COMMIT = new Changes.OfCommit(SHA);
    private static final CommitFile A = new CommitFile("a.txt", ChangeType.MODIFIED);
    private static final CommitFile B = new CommitFile("dir/b.txt", ChangeType.ADDED);

    private static final PullRequestCommits COMMITS =
            new PullRequestCommits(new PrNumber(7), "owner/repo", "", new Sha("b".repeat(40)), new Sha("a".repeat(40)));
    private static final Changes PULL_REQUEST = new Changes.OfPullRequest(COMMITS);
    private static final ReviewThread THREAD = new ReviewThread(
            "a.txt",
            ReviewThread.Side.RIGHT,
            Option.of(1),
            Option.none(),
            ReviewThread.Side.RIGHT,
            Option.of(1),
            false,
            false,
            ReviewThread.Subject.LINE,
            List.empty(),
            0,
            new ReviewThread.Id("T"),
            ReviewThread.Rights.ALL);

    private static ReviewRemarks remarks(String head, String base) {
        return remarks(PullRequestDetails.State.OPEN, head, base);
    }

    private static ReviewRemarks remarks(PullRequestDetails.State state, String head, String base) {
        return new ReviewRemarks(state, Sha.parse(head), Sha.parse(base), List.of(THREAD));
    }

    private static Model openedPullRequest() {
        return Update.update(loadedWithTheFeature(), new CommitFilesMsg.Opened(PULL_REQUEST, Scopes.BRANCHES.pane()))
                .model();
    }

    @Test
    void theFilesOfAPullRequestComeWithItsReviewThreadsWhichCanBeReadAgain() {
        Next opened =
                Update.update(loadedWithTheFeature(), new CommitFilesMsg.Opened(PULL_REQUEST, Scopes.BRANCHES.pane()));
        assertEquals(
                List.of(
                        new LoadCommitFiles(PULL_REQUEST),
                        new RemarksCmd.LoadReviewRemarks(new PrNumber(7), HashSet.empty(), 0),
                        new RemarksCmd.LoadCollapsed("owner/repo", new PrNumber(7))),
                opened.cmds());
        assertEquals(
                Option.of(new Loading<>()),
                RemarksState.files(opened.model()).get().remarks());

        Model read = Update.update(
                        opened.model(),
                        new RemarksMsg.Loaded(
                                new PrNumber(7), remarks("a".repeat(40), "b".repeat(40)), HashSet.empty(), 0))
                .model();
        PrFiles view = RemarksState.files(read).get();
        assertEquals(List.of(THREAD), view.threads());
        assertEquals(PULL_REQUEST, view.of());
        assertTrue(view.remarksAtItsCommits());

        assertEquals(
                List.of(new RemarksCmd.LoadReviewRemarks(new PrNumber(7), HashSet.empty(), 0)),
                Update.update(read, new RemarksMsg.Refreshed()).cmds());

        // A failed read again keeps the threads read before.
        Model failed = Update.update(
                        read,
                        new RemarksMsg.Failed(new PrNumber(7), new Failure.Bug(new Exception()), HashSet.empty(), 0))
                .model();
        assertEquals(List.of(THREAD), RemarksState.files(failed).get().threads());
    }

    @Test
    void theImagesOnGitHubOfTheReviewThreadsReadAreReadOnceToShowThem() {
        String icon = "https://raw.githubusercontent.com/o/r/assets/icon.png";
        String pasted = "https://github.com/user-attachments/assets/5ac3";
        ReviewThread withImages = THREAD.withComments(List.of(
                new ReviewThread.Comment(
                        "cvf",
                        Instant.EPOCH,
                        "Working… <img src=\"" + icon + "\" width=\"14\" /> and ![x](https://example.com/track.png)",
                        Option.none(),
                        new ReviewThread.Comment.Id("C1"),
                        new ReviewThread.Comment.RestId("1"),
                        CommentRights.ALL),
                new ReviewThread.Comment(
                        "cvf",
                        Instant.EPOCH,
                        "<img alt=\"shot\" src=\"" + pasted + "\">\n\n![again](" + icon + ")",
                        Option.none(),
                        new ReviewThread.Comment.Id("C2"),
                        new ReviewThread.Comment.RestId("2"),
                        CommentRights.ALL)));
        ReviewRemarks remarks = new ReviewRemarks(
                PullRequestDetails.State.OPEN,
                Sha.parse("a".repeat(40)),
                Sha.parse("b".repeat(40)),
                List.of(withImages));

        Next read =
                Update.update(openedPullRequest(), new RemarksMsg.Loaded(new PrNumber(7), remarks, HashSet.empty(), 0));

        // Each once, and not one elsewhere, which would tell its server who reads the comment.
        assertEquals(
                List.of(
                        new RemarksCmd.LoadPicture(new PrNumber(7), icon),
                        new RemarksCmd.LoadPicture(new PrNumber(7), pasted)),
                read.cmds());
        assertEquals(
                HashMap.of(icon, Option.none(), pasted, Option.none()),
                RemarksState.files(read.model()).get().read().pictures());
        // Read again, they are not read again.
        assertEquals(
                List.empty(),
                Update.update(read.model(), new RemarksMsg.Loaded(new PrNumber(7), remarks, HashSet.empty(), 0))
                        .cmds());

        Picture picture = new Picture(1, 2, Vector.of(0xffff0000, 0xff00ff00));
        Model shown = Update.update(
                        read.model(), new RemarksMsg.PictureLoaded(new PrNumber(7), icon, Option.of(picture)))
                .model();
        assertEquals(
                Option.of(Option.of(picture)),
                RemarksState.files(shown).get().read().pictures().get(icon));
    }

    @Test
    void cKeepsCyclingThroughWhichFilesOfAPullRequestAreListedWhenItsFilesAreReadAgain() {
        Model opened = openedPullRequest();
        Model unresolved =
                Update.update(opened, new CommitFilesMsg.ListedCycled()).model();
        assertEquals(
                CommitFilesView.Listed.UNRESOLVED,
                unresolved.repoView().files().commitFiles().get().listed());
        Model remarked =
                Update.update(unresolved, new CommitFilesMsg.ListedCycled()).model();
        assertEquals(
                CommitFilesView.Listed.REMARKED,
                remarked.repoView().files().commitFiles().get().listed());
        assertEquals(
                CommitFilesView.Listed.ALL,
                Update.update(remarked, new CommitFilesMsg.ListedCycled())
                        .model()
                        .repoView()
                        .files()
                        .commitFiles()
                        .get()
                        .listed());

        Model moved = Update.update(
                        withFiles(remarked),
                        new RemarksMsg.Loaded(
                                new PrNumber(7), remarks("c".repeat(40), "b".repeat(40)), HashSet.empty(), 0))
                .model();
        assertEquals(
                CommitFilesView.Listed.REMARKED,
                moved.repoView().files().commitFiles().get().listed());
    }

    @Test
    void whenThePullRequestMovedOnItsFilesAreReadAgainAtItsNewCommits() {
        Next moved = Update.update(
                withFiles(openedPullRequest()),
                new RemarksMsg.Loaded(new PrNumber(7), remarks("c".repeat(40), "b".repeat(40)), HashSet.empty(), 0));

        Changes now = new Changes.OfPullRequest(new PullRequestCommits(
                new PrNumber(7), "owner/repo", "", new Sha("b".repeat(40)), new Sha("c".repeat(40))));
        PrFiles view = RemarksState.files(moved.model()).get();
        assertEquals(now, view.of());
        assertEquals(new Loading<>(), view.view().files());
        assertEquals(List.of(THREAD), view.threads());
        assertEquals(List.of(new LoadCommitFiles(now)), moved.cmds());
        // The files are read again at the commits the threads were read at.
        assertTrue(view.remarksAtItsCommits());
    }

    @Test
    void aPullRequestThatMovedOnStaysOfTheBranchItWasOpenedFrom() {
        Changes ofFeature = new Changes.OfPullRequest(COMMITS, Option.of(new BranchName("feature")));
        Model opened = Update.update(
                        loadedWithTheFeature(), new CommitFilesMsg.Opened(ofFeature, Scopes.BRANCHES.pane()))
                .model();

        Next moved = Update.update(
                opened,
                new RemarksMsg.Loaded(new PrNumber(7), remarks("c".repeat(40), "b".repeat(40)), HashSet.empty(), 0));

        assertEquals(
                new Changes.OfPullRequest(
                        new PullRequestCommits(
                                new PrNumber(7), "owner/repo", "", new Sha("b".repeat(40)), new Sha("c".repeat(40))),
                        Option.of(new BranchName("feature"))),
                RemarksState.files(moved.model()).get().of());
    }

    @Test
    void allFilesOfAPullRequestThatMovedOnAreListedStillAndReadAgainAtItsHead() {
        Model all = Update.update(withFiles(openedPullRequest()), new CommitFilesMsg.AllToggled())
                .model();

        Next moved = Update.update(
                all,
                new RemarksMsg.Loaded(new PrNumber(7), remarks("c".repeat(40), "b".repeat(40)), HashSet.empty(), 0));

        Changes now = new Changes.OfPullRequest(new PullRequestCommits(
                new PrNumber(7), "owner/repo", "", new Sha("b".repeat(40)), new Sha("c".repeat(40))));
        PrFiles view = RemarksState.files(moved.model()).get();
        assertTrue(view.view().allFiles());
        assertEquals(Option.of(new Loading<>()), view.view().tree());
        assertEquals(List.of(new LoadCommitFiles(now), new LoadCommitTree(now)), moved.cmds());
    }

    @Test
    void theChangedFilesOfAPullRequestThatMovedOnReadNotAllItsFiles() {
        Next moved = Update.update(
                withFiles(openedPullRequest()),
                new RemarksMsg.Loaded(new PrNumber(7), remarks("c".repeat(40), "b".repeat(40)), HashSet.empty(), 0));

        PrFiles view = RemarksState.files(moved.model()).get();
        assertFalse(view.view().allFiles());
        assertEquals(Option.none(), view.view().tree());
        assertFalse(moved.cmds().exists(LoadCommitTree.class::isInstance));
    }

    @Test
    void theFilterOfTheFilesOfAPullRequestStaysWhenItMovedOn() {
        Option<FileFilter> filter = Option.of(new FileFilter("Test", Option.none()));
        Model filtered = withFiles(openedPullRequest())
                .mapRepoView(repoView -> repoView.mapFiles(
                        files -> files.withCommitFiles(files.commitFiles().map(view -> view.withFilter(filter)))));

        Model moved = Update.update(
                        filtered,
                        new RemarksMsg.Loaded(
                                new PrNumber(7), remarks("c".repeat(40), "b".repeat(40)), HashSet.empty(), 0))
                .model();

        assertEquals(filter, moved.repoView().files().commitFiles().get().filter());
        // Other changes opened in their place start without a filter.
        assertEquals(
                Option.none(),
                Update.update(
                                moved,
                                new CommitFilesMsg.Opened(
                                        new Changes.OfCommit(new Sha("a".repeat(40))), new PaneId("x")))
                        .model()
                        .repoView()
                        .files()
                        .commitFiles()
                        .get()
                        .filter());
    }

    @Test
    void aPullRequestFoundMergedOrClosedStaysAtTheCommitsItWasReadAtAndSaysSo() {
        for (PullRequestDetails.State state :
                List.of(PullRequestDetails.State.MERGED, PullRequestDetails.State.CLOSED)) {
            Next read = Update.update(
                    withFiles(openedPullRequest()),
                    new RemarksMsg.Loaded(
                            new PrNumber(7), remarks(state, "c".repeat(40), "b".repeat(40)), HashSet.empty(), 0));

            PrFiles view = RemarksState.files(read.model()).get();
            assertEquals(PULL_REQUEST, view.of());
            assertEquals(Option.of(state), view.ended());
            assertEquals(List.of(THREAD), view.threads());
            // Read at other commits than the files: their lines number other lines than the diff shows.
            assertFalse(view.remarksAtItsCommits());
            assertEquals(List.empty(), read.cmds());
        }
        assertEquals(
                Option.none(), RemarksState.files(openedPullRequest()).get().ended());
    }

    @Test
    void theThreadsOfAnotherPullRequestDoNothing() {
        Model other = openedPullRequest();
        assertEquals(
                other,
                Update.update(
                                other,
                                new RemarksMsg.Loaded(
                                        new PrNumber(8), remarks("a".repeat(40), "b".repeat(40)), HashSet.empty(), 0))
                        .model());
    }

    @Test
    void theRemarksAreStatedForTheFilesOfThePullRequestOnly() {
        Model read = Update.update(
                        openedPullRequest(),
                        new RemarksMsg.Loaded(
                                new PrNumber(7), remarks("a".repeat(40), "b".repeat(40)), HashSet.empty(), 0))
                .model();
        assertEquals(
                List.of(FromGitHub.thread(THREAD)),
                read.remarks(PULL_REQUEST).get().read());
        assertTrue(read.remarks(PULL_REQUEST).get().atItsCommits());
        assertEquals(Option.none(), read.remarks(COMMIT));
        // The files of a commit opened in their place have none.
        assertEquals(
                Option.none(),
                Update.update(read, new CommitFilesMsg.Opened(COMMIT, Scopes.COMMITS.pane()))
                        .model()
                        .remarks());
    }

    @Test
    void aDeletedCommentLeavesItsThreadAndTheThreadGoesWithItsLastUnlessGitHubHasMore() {
        ReviewThread read = THREAD.withComments(List.of(comment("C")));
        ReviewThread more = new ReviewThread(
                "a.txt",
                ReviewThread.Side.RIGHT,
                Option.of(1),
                Option.none(),
                ReviewThread.Side.RIGHT,
                Option.of(1),
                false,
                false,
                ReviewThread.Subject.LINE,
                List.of(comment("M")),
                3,
                new ReviewThread.Id("TM"),
                ReviewThread.Rights.ALL);
        PrRemarks remarks = PrRemarks.reading(new PrNumber(7))
                .withRemarks(new Loaded<>(new ReviewRemarks(
                        PullRequestDetails.State.OPEN, Option.none(), Option.none(), List.of(read, more))));

        PrRemarks deleted = remarks.withoutComment(new ReviewThread.Comment.Id("C"))
                .withoutComment(new ReviewThread.Comment.Id("M"));

        assertEquals(List.of(new ReviewThread.Id("TM")), deleted.read().map(ReviewThread::id));
        assertEquals(List.empty(), deleted.read().head().comments());
        assertEquals(2, deleted.version());
        // The kernel's threads follow.
        assertEquals(List.empty(), deleted.threads().toOption().get().head().remarks());
        // One that is gone already changes nothing, not even the version.
        assertEquals(deleted, deleted.withoutComment(new ReviewThread.Comment.Id("C")));
    }

    @Test
    void aCommentEditedSaysWhatGitHubHoldsAtANewerVersion() {
        PrRemarks remarks = PrRemarks.reading(new PrNumber(7))
                .withRemarks(new Loaded<>(new ReviewRemarks(
                        PullRequestDetails.State.OPEN,
                        Option.none(),
                        Option.none(),
                        List.of(THREAD.withComments(List.of(comment("C")))))));

        PrRemarks edited = remarks.withEdited(new ReviewThread.Comment.Id("C"), "Hm?");

        assertEquals(List.of("Hm?"), edited.read().head().comments().map(ReviewThread.Comment::body));
        assertEquals(
                List.of("Hm?"),
                edited.threads().toOption().get().head().remarks().map(each -> each.body()));
        assertEquals(1, edited.version());
        // One that is gone changes nothing, not even the version.
        assertEquals(edited, edited.withEdited(new ReviewThread.Comment.Id("X"), "Hm!"));
    }

    private static ReviewThread.Comment comment(String id) {
        return new ReviewThread.Comment(
                "bob",
                Instant.EPOCH,
                "Hm",
                Option.none(),
                new ReviewThread.Comment.Id(id),
                new ReviewThread.Comment.RestId("1"),
                CommentRights.ALL);
    }

    private static Model withFiles(Model model) {
        return Update.update(model, new LoadMsg.CommitFilesLoaded(COMMIT, List.of(A, B)))
                .model();
    }
}
