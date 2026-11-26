package net.vanfleteren.jazygit.features.changes.discard;

import static net.vanfleteren.jazygit.kernel.state.TestModels.*;
import static net.vanfleteren.jazygit.kernel.ui.text.ErrorTexts.errorText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.DiscardPlan;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.features.changes.discard.DiscardCmd.Discard;
import net.vanfleteren.jazygit.features.changes.discard.DiscardMsg.Scope;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import org.junit.jupiter.api.Test;

class DiscardUpdateTest {

    private static final FileEntry UNSTAGED_MOD = Fixtures.fileEntry("unstaged.txt", ChangeType.MODIFIED);
    private static final FileEntry STAGED_MOD =
            new FileEntry(new RepoPath("staged.txt"), ChangeType.MODIFIED, true, false);
    private static final FileEntry BOTH_MOD = new FileEntry(new RepoPath("both.txt"), ChangeType.MODIFIED, true, true);
    private static final FileEntry NEW = Fixtures.fileEntry("new.txt", ChangeType.ADDED);
    private static final FileEntry NEW_EDITED =
            new FileEntry(new RepoPath("edited-new.txt"), ChangeType.ADDED, true, true);
    private static final FileEntry UNTRACKED = Fixtures.fileEntry("dir/untracked.txt", ChangeType.UNTRACKED);
    private static final List<FileEntry> ALL = List.of(UNSTAGED_MOD, STAGED_MOD, BOTH_MOD, NEW, NEW_EDITED, UNTRACKED);

    @Test
    void requestOpensThePopupWithTheFiles() {
        Next next = Update.update(loaded(), new DiscardMsg.Requested(ALL));

        assertEquals(Option.of(new DiscardPopup(ALL)), next.model().popup(DiscardPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void requestWithoutFilesDoesNothing() {
        assertEquals(
                Option.none(),
                Update.update(loaded(), new DiscardMsg.Requested(List.of()))
                        .model()
                        .popup());
    }

    @Test
    void cancellingClosesThePopup() {
        Model open = Update.update(loaded(), new DiscardMsg.Requested(ALL)).model();

        Next next = Update.update(open, new PopupMsg.Dismissed(DiscardPopup.class));

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void discardingEverythingResetsTrackedFilesToHeadAndRemovesTheRest() {
        assertEquals(
                new DiscardPlan(
                        List.of(new RepoPath("dir/untracked.txt")),
                        List.of(),
                        List.of(new RepoPath("unstaged.txt"), new RepoPath("staged.txt"), new RepoPath("both.txt")),
                        List.of(new RepoPath("new.txt"), new RepoPath("edited-new.txt"))),
                DiscardUpdate.plan(ALL, Scope.ALL));
    }

    @Test
    void discardingUnstagedChangesKeepsWhatIsStaged() {
        assertEquals(
                new DiscardPlan(
                        List.of(new RepoPath("dir/untracked.txt")),
                        List.of(new RepoPath("unstaged.txt"), new RepoPath("both.txt"), new RepoPath("edited-new.txt")),
                        List.of(),
                        List.of()),
                DiscardUpdate.plan(ALL, Scope.UNSTAGED));
    }

    @Test
    void discardingARenameRemovesItAndRestoresItsOrigin() {
        List<FileEntry> renamed = List.of(FileEntry.renamed(new RepoPath("b.txt"), new RepoPath("a.txt"), true, true));

        assertEquals(
                new DiscardPlan(List.of(), List.of(), List.of(new RepoPath("a.txt")), List.of(new RepoPath("b.txt"))),
                DiscardUpdate.plan(renamed, Scope.ALL));
        assertEquals(
                new DiscardPlan(List.of(), List.of(new RepoPath("b.txt")), List.of(), List.of()),
                DiscardUpdate.plan(renamed, Scope.UNSTAGED));
    }

    @Test
    void choosingStartsTheDiscardAndClosesThePopup() {
        Model open = Update.update(loaded(), new DiscardMsg.Requested(List.of(UNSTAGED_MOD)))
                .model();

        Next next = Update.update(open, new DiscardMsg.Chosen(Scope.ALL));

        assertEquals(List.of(new Discard(DiscardUpdate.plan(List.of(UNSTAGED_MOD), Scope.ALL))), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void choosingUnstagedWithNoUnstagedChangesDoesNothing() {
        Model open = Update.update(loaded(), new DiscardMsg.Requested(List.of(STAGED_MOD)))
                .model();

        Next next = Update.update(open, new DiscardMsg.Chosen(Scope.UNSTAGED));

        assertEquals(List.of(), next.cmds());
        assertTrue(next.model().popup(DiscardPopup.class).isDefined());
    }

    @Test
    void choosingWithoutAnOpenPopupDoesNothing() {
        assertEquals(
                List.of(),
                Update.update(loaded(), new DiscardMsg.Chosen(Scope.ALL)).cmds());
    }

    @Test
    void aFailureIsStoredAsAnError() {
        Next next = Update.update(
                loaded(),
                failed(new DiscardCmd.Discard(new DiscardPlan(List.of(), List.of(), List.of(), List.of())), "boom"));

        assertEquals(Option.of("Discarding changes failed: boom"), errorText(next.model()));
    }
}
