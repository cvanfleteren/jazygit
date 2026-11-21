package net.vanfleteren.jazygit.features.changes.diff;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Stash;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Loadable.Loaded;
import net.vanfleteren.jazygit.kernel.state.Loadable.Loading;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.load.LoadCommitFileDiff;
import net.vanfleteren.jazygit.kernel.state.load.LoadCommitFiles;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.DivergenceView;
import net.vanfleteren.jazygit.kernel.state.view.FilesLock;
import net.vanfleteren.jazygit.kernel.state.view.Followed;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

class CommitFilesUpdateTest {

    private static final Sha SHA = new Sha("aaaa");
    private static final Changes COMMIT = new Changes.OfCommit(SHA);
    private static final CommitFile A = new CommitFile("a.txt", ChangeType.MODIFIED);
    private static final CommitFile B = new CommitFile("dir/b.txt", ChangeType.ADDED);

    @Test
    void openingListsTheFilesInTheFilesPaneAndReadsThem() {
        Next next = Update.update(loaded(), new CommitFilesMsg.Opened(COMMIT, Scopes.COMMITS.pane()));

        assertEquals(
                Option.of(CommitFilesView.of(COMMIT, Scopes.COMMITS.pane(), Scopes.FILES.pane())),
                next.model().repoView().files().commitFiles());
        assertEquals(List.of(new LoadCommitFiles(COMMIT)), next.cmds());
    }

    @Test
    void theFilesReadAreListedAndAHighlightedNodeReadsItsDiff() {
        Model open = withFiles(opened());

        assertEquals(
                new Loaded<>(List.of(A, B)),
                open.repoView().files().commitFiles().get().files());

        Next selected = Update.update(open, new SelectionMsg.CommitFilesSelected(List.of(A)));

        assertEquals(List.of(new LoadCommitFileDiff(COMMIT, List.of(A), DiffOptions.DEFAULT)), selected.cmds());
        Model read = Update.update(
                        selected.model(),
                        new LoadMsg.CommitFileDiffLoaded(COMMIT, List.of(A), DiffOptions.DEFAULT, DiffText.of("+a")))
                .model();
        assertEquals(
                Option.of(new Preview<>(List.of(A), new Loaded<>(DiffText.of("+a")))),
                read.repoView().files().commitFiles().get().diff());
    }

    @Test
    void whatIsReadForAnotherCommitIsDropped() {
        Model open = opened();

        Model other = Update.update(
                        open, new LoadMsg.CommitFilesLoaded(new Changes.OfCommit(new Sha("bbbb")), List.of(A)))
                .model();

        assertEquals(
                new Loading<>(), other.repoView().files().commitFiles().get().files());
    }

    @Test
    void escapeLeavesThemListedAsTheAppMovesTheFocusBackAndLetsGoOfTheLock() {
        Model open = withFiles(opened());

        Model closed = Update.update(open, new CommitFilesMsg.Closed()).model();

        assertEquals(
                open.repoView().files().commitFiles(), closed.repoView().files().commitFiles());
        assertEquals(Option.none(), closed.repoView().files().locked());
    }

    @Test
    void openingLocksTheFilesToTheListItWasOpenedInEachTimeAnew() {
        Model once = opened();
        Model twice = Update.update(once, new CommitFilesMsg.Opened(COMMIT, Scopes.COMMITS.pane()))
                .model();

        assertEquals(
                Option.of(new FilesLock(Scopes.COMMITS.pane(), FilesLock.In.LIST, Option.none(), 1)),
                once.repoView().files().locked());
        // Enter again moves the focus into them again: a new lock, of the files already listed.
        assertEquals(Option.of(2), twice.repoView().files().locked().map(FilesLock::serial));
        assertEquals(
                once.repoView().files().commitFiles(), twice.repoView().files().commitFiles());
    }

    @Test
    void enterInADivergenceLocksTheFilesToIt() {
        Model diverged = loaded().mapRepoView(repoView ->
                repoView.mapBranches(branches -> branches.withDivergence(Option.of(DivergenceView.loading(
                        new BranchName("feature"),
                        new BranchRef.Local(new BranchName("main")),
                        DivergenceView.Against.BASE)))));

        Model open = Update.update(diverged, new CommitFilesMsg.Opened(COMMIT, Scopes.BRANCHES.pane()))
                .model();

        assertEquals(
                Option.of(FilesLock.In.DIVERGENCE),
                open.repoView().files().locked().map(FilesLock::list));
    }

    @Test
    void theFocusMovingLeavesThemListed() {
        // Which files are listed for the pane that gets the focus is the panes' to say (ADR 141).
        Model open = withFiles(opened());

        for (var focus : List.of(Scopes.FILES.pane(), new AreaId("commit-file-changes"), Scopes.COMMITS.pane())) {
            assertTrue(Update.update(open, new ViewMsg.Focused(focus))
                    .model()
                    .repoView()
                    .files()
                    .commitFiles()
                    .isDefined());
        }
    }

    @Test
    void fOnTheCommitWhoseFilesFollowTheHighlightKeepsThemAndNoLongerFollows() {
        Model following = Update.update(
                        loaded(),
                        new SelectionMsg.FilesFollowed(
                                new Followed.Files(COMMIT), Scopes.COMMITS.pane(), Scopes.FILES.pane()))
                .model();

        Next next = Update.update(following, new CommitFilesMsg.Opened(COMMIT, Scopes.COMMITS.pane()));

        assertEquals(
                following.repoView().files().commitFiles().map(view -> view.withFollowed(false)),
                next.model().repoView().files().commitFiles());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theFilesOfAStashCloseWhenItIsDropped() {
        Stash stash = new Stash("stash@{0}", new Sha("5555"), "work", Option.none(), Instant.EPOCH);
        Stash other = new Stash("stash@{0}", new Sha("6666"), "other", Option.none(), Instant.EPOCH);
        Model open = Update.update(
                        loaded(), new CommitFilesMsg.Opened(new Changes.OfStash(stash.sha()), Scopes.STASH.pane()))
                .model();

        Model kept = Update.update(open, new LoadMsg.StashesLoaded(List.of(other, stash)))
                .model();
        Model dropped =
                Update.update(kept, new LoadMsg.StashesLoaded(List.of(other))).model();

        assertTrue(kept.repoView().files().commitFiles().isDefined());
        assertEquals(Option.none(), dropped.repoView().files().commitFiles());
    }

    @Test
    void switchingTheStyleKeepsItReadsMergesAgainstTheirFirstParentAndReadsTheDiffAgain() {
        Model open = Update.update(
                        Update.update(withFiles(opened()), new SelectionMsg.CommitFilesSelected(List.of(A)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(COMMIT, List.of(A), DiffOptions.DEFAULT, DiffText.of("+a")))
                .model();

        Next next = Update.update(open, new DiffOptionsMsg.StyleChosen(DiffStyle.RICH));

        DiffOptions rich = DiffOptions.DEFAULT.withFirstParent(true);
        assertEquals(DiffStyle.RICH, next.model().preferences().diffLook().style());
        assertEquals(rich, next.model().preferences().diffOptions());
        assertTrue(next.cmds().contains(new LoadCommitFileDiff(COMMIT, List.of(A), rich)));
        assertTrue(next.cmds()
                .contains(new SaveSettings(
                        open.preferences().settings(),
                        open.preferences().settings().withDiffStyle(DiffStyle.RICH))));
        assertEquals(
                new Loaded<>(DiffText.of("+a")),
                next.model()
                        .repoView()
                        .files()
                        .commitFiles()
                        .get()
                        .diff()
                        .get()
                        .shown()
                        .value());
    }

    @Test
    void theSplitKeyGoesFromThePlainViewToTheSplitOneAndBackToTheRichOneWithoutReadingAgain() {
        Model open = Update.update(
                        Update.update(withFiles(opened()), new SelectionMsg.CommitFilesSelected(List.of(A)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(COMMIT, List.of(A), DiffOptions.DEFAULT, DiffText.of("+a")))
                .model();

        Next split = Update.update(open, new DiffOptionsMsg.SplitToggled());

        DiffOptions firstParent = DiffOptions.DEFAULT.withFirstParent(true);
        assertEquals(DiffStyle.SPLIT, split.model().preferences().diffLook().style());
        assertEquals(firstParent, split.model().preferences().diffOptions());
        assertTrue(split.cmds().contains(new LoadCommitFileDiff(COMMIT, List.of(A), firstParent)));

        Next rich = Update.update(split.model(), new DiffOptionsMsg.SplitToggled());

        // Both are drawn of the same diff: it is only drawn again (ADR 139).
        assertEquals(DiffStyle.RICH, rich.model().preferences().diffLook().style());
        assertFalse(rich.cmds().exists(LoadCommitFileDiff.class::isInstance));
        assertTrue(rich.cmds()
                .contains(new SaveSettings(
                        split.model().preferences().settings(),
                        split.model().preferences().settings().withDiffStyle(DiffStyle.RICH))));
        assertEquals(
                DiffStyle.PLAIN,
                Update.update(split.model(), new DiffOptionsMsg.StyleChosen(DiffStyle.PLAIN))
                        .model()
                        .preferences()
                        .diffLook()
                        .style());
    }

    @Test
    void theKeysOfTheFilesOfACommitWithoutRemarksDoNothing() {
        Model commit = opened();
        assertEquals(
                commit, Update.update(commit, new CommitFilesMsg.ListedCycled()).model());
    }

    private static Model opened() {
        return Update.update(loaded(), new CommitFilesMsg.Opened(COMMIT, Scopes.COMMITS.pane()))
                .model();
    }

    private static Model withFiles(Model model) {
        return Update.update(model, new LoadMsg.CommitFilesLoaded(COMMIT, List.of(A, B)))
                .model();
    }
}
