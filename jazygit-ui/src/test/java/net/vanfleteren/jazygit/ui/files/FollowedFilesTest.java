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
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * Which files the files pane lists for the pane in use (ADR 141).
 */
class FollowedFilesTest {

    private static final Sha COMMIT = new Sha("aaaa");
    private static final Sha STASH = new Sha("5555");
    private static final Sha BRANCH_COMMIT = new Sha("bbbb");
    private static final Model MODEL = TestModels.loaded()
            .mapRepoView(view -> view.mapPreview(
                    preview -> preview.withCommitDetail(Option.of(new Preview<>(COMMIT, new Loadable.Loading<>())))
                            .withStashDetail(Option.of(new Preview<>(STASH, new Loadable.Loading<>())))));

    @Test
    void theCommitsListTheFilesOfTheHighlightedCommit() {
        assertEquals(
                Option.of(followed(new Changes.OfCommit(COMMIT), Scopes.COMMITS.pane())),
                change(MODEL, Scopes.COMMITS.pane()));
    }

    @Test
    void theStashesListTheFilesOfTheHighlightedStash() {
        assertEquals(
                Option.of(followed(new Changes.OfStash(STASH), Scopes.STASH.pane())),
                change(MODEL, Scopes.STASH.pane()));
    }

    @Test
    void theCommitsOfABranchListTheFilesOfTheHighlightedOne() {
        Model model = MODEL.mapRepoView(view ->
                view.mapBranches(branches -> branches.withBranchCommits(Option.of(new BranchCommitsView(
                        new BranchRef.Local(new BranchName("feature")),
                        Option.of(Preview.loading(BRANCH_COMMIT, Option.none())))))));

        assertEquals(
                Option.of(followed(new Changes.OfCommit(BRANCH_COMMIT), Scopes.BRANCHES.pane())),
                change(model, Scopes.BRANCHES.pane()));
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
                Option.of(followed(new Changes.OfCommit(COMMIT), Scopes.COMMITS.pane())),
                change(asked, Scopes.COMMITS.pane()));
    }

    @Test
    void theFilesPaneKeepsWhatItLists() {
        assertEquals(Option.none(), change(listing(MODEL), FilesPanel.PANE));
        assertEquals(Option.none(), change(MODEL, FilesPanel.PANE));
    }

    @Test
    void anotherPaneListsTheWorkingTreeAgain() {
        assertEquals(
                Option.of(new SelectionMsg.FilesFollowed(Option.none(), Scopes.WORKTREES.pane(), FilesPanel.PANE)),
                change(listing(MODEL), Scopes.WORKTREES.pane()));
        assertEquals(Option.none(), change(MODEL, Scopes.WORKTREES.pane()));
    }

    @Test
    void theBranchesThemselvesFollowNothingYet() {
        assertEquals(Option.none(), change(MODEL, Scopes.BRANCHES.pane()));
    }

    @Test
    void withNoPaneInUseNothingChanges() {
        assertEquals(Option.none(), FollowedFiles.change(listing(MODEL), Option.none()));
    }

    private static Option<?> change(Model model, PaneId inUse) {
        return FollowedFiles.change(model, Option.of(inUse));
    }

    private static Model listing(Model model) {
        return model.mapRepoView(view -> view.mapFiles(files -> files.withCommitFiles(
                Option.of(CommitFilesView.of(new Changes.OfCommit(COMMIT), Scopes.COMMITS.pane(), FilesPanel.PANE)
                        .withFollowed(true)))));
    }

    private static SelectionMsg.FilesFollowed followed(Changes of, PaneId from) {
        return new SelectionMsg.FilesFollowed(Option.of(of), from, FilesPanel.PANE);
    }
}
