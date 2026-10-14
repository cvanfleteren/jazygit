package net.vanfleteren.jazygit.feature.commit;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.failed;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

/**
 * Fixing up a commit into the one below it.
 */
class FixupUpdateTest {

    private static final Commit ROOT = commit("c1", List.of());
    private static final Commit MIDDLE = commit("c2", List.of("c1"));
    private static final Commit TIP = commit("c3", List.of("c2"));
    private static final Commit MERGE = commit("c4", List.of("c3", "x"));

    private static Commit commit(String sha, List<String> parents) {
        return new Commit(new Sha(sha), "Ada", "ada@example.com", Instant.EPOCH, "Commit " + sha, "", parents.map(Sha::new).toList());
    }

    private static Model withCommits(Commit... commits) {
        return Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(commits))).model();
    }

    private static Model opened() {
        return Update.update(withCommits(TIP, MIDDLE, ROOT), new FixupMsg.Requested(TIP.sha())).model();
    }

    @Test
    void requestOpensTheQuestionWithTheCommitAndItsParent() {
        Next next = Update.update(withCommits(TIP, MIDDLE, ROOT), new FixupMsg.Requested(TIP.sha()));

        assertEquals(Option.of(new FixupPopup(TIP, MIDDLE)), next.model().popup(FixupPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theFirstCommitHasNothingToFixUpInto() {
        Model model = withCommits(MIDDLE, ROOT);

        assertEquals(Option.none(), Update.update(model, new FixupMsg.Requested(ROOT.sha())).model().popup(FixupPopup.class));
    }

    @Test
    void aMergeCannotBeFixedUp() {
        Model model = withCommits(MERGE, TIP, MIDDLE, ROOT);

        assertEquals(Option.none(), Update.update(model, new FixupMsg.Requested(MERGE.sha())).model().popup(FixupPopup.class));
    }

    @Test
    void anUnknownCommitDoesNothing() {
        Model model = withCommits(TIP, MIDDLE, ROOT);

        assertEquals(Option.none(), Update.update(model, new FixupMsg.Requested(new Sha("elsewhere"))).model().popup(FixupPopup.class));
    }

    @Test
    void choosingClosesThePopupAndFixesUpTheWayChosen() {
        for (SquashMode mode : List.of(SquashMode.FIXUP, SquashMode.FIXUP_KEEP_MESSAGE)) {
            Next next = Update.update(opened(), new FixupMsg.Chosen(mode));

            assertEquals(List.of(new CommitCmd.Squash(new Sha("c3"), mode)), next.cmds());
            assertEquals(Option.none(), next.model().popup(FixupPopup.class));
        }
    }

    @Test
    void cancellingClosesThePopup() {
        Next next = Update.update(opened(), new FixupMsg.Cancelled());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup(FixupPopup.class));
    }

    @Test
    void aFixupIsLoggedAndReportedAsOne() {
        assertEquals(Operation.FIXUP, new CommitCmd.Squash(new Sha("aaaa"), SquashMode.FIXUP).operation());
        assertEquals(Operation.FIXUP, new CommitCmd.Squash(new Sha("aaaa"), SquashMode.FIXUP_KEEP_MESSAGE).operation());

        Next next = Update.update(TestModels.loaded(), failed(new CommitCmd.Squash(new Sha("aaaa"), SquashMode.FIXUP), "conflict"));

        assertEquals(Option.of("Fixup failed: conflict"), errorText(next.model()));
    }
}
