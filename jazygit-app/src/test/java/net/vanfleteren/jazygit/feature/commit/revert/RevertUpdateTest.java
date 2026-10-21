package net.vanfleteren.jazygit.feature.commit.revert;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.failed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Commit;
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
    void requestingAsksToConfirm() {
        assertEquals(Option.of(new RevertPopup(FIRST)), asked().popup(RevertPopup.class));
    }

    @Test
    void theConfirmationSaysAMergeIsRevertedAgainstItsFirstParent() {
        assertEquals(
                "Are you sure you want to revert the merge mmmm \"Merge\" against its first parent?",
                RevertDialogs.REVERT.message().apply(new RevertPopup(MERGE)));
    }

    @Test
    void aMergeCanBeReverted() {
        Model model = Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(MERGE)))
                .model();

        Model next = Update.update(model, new RevertMsg.Requested(MERGE.sha())).model();

        assertEquals(Option.of(new RevertPopup(MERGE)), next.popup(RevertPopup.class));
    }

    @Test
    void requestingAnUnknownCommitDoesNothing() {
        Model model = TestModels.loaded();

        assertEquals(
                model,
                Update.update(model, new RevertMsg.Requested(new Sha("ffff"))).model());
    }

    @Test
    void confirmingReverts() {
        Next next = Update.update(asked(), new RevertMsg.Confirmed());

        assertEquals(Option.none(), next.model().popup(RevertPopup.class));
        assertEquals(new RevertCmd(FIRST), next.cmds().head());
    }

    @Test
    void cancellingClosesThePopup() {
        Next next = Update.update(asked(), new RevertMsg.Cancelled());

        assertEquals(Option.none(), next.model().popup(RevertPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void aFailureShowsTheError() {
        Model running = Update.update(asked(), new RevertMsg.Confirmed()).model();

        Model next = Update.update(running, failed(new RevertCmd(FIRST), "nothing to commit"))
                .model();

        assertEquals(Option.of("Revert of aaaa failed: nothing to commit"), errorText(next));
    }
}
