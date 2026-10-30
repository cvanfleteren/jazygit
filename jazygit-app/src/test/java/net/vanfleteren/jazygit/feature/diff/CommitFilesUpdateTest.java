package net.vanfleteren.jazygit.feature.diff;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.CommitFilesView;
import net.vanfleteren.jazygit.state.CommitFilesView.Changes;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Loadable.Loading;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.state.SelectionMsg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

class CommitFilesUpdateTest {

    private static final Sha SHA = new Sha("aaaa");
    private static final Changes COMMIT = new Changes.OfCommit(SHA);
    private static final CommitFile A = new CommitFile("a.txt", ChangeType.MODIFIED);
    private static final CommitFile B = new CommitFile("dir/b.txt", ChangeType.ADDED);

    @Test
    void openingListsTheFilesInTheFilesPaneAndReadsThem() {
        Next next = Update.update(loaded(), new CommitFilesMsg.Opened(COMMIT, Scopes.COMMITS.id()));

        assertEquals(
                Option.of(CommitFilesView.of(COMMIT, Scopes.COMMITS.id(), Scopes.FILES.id())),
                next.model().repoView().commitFiles());
        assertEquals(List.of(new Cmd.LoadCommitFiles(COMMIT)), next.cmds());
    }

    @Test
    void theFilesReadAreListedAndAHighlightedNodeReadsItsDiff() {
        Model open = withFiles(opened());

        assertEquals(
                new Loaded<>(List.of(A, B)), open.repoView().commitFiles().get().files());

        Next selected = Update.update(open, new SelectionMsg.CommitFilesSelected(List.of(A)));

        assertEquals(List.of(new Cmd.LoadCommitFileDiff(COMMIT, List.of(A), DiffOptions.DEFAULT)), selected.cmds());
        Model read = Update.update(
                        selected.model(),
                        new LoadMsg.CommitFileDiffLoaded(COMMIT, List.of(A), DiffOptions.DEFAULT, DiffText.of("+a")))
                .model();
        assertEquals(
                Option.of(new Preview<>(List.of(A), new Loaded<>(DiffText.of("+a")))),
                read.repoView().commitFiles().get().diff());
    }

    @Test
    void whatIsReadForAnotherCommitIsDropped() {
        Model open = opened();

        Model other = Update.update(
                        open, new LoadMsg.CommitFilesLoaded(new Changes.OfCommit(new Sha("bbbb")), List.of(A)))
                .model();

        assertEquals(new Loading<>(), other.repoView().commitFiles().get().files());
    }

    @Test
    void escapeClosesThem() {
        Model closed =
                Update.update(withFiles(opened()), new CommitFilesMsg.Closed()).model();

        assertEquals(Option.none(), closed.repoView().commitFiles());
    }

    @Test
    void anotherPaneGettingTheFocusClosesThemButTheirDiffDoesNot() {
        Model open = withFiles(opened());

        assertTrue(Update.update(open, new Msg.Focused(Scopes.FILES.id(), false))
                .model()
                .repoView()
                .commitFiles()
                .isDefined());
        assertTrue(Update.update(open, new Msg.Focused("commit-file-changes", true))
                .model()
                .repoView()
                .commitFiles()
                .isDefined());
        assertEquals(
                Option.none(),
                Update.update(open, new Msg.Focused(Scopes.COMMITS.id(), false))
                        .model()
                        .repoView()
                        .commitFiles());
    }

    @Test
    void theFilesOfAStashCloseWhenItIsDropped() {
        Stash stash = new Stash("stash@{0}", new Sha("5555"), "work", Option.none(), Instant.EPOCH);
        Stash other = new Stash("stash@{0}", new Sha("6666"), "other", Option.none(), Instant.EPOCH);
        Model open = Update.update(
                        loaded(), new CommitFilesMsg.Opened(new Changes.OfStash(stash.sha()), Scopes.STASH.id()))
                .model();

        Model kept = Update.update(open, new LoadMsg.StashesLoaded(List.of(other, stash)))
                .model();
        Model dropped =
                Update.update(kept, new LoadMsg.StashesLoaded(List.of(other))).model();

        assertTrue(kept.repoView().commitFiles().isDefined());
        assertEquals(Option.none(), dropped.repoView().commitFiles());
    }

    @Test
    void switchingTheStyleKeepsItReadsMergesAgainstTheirFirstParentAndReadsTheDiffAgain() {
        Model open = Update.update(
                        Update.update(withFiles(opened()), new SelectionMsg.CommitFilesSelected(List.of(A)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(COMMIT, List.of(A), DiffOptions.DEFAULT, DiffText.of("+a")))
                .model();

        Next next = Update.update(open, new DiffOptionsMsg.StyleToggled());

        DiffOptions rich = DiffOptions.DEFAULT.withFirstParent(true);
        assertEquals(DiffStyle.RICH, next.model().preferences().diffLook().style());
        assertEquals(rich, next.model().preferences().diffOptions());
        assertTrue(next.cmds().contains(new Cmd.LoadCommitFileDiff(COMMIT, List.of(A), rich)));
        assertTrue(next.cmds()
                .contains(new Cmd.SaveSettings(
                        open.preferences().settings(),
                        open.preferences().settings().withDiffStyle(DiffStyle.RICH))));
        assertEquals(
                new Loaded<>(DiffText.of("+a")),
                next.model().repoView().commitFiles().get().diff().get().shown().value());
    }

    private static final PullRequestCommits COMMITS =
            new PullRequestCommits(7, "owner/repo", "", new Sha("b".repeat(40)), new Sha("a".repeat(40)));
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
            0);

    private static ReviewRemarks remarks(String head, String base) {
        return remarks(PullRequestDetails.State.OPEN, head, base);
    }

    private static ReviewRemarks remarks(PullRequestDetails.State state, String head, String base) {
        return new ReviewRemarks(state, GhCommit.parse(head), GhCommit.parse(base), List.of(THREAD));
    }

    private static Model openedPullRequest() {
        return Update.update(loaded(), new CommitFilesMsg.Opened(PULL_REQUEST, Scopes.BRANCHES.id()))
                .model();
    }

    @Test
    void theFilesOfAPullRequestComeWithItsReviewThreadsWhichCanBeOpenedAndReadAgain() {
        Next opened = Update.update(loaded(), new CommitFilesMsg.Opened(PULL_REQUEST, Scopes.BRANCHES.id()));
        assertEquals(
                List.of(new Cmd.LoadCommitFiles(PULL_REQUEST), new CommitFilesCmd.LoadReviewRemarks(7)), opened.cmds());
        assertEquals(
                Option.of(new Loading<>()),
                opened.model().repoView().commitFiles().get().remarks());

        Model read = Update.update(
                        opened.model(), new CommitFilesMsg.RemarksLoaded(7, remarks("a".repeat(40), "b".repeat(40))))
                .model();
        CommitFilesView view = read.repoView().commitFiles().get();
        assertEquals(List.of(THREAD), view.threads());
        assertEquals(PULL_REQUEST, view.of());
        assertTrue(view.remarksAtItsCommits());

        Model toggled = Update.update(read, new CommitFilesMsg.RemarksToggled()).model();
        assertTrue(toggled.repoView().commitFiles().get().opened());
        assertEquals(
                List.of(new CommitFilesCmd.LoadReviewRemarks(7)),
                Update.update(toggled, new CommitFilesMsg.RemarksRefreshed()).cmds());

        // A failed read again keeps the threads read before.
        Model failed = Update.update(toggled, new CommitFilesMsg.RemarksFailed(7, new Failure.Bug(new Exception())))
                .model();
        assertEquals(List.of(THREAD), failed.repoView().commitFiles().get().threads());
    }

    @Test
    void cKeepsCyclingThroughWhichFilesOfAPullRequestAreListedWhenItsFilesAreReadAgain() {
        Model opened = openedPullRequest();
        Model unresolved =
                Update.update(opened, new CommitFilesMsg.ListedCycled()).model();
        assertEquals(
                CommitFilesView.Listed.UNRESOLVED,
                unresolved.repoView().commitFiles().get().listed());
        Model remarked =
                Update.update(unresolved, new CommitFilesMsg.ListedCycled()).model();
        assertEquals(
                CommitFilesView.Listed.REMARKED,
                remarked.repoView().commitFiles().get().listed());
        assertEquals(
                CommitFilesView.Listed.ALL,
                Update.update(remarked, new CommitFilesMsg.ListedCycled())
                        .model()
                        .repoView()
                        .commitFiles()
                        .get()
                        .listed());

        Model moved = Update.update(
                        withFiles(remarked),
                        new CommitFilesMsg.RemarksLoaded(7, remarks("c".repeat(40), "b".repeat(40))))
                .model();
        assertEquals(
                CommitFilesView.Listed.REMARKED,
                moved.repoView().commitFiles().get().listed());
    }

    @Test
    void whenThePullRequestMovedOnItsFilesAreReadAgainAtItsNewCommits() {
        Next moved = Update.update(
                withFiles(openedPullRequest()),
                new CommitFilesMsg.RemarksLoaded(7, remarks("c".repeat(40), "b".repeat(40))));

        Changes now = new Changes.OfPullRequest(
                new PullRequestCommits(7, "owner/repo", "", new Sha("b".repeat(40)), new Sha("c".repeat(40))));
        CommitFilesView view = moved.model().repoView().commitFiles().get();
        assertEquals(now, view.of());
        assertEquals(new Loading<>(), view.files());
        assertEquals(List.of(THREAD), view.threads());
        assertEquals(List.of(new Cmd.LoadCommitFiles(now)), moved.cmds());
        // The files are read again at the commits the threads were read at.
        assertTrue(view.remarksAtItsCommits());
    }

    @Test
    void aPullRequestFoundMergedOrClosedStaysAtTheCommitsItWasReadAtAndSaysSo() {
        for (PullRequestDetails.State state :
                List.of(PullRequestDetails.State.MERGED, PullRequestDetails.State.CLOSED)) {
            Next read = Update.update(
                    withFiles(openedPullRequest()),
                    new CommitFilesMsg.RemarksLoaded(7, remarks(state, "c".repeat(40), "b".repeat(40))));

            CommitFilesView view = read.model().repoView().commitFiles().get();
            assertEquals(PULL_REQUEST, view.of());
            assertEquals(Option.of(state), view.ended());
            assertEquals(List.of(THREAD), view.threads());
            // Read at other commits than the files: their lines number other lines than the diff shows.
            assertFalse(view.remarksAtItsCommits());
            assertEquals(List.empty(), read.cmds());
        }
        assertEquals(
                Option.none(),
                openedPullRequest().repoView().commitFiles().get().ended());
    }

    @Test
    void theThreadsOfAnotherPullRequestAndTheKeysOfACommitDoNothing() {
        Model other = openedPullRequest();
        assertEquals(
                other,
                Update.update(other, new CommitFilesMsg.RemarksLoaded(8, remarks("a".repeat(40), "b".repeat(40))))
                        .model());

        Model commit = opened();
        assertEquals(
                commit,
                Update.update(commit, new CommitFilesMsg.RemarksToggled()).model());
        assertEquals(
                commit, Update.update(commit, new CommitFilesMsg.ListedCycled()).model());
        assertEquals(
                List.empty(),
                Update.update(commit, new CommitFilesMsg.RemarksRefreshed()).cmds());
    }

    private static Model opened() {
        return Update.update(loaded(), new CommitFilesMsg.Opened(COMMIT, Scopes.COMMITS.id()))
                .model();
    }

    private static Model withFiles(Model model) {
        return Update.update(model, new LoadMsg.CommitFilesLoaded(COMMIT, List.of(A, B)))
                .model();
    }
}
