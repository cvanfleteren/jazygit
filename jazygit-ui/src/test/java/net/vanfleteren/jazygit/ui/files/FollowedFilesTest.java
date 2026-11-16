package net.vanfleteren.jazygit.ui.files;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.BranchCommitsView;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.DivergenceView;
import net.vanfleteren.jazygit.state.view.Followed;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.Unlisted;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * Which files the files pane lists for the pane in use (ADR 141).
 */
class FollowedFilesTest {

    private static final Sha COMMIT = new Sha("aaaa");
    private static final Sha STASH = new Sha("5555");
    private static final Sha BRANCH_COMMIT = new Sha("bbbb");
    private static final Changes BRANCH = new Changes.OfTip(new BranchName("main"), new Sha("cccc"));
    private static final Model MODEL = TestModels.loaded()
            .mapRepoView(view -> view.mapPreview(
                    preview -> preview.withCommitDetail(Option.of(new Preview<>(COMMIT, new Loadable.Loading<>())))
                            .withStashDetail(Option.of(new Preview<>(STASH, new Loadable.Loading<>())))));

    @Test
    void theCommitsListTheFilesOfTheHighlightedCommit() {
        assertEquals(
                Option.of(followed(new Followed.Files(new Changes.OfCommit(COMMIT)), Scopes.COMMITS.pane())),
                change(MODEL, Scopes.COMMITS.pane()));
    }

    @Test
    void theStashesListTheFilesOfTheHighlightedStash() {
        assertEquals(
                Option.of(followed(new Followed.Files(new Changes.OfStash(STASH)), Scopes.STASH.pane())),
                change(MODEL, Scopes.STASH.pane()));
    }

    @Test
    void theCommitsOfABranchListTheFilesOfTheHighlightedOneRatherThanTheBranch() {
        Model model = MODEL.mapRepoView(view ->
                view.mapBranches(branches -> branches.withBranchCommits(Option.of(new BranchCommitsView(
                        new BranchRef.Local(new BranchName("feature")),
                        Option.of(Preview.loading(BRANCH_COMMIT, Option.none())))))));

        assertEquals(
                Option.of(followed(new Followed.Files(new Changes.OfCommit(BRANCH_COMMIT)), Scopes.BRANCHES.pane())),
                change(model, Scopes.BRANCHES.pane(), new Followed.Files(BRANCH)));
    }

    @Test
    void theDivergenceListsTheFilesOfTheHighlightedCommit() {
        Model model = MODEL.mapRepoView(view ->
                view.mapBranches(branches -> branches.withDivergence(Option.of(DivergenceView.loading(
                                new BranchName("feature"),
                                new BranchRef.Local(new BranchName("main")),
                                DivergenceView.Against.UPSTREAM)
                        .withDetail(Option.of(Preview.loading(BRANCH_COMMIT, Option.none())))))));

        assertEquals(
                Option.of(followed(new Followed.Files(new Changes.OfCommit(BRANCH_COMMIT)), Scopes.BRANCHES.pane())),
                change(model, Scopes.BRANCHES.pane(), new Followed.Files(BRANCH)));
    }

    @Test
    void theSameCommitHighlightedInAnotherListFollowsThatList() {
        // Escape goes back to the list the files are of (ADR 141): the commits of the branch, not the commits.
        Model model = listing(MODEL)
                .mapRepoView(view ->
                        view.mapBranches(branches -> branches.withBranchCommits(Option.of(new BranchCommitsView(
                                new BranchRef.Local(new BranchName("feature")),
                                Option.of(Preview.loading(COMMIT, Option.none())))))));

        assertEquals(
                Option.of(followed(new Followed.Files(new Changes.OfCommit(COMMIT)), Scopes.BRANCHES.pane())),
                change(model, Scopes.BRANCHES.pane(), new Followed.Files(BRANCH)));
    }

    @Test
    void theBranchesListWhatTheirHighlightedRowFollows() {
        assertEquals(
                Option.of(followed(new Followed.Files(BRANCH), Scopes.BRANCHES.pane())),
                change(MODEL, Scopes.BRANCHES.pane(), new Followed.Files(BRANCH)));

        Followed nothing = new Followed.Nothing(new Unlisted.Stack());
        assertEquals(
                Option.of(followed(nothing, Scopes.BRANCHES.pane())), change(MODEL, Scopes.BRANCHES.pane(), nothing));
        assertEquals(Option.none(), change(unlisted(MODEL), Scopes.BRANCHES.pane(), nothing));
    }

    @Test
    void aBranchNotKnownYetChangesNothing() {
        assertEquals(
                Option.none(), FollowedFiles.change(listing(MODEL), Option.of(Scopes.BRANCHES.pane()), Option.none()));
    }

    @Test
    void nothingChangesWhileTheFilesTheyFollowAreListed() {
        assertEquals(Option.none(), change(listing(MODEL), Scopes.COMMITS.pane()));
    }

    @Test
    void theFilesFAskedForFollowAgainOnceTheirListHasTheFocus() {
        Model asked = listing(MODEL)
                .mapRepoView(view -> view.mapFiles(
                        files -> files.withCommitFiles(files.commitFiles().map(open -> open.withFollowed(false)))));

        assertEquals(
                Option.of(followed(new Followed.Files(new Changes.OfCommit(COMMIT)), Scopes.COMMITS.pane())),
                change(asked, Scopes.COMMITS.pane()));
    }

    @Test
    void theFilesFAskedForStayWhileTheFocusIsInThem() {
        // F on a branch with a pull request lists those of the pull request, though the branch follows its own.
        Model asked = listing(MODEL)
                .mapRepoView(view -> view.mapFiles(
                        files -> files.withCommitFiles(files.commitFiles().map(open -> open.withFollowed(false)))));

        assertEquals(Option.none(), change(asked, FilesPanel.PANE, new Followed.Files(BRANCH)));
    }

    @Test
    void theFilesPaneKeepsWhatItLists() {
        assertEquals(Option.none(), change(listing(MODEL), FilesPanel.PANE));
        assertEquals(Option.none(), change(MODEL, FilesPanel.PANE));
    }

    @Test
    void aPaneThatFollowsNothingListsTheWorkingTreeAgain() {
        for (Model listed : java.util.List.of(listing(MODEL), unlisted(MODEL))) {
            assertEquals(
                    Option.of(followed(new Followed.WorkingTree(), Scopes.WORKTREES.pane())),
                    change(listed, Scopes.WORKTREES.pane()));
        }
        assertEquals(Option.none(), change(MODEL, Scopes.WORKTREES.pane()));
    }

    @Test
    void withNoPaneInUseNothingChanges() {
        assertEquals(
                Option.none(),
                FollowedFiles.change(listing(MODEL), Option.none(), Option.of(new Followed.WorkingTree())));
    }

    private static Option<?> change(Model model, PaneId inUse) {
        return change(model, inUse, new Followed.WorkingTree());
    }

    private static Option<?> change(Model model, PaneId inUse, Followed branches) {
        return FollowedFiles.change(model, Option.of(inUse), Option.of(branches));
    }

    private static Model listing(Model model) {
        return model.mapRepoView(view -> view.mapFiles(files -> files.withCommitFiles(
                Option.of(CommitFilesView.of(new Changes.OfCommit(COMMIT), Scopes.COMMITS.pane(), FilesPanel.PANE)
                        .withFollowed(true)))));
    }

    private static Model unlisted(Model model) {
        return model.mapRepoView(view -> view.mapFiles(files -> files.withUnlisted(Option.of(new Unlisted.Stack()))));
    }

    private static SelectionMsg.FilesFollowed followed(Followed followed, PaneId from) {
        return new SelectionMsg.FilesFollowed(followed, from, FilesPanel.PANE);
    }
}
