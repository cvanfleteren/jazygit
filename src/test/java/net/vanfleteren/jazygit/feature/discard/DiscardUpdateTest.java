package net.vanfleteren.jazygit.feature.discard;

import net.vanfleteren.jazygit.feature.discard.DiscardCmd.Discard;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Scope;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscardUpdateTest {

    private static final FileEntry UNSTAGED_MOD = new FileEntry("unstaged.txt", ChangeType.MODIFIED);
    private static final FileEntry STAGED_MOD = new FileEntry("staged.txt", ChangeType.MODIFIED, true, false);
    private static final FileEntry BOTH_MOD = new FileEntry("both.txt", ChangeType.MODIFIED, true, true);
    private static final FileEntry NEW = new FileEntry("new.txt", ChangeType.ADDED);
    private static final FileEntry NEW_EDITED = new FileEntry("edited-new.txt", ChangeType.ADDED, true, true);
    private static final FileEntry UNTRACKED = new FileEntry("dir/untracked.txt", ChangeType.UNTRACKED);
    private static final List<FileEntry> ALL = List.of(UNSTAGED_MOD, STAGED_MOD, BOTH_MOD, NEW, NEW_EDITED, UNTRACKED);

    @Test
    void requestOpensThePopupWithTheFiles() {
        Next next = Update.update(loaded(), new DiscardMsg.Requested(ALL));

        assertEquals(Optional.of(new DiscardPopup(ALL)), next.model().popup(DiscardPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void requestWithoutFilesDoesNothing() {
        assertEquals(Optional.empty(), Update.update(loaded(), new DiscardMsg.Requested(List.of())).model().popup());
    }

    @Test
    void cancellingClosesThePopup() {
        Model open = Update.update(loaded(), new DiscardMsg.Requested(ALL)).model();

        Next next = Update.update(open, new DiscardMsg.Cancelled());

        assertEquals(Optional.empty(), next.model().popup());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void discardingEverythingResetsTrackedFilesToHeadAndRemovesTheRest() {
        assertEquals(new DiscardPlan(
                        List.of("dir/untracked.txt"),
                        List.of(),
                        List.of("unstaged.txt", "staged.txt", "both.txt"),
                        List.of("new.txt", "edited-new.txt")),
                DiscardUpdate.plan(ALL, Scope.ALL));
    }

    @Test
    void discardingUnstagedChangesKeepsWhatIsStaged() {
        assertEquals(new DiscardPlan(
                        List.of("dir/untracked.txt"),
                        List.of("unstaged.txt", "both.txt", "edited-new.txt"),
                        List.of(),
                        List.of()),
                DiscardUpdate.plan(ALL, Scope.UNSTAGED));
    }

    @Test
    void choosingStartsTheDiscardAndClosesThePopup() {
        Model open = Update.update(loaded(), new DiscardMsg.Requested(List.of(UNSTAGED_MOD))).model();

        Next next = Update.update(open, new DiscardMsg.Chosen(Scope.ALL));

        assertEquals(List.of(new Discard(DiscardUpdate.plan(List.of(UNSTAGED_MOD), Scope.ALL))), next.cmds());
        assertEquals(Optional.empty(), next.model().popup());
    }

    @Test
    void choosingUnstagedWithNoUnstagedChangesDoesNothing() {
        Model open = Update.update(loaded(), new DiscardMsg.Requested(List.of(STAGED_MOD))).model();

        Next next = Update.update(open, new DiscardMsg.Chosen(Scope.UNSTAGED));

        assertEquals(List.of(), next.cmds());
        assertTrue(next.model().popup(DiscardPopup.class).isPresent());
    }

    @Test
    void choosingWithoutAnOpenPopupDoesNothing() {
        assertEquals(List.of(), Update.update(loaded(), new DiscardMsg.Chosen(Scope.ALL)).cmds());
    }

    @Test
    void aFailureIsStoredAsAnError() {
        Next next = Update.update(loaded(), new DiscardMsg.Failed("boom"));

        assertEquals(Optional.of("Discarding changes failed: boom"), next.model().error());
    }
}
