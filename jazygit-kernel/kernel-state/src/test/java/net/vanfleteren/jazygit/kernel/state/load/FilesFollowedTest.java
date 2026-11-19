package net.vanfleteren.jazygit.kernel.state.load;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Loadable.Loaded;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.FileFilter;
import net.vanfleteren.jazygit.kernel.state.view.FilesLock;
import net.vanfleteren.jazygit.kernel.state.view.Followed;
import net.vanfleteren.jazygit.kernel.state.view.Unlisted;
import org.junit.jupiter.api.Test;

/**
 * The files pane listing the files of what the list with the focus highlights (ADR 141).
 */
class FilesFollowedTest {

    private static final PaneId COMMITS = new PaneId("commits");
    private static final PaneId FILES = new PaneId("files");
    private static final Changes FIRST = new Changes.OfCommit(new Sha("aaaa"));
    private static final Changes SECOND = new Changes.OfCommit(new Sha("bbbb"));
    private static final Changes THIRD = new Changes.OfCommit(new Sha("cccc"));
    private static final CommitFile A = new CommitFile("a.txt", ChangeType.MODIFIED);

    @Test
    void theFilesOfTheHighlightedCommitAreListedWithoutTakingTheFocusAndRead() {
        Next next = followed(loaded(), FIRST);

        assertEquals(
                Option.of(CommitFilesView.of(FIRST, COMMITS, FILES).withFollowed(true)),
                next.model().repoView().files().commitFiles());
        assertEquals(List.of(new LoadCommitFiles(FIRST)), next.cmds());
    }

    @Test
    void readingTheFilesOfTheNextOneCancelsThoseOfTheOneBeforeThatDidNotStart() {
        assertEquals(Option.of(Cmd.Slot.COMMIT_FILES), new LoadCommitFiles(FIRST).slot());
    }

    @Test
    void theFilesAlreadyListedStayAsTheyAre() {
        Model listed = followed(loaded(), FIRST).model();

        Next again = followed(listed, FIRST);

        assertSame(listed, again.model());
        assertEquals(List.of(), again.cmds());
    }

    @Test
    void thoseFAskedForFollowTheHighlightAgainKeepingWhatWasRead() {
        Model asked = followed(loaded(), FIRST)
                .model()
                .mapRepoView(repoView -> repoView.mapFiles(
                        files -> files.withCommitFiles(files.commitFiles().map(view -> view.withFollowed(false)))));

        Next again = followed(asked, FIRST);

        assertEquals(
                asked.repoView().files().commitFiles().map(view -> view.withFollowed(true)),
                again.model().repoView().files().commitFiles());
        assertEquals(List.of(), again.cmds());
    }

    @Test
    void theFilesReadBeforeStayListedAMomentWhileTheNextAreRead() {
        Instant now = Instant.parse("2026-11-16T10:00:00Z");
        Model read = Update.update(
                        followed(loaded().withNow(now), FIRST).model(),
                        new LoadMsg.CommitFilesLoaded(FIRST, List.of(A)))
                .model();
        CommitFilesView first = read.repoView().files().commitFiles().get();

        CommitFilesView next =
                followed(read, SECOND).model().repoView().files().commitFiles().get();

        assertEquals(first, next.shown(now.plusMillis(199)));
        assertEquals(next, next.shown(now.plus(CommitFilesView.KEPT_WHILE_READ)));
        CommitFilesView nextRead = next.withFiles(new Loaded<>(List.of(A)));
        assertEquals(nextRead, nextRead.shown(now));
    }

    @Test
    void whileTheNextAreStillReadTheFilesReadBeforeKeepTheirMoment() {
        Instant now = Instant.parse("2026-11-16T10:00:00Z");
        Model read = Update.update(
                        followed(loaded().withNow(now), FIRST).model(),
                        new LoadMsg.CommitFilesLoaded(FIRST, List.of(A)))
                .model();
        CommitFilesView first = read.repoView().files().commitFiles().get();
        Model reading = followed(read, SECOND).model();

        CommitFilesView third = followed(reading.withNow(now.plusMillis(150)), THIRD)
                .model()
                .repoView()
                .files()
                .commitFiles()
                .get();

        assertEquals(first, third.shown(now.plusMillis(199)));
        assertEquals(third, third.shown(now.plusMillis(200)));
    }

    @Test
    void theSameCommitFollowedFromAnotherListIsOfThatListKeepingWhatWasRead() {
        PaneId branches = new PaneId("branches");
        Model fromCommits = followed(loaded(), FIRST).model();

        Next fromBranches =
                Update.update(fromCommits, new SelectionMsg.FilesFollowed(new Followed.Files(FIRST), branches, FILES));

        assertEquals(
                fromCommits.repoView().files().commitFiles().map(view -> view.withPane(branches)),
                fromBranches.model().repoView().files().commitFiles());
        assertEquals(List.of(), fromBranches.cmds());
    }

    @Test
    void theFilterGoesAlongToTheNextButAllFilesDoNot() {
        Model listed = followed(loaded(), FIRST)
                .model()
                .mapRepoView(repoView -> repoView.mapFiles(files -> files.withCommitFiles(files.commitFiles()
                        .map(view -> view.withFilter(Option.of(new FileFilter("Test", Option.none())))
                                .withAllFiles(true)))));

        CommitFilesView next = followed(listed, SECOND)
                .model()
                .repoView()
                .files()
                .commitFiles()
                .get();

        assertEquals(SECOND, next.of());
        assertEquals(Option.of(new FileFilter("Test", Option.none())), next.filter());
        assertFalse(next.allFiles());
    }

    @Test
    void theWorkingTreeFollowedListsItsFilesAgain() {
        Model listed = followed(loaded(), FIRST).model();

        Next next = Update.update(listed, new SelectionMsg.FilesFollowed(new Followed.WorkingTree(), COMMITS, FILES));

        assertEquals(new Followed.WorkingTree(), next.model().repoView().files().followed());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void nothingToListSaysWhyInPlaceOfTheFilesUntilFilesAreFollowedAgain() {
        Followed nothing = new Followed.Nothing(new Unlisted.NoBase(new BranchName("feature")));
        Model unlisted = Update.update(
                        followed(loaded(), FIRST).model(), new SelectionMsg.FilesFollowed(nothing, COMMITS, FILES))
                .model();

        assertEquals(nothing, unlisted.repoView().files().followed());
        // Escape there goes back to the list whose row has nothing to list.
        assertEquals(Option.of(COMMITS), unlisted.repoView().files().backTo());

        assertEquals(
                new Followed.Files(SECOND),
                followed(unlisted, SECOND).model().repoView().files().followed());
    }

    @Test
    void manyFilesThatFollowTheHighlightAreCountedUntilAskedForWhole() {
        List<CommitFile> many = List.range(0, CommitFilesView.FOLLOWED_LIMIT + 1)
                .map(i -> new CommitFile("f" + i, ChangeType.MODIFIED));
        Model read = Update.update(followed(loaded(), FIRST).model(), new LoadMsg.CommitFilesLoaded(FIRST, many))
                .model();
        CommitFilesView held = read.repoView().files().commitFiles().get();

        assertEquals(Option.of(CommitFilesView.FOLLOWED_LIMIT + 1), held.held());
        assertEquals(Option.none(), held.withFiles(new Loaded<>(many.tail())).held());
        assertEquals(Option.none(), held.withAllFiles(true).held());
        assertEquals(Option.none(), held.withFollowed(false).held());

        Model whole = Update.update(read, new SelectionMsg.WholeListed()).model();
        assertEquals(Option.none(), whole.repoView().files().commitFiles().get().held());
        assertEquals(
                Option.of(CommitFilesView.FOLLOWED_LIMIT + 1),
                followed(whole, SECOND)
                        .model()
                        .repoView()
                        .files()
                        .commitFiles()
                        .map(view -> view.withFiles(new Loaded<>(many)))
                        .flatMap(CommitFilesView::held));
    }

    @Test
    void theListTheFilesAreLockedToRemembersWhatItFollowsAsTheyAreListedForIt() {
        // Locked to the commits (ADR 149): the next move of their highlight lists anew from what this one lists.
        Model locked = loaded().mapRepoView(
                        repoView -> repoView.mapFiles(files -> files.lockedTo(COMMITS, FilesLock.In.LIST)));

        Model listed = followed(locked, FIRST).model();

        assertEquals(
                Option.of(new FilesLock(COMMITS, FilesLock.In.LIST, Option.of(new Followed.Files(FIRST)), 1)),
                listed.repoView().files().locked());
    }

    @Test
    void theFrameAfterTheLockTellsWhatTheListFollowsAndEscapeLetsGo() {
        Model locked = loaded().mapRepoView(
                        repoView -> repoView.mapFiles(files -> files.lockedTo(COMMITS, FilesLock.In.LIST)));

        Model at = Update.update(locked, new SelectionMsg.FilesLockedAt(new Followed.Files(FIRST)))
                .model();
        Model unlocked = Update.update(at, new SelectionMsg.FilesUnlocked()).model();

        assertEquals(
                Option.of(new Followed.Files(FIRST)),
                at.repoView().files().locked().flatMap(FilesLock::at));
        assertEquals(Option.none(), unlocked.repoView().files().locked());
    }

    private static Next followed(Model model, Changes of) {
        return Update.update(model, new SelectionMsg.FilesFollowed(new Followed.Files(of), COMMITS, FILES));
    }
}
