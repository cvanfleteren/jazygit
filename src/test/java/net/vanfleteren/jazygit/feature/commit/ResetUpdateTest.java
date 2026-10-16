package net.vanfleteren.jazygit.feature.commit;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.failed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Resetting the checked out branch to a commit.
 */
class ResetUpdateTest {

    private static final Commit ROOT = commit("c1", List.of());
    private static final Commit TIP = commit("c2", List.of("c1"));
    private static final FileEntry MODIFIED = new FileEntry("a.txt", ChangeType.MODIFIED, false, true);
    private static final FileEntry STAGED = new FileEntry("b.txt", ChangeType.ADDED, true, false);
    private static final FileEntry UNTRACKED = new FileEntry("c.txt", ChangeType.UNTRACKED, false, true);

    private static Commit commit(String sha, List<String> parents) {
        return new Commit(
                new Sha(sha),
                "Ada",
                "ada@example.com",
                Instant.EPOCH,
                "Commit " + sha,
                "",
                parents.map(Sha::new).toList());
    }

    private static Model withFiles(FileEntry... files) {
        Model model = Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(TIP, ROOT)))
                .model();
        RepoStatus status = new RepoStatus(Head.onBranch(new Branch.Name("main"), TIP.sha()), List.of(files));
        return Update.update(model, new LoadMsg.StatusLoaded(status)).model();
    }

    private static Model opened(Model model) {
        return Update.update(model, new ResetMsg.Requested(ROOT.sha())).model();
    }

    @Test
    void requestOpensTheChoiceWithTheTarget() {
        Next next = Update.update(withFiles(), new ResetMsg.Requested(ROOT.sha()));

        assertEquals(Option.of(new ResetPopup(ROOT)), next.model().popup(ResetPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theTipCanBeResetTo() {
        Next next = Update.update(withFiles(), new ResetMsg.Requested(TIP.sha()));

        assertEquals(Option.of(new ResetPopup(TIP)), next.model().popup(ResetPopup.class));
    }

    @Test
    void anUnknownCommitDoesNothing() {
        Next next = Update.update(withFiles(), new ResetMsg.Requested(new Sha("elsewhere")));

        assertEquals(Option.none(), next.model().popup(ResetPopup.class));
    }

    @Test
    void mixedAndSoftResetRightAway() {
        for (ResetMode mode : List.of(ResetMode.MIXED, ResetMode.SOFT)) {
            Next next = Update.update(opened(withFiles(MODIFIED)), new ResetMsg.Chosen(mode));

            assertEquals(List.of(new CommitCmd.Reset(ROOT.sha(), mode)), next.cmds());
            assertEquals(Option.none(), next.model().popup());
        }
    }

    @Test
    void aHardResetOfACleanTreeResetsRightAway() {
        Next next = Update.update(opened(withFiles()), new ResetMsg.Chosen(ResetMode.HARD));

        assertEquals(List.of(new CommitCmd.Reset(ROOT.sha(), ResetMode.HARD)), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void untrackedFilesAreLeftAloneSoTheyNeedNoConfirmation() {
        Next next = Update.update(opened(withFiles(UNTRACKED)), new ResetMsg.Chosen(ResetMode.HARD));

        assertEquals(List.of(new CommitCmd.Reset(ROOT.sha(), ResetMode.HARD)), next.cmds());
    }

    @Test
    void aHardResetThatDiscardsChangesAsksFirst() {
        Next next = Update.update(opened(withFiles(MODIFIED, STAGED, UNTRACKED)), new ResetMsg.Chosen(ResetMode.HARD));

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup(ResetPopup.class));
        assertEquals(
                Option.of(new HardResetPopup(ROOT, Option.of(2))), next.model().popup(HardResetPopup.class));
    }

    @Test
    void aHardResetAsksWhenTheStatusIsNotKnown() {
        Model model = Update.update(Update.init("repo").model(), new LoadMsg.CommitsLoaded(List.of(TIP, ROOT)))
                .model();

        Next next = Update.update(opened(model), new ResetMsg.Chosen(ResetMode.HARD));

        assertEquals(List.of(), next.cmds());
        assertEquals(
                Option.of(new HardResetPopup(ROOT, Option.none())), next.model().popup(HardResetPopup.class));
    }

    @Test
    void confirmingTheHardResetResets() {
        Model asking = Update.update(opened(withFiles(MODIFIED)), new ResetMsg.Chosen(ResetMode.HARD))
                .model();

        Next next = Update.update(asking, new ResetMsg.HardConfirmed());

        assertEquals(List.of(new CommitCmd.Reset(ROOT.sha(), ResetMode.HARD)), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void cancellingTheHardResetClosesEverything() {
        Model asking = Update.update(opened(withFiles(MODIFIED)), new ResetMsg.Chosen(ResetMode.HARD))
                .model();

        Next next = Update.update(asking, new ResetMsg.HardCancelled());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void cancellingTheChoiceClosesThePopup() {
        Next next = Update.update(opened(withFiles()), new ResetMsg.Cancelled());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void aFailedResetNamesItsTarget() {
        CommitCmd.Reset reset = new CommitCmd.Reset(new Sha("abcdef0123456789"), ResetMode.HARD);
        assertEquals(Operation.RESET, reset.operation());

        Next next = Update.update(TestModels.loaded(), failed(reset, "locked"));

        assertEquals(
                Option.of("Reset to " + new Sha("abcdef0123456789").shortSha() + " failed: locked"),
                errorText(next.model()));
    }
}
