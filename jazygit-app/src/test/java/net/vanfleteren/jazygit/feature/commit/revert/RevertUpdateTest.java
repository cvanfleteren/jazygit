package net.vanfleteren.jazygit.feature.commit.revert;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.failed;
import static net.vanfleteren.jazygit.state.TestModels.typed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Reverting a commit.
 */
class RevertUpdateTest {

    private static final Commit FIRST = TestModels.COMMITS.head();
    private static final Commit MERGE = new Commit(
            new Sha("mmmm"),
            "Ada",
            "ada@example.com",
            Instant.EPOCH,
            "Merge",
            "",
            List.of(new Sha("aaaa"), new Sha("ffff")));

    private static Model asked() {
        return Update.update(TestModels.loaded(), new RevertMsg.Requested(FIRST.sha()))
                .model();
    }

    @Test
    void requestingAsksForTheMessageGitWouldWrite() {
        assertEquals(
                Option.of(new RevertPopup(FIRST, new CommitMessage("Revert \"First\"", "This reverts commit aaaa."))),
                asked().popup(RevertPopup.class));
    }

    @Test
    void aMergeIsRevertedAgainstItsFirstParent() {
        Model model = Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(MERGE)))
                .model();

        Model next = Update.update(model, new RevertMsg.Requested(MERGE.sha())).model();

        assertEquals(
                Option.of("This reverts commit mmmm, reversing\nchanges made to aaaa."),
                next.popup(RevertPopup.class).map(popup -> popup.message().description()));
    }

    @Test
    void requestingAnUnknownCommitDoesNothing() {
        Model model = TestModels.loaded();

        assertEquals(
                model,
                Update.update(model, new RevertMsg.Requested(new Sha("ffff"))).model());
    }

    @Test
    void confirmingRevertsWithTheTypedMessage() {
        Model model = typed(asked(), new CommitMessage(" Undo first ", "Broke it\n"));

        Next next = Update.update(model, new RevertMsg.Confirmed());

        assertEquals(Option.none(), next.model().popup(RevertPopup.class));
        assertEquals(
                new RevertCmd(FIRST, new CommitMessage("Undo first", "Broke it")),
                next.cmds().head());
    }

    @Test
    void aBlankSummaryKeepsTheDialogOpen() {
        Model model = typed(asked(), new CommitMessage("  ", "Broke it"));

        Next next = Update.update(model, new RevertMsg.Confirmed());

        assertEquals(
                Option.of(new CommitMessage("  ", "Broke it")),
                next.model().popup(RevertPopup.class).map(RevertPopup::message));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void cancellingClosesTheDialog() {
        Next next = Update.update(asked(), new RevertMsg.Cancelled());

        assertEquals(Option.none(), next.model().popup(RevertPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void aFailureShowsTheError() {
        RevertCmd cmd = new RevertCmd(FIRST, CommitMessage.revertOf(FIRST));
        Model running = Update.update(asked(), new RevertMsg.Confirmed()).model();

        Model next = Update.update(running, failed(cmd, "nothing to commit")).model();

        assertEquals(Option.of("Revert of aaaa failed: nothing to commit"), errorText(next));
    }
}
