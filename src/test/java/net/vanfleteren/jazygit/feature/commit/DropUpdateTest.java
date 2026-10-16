package net.vanfleteren.jazygit.feature.commit;

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
 * Dropping a commit from the history of the checked out branch.
 */
class DropUpdateTest {

    private static final Commit ROOT = commit("c1", List.of());
    private static final Commit MIDDLE = commit("c2", List.of("c1"));
    private static final Commit TIP = commit("c3", List.of("c2"));
    private static final Commit MERGE = commit("c4", List.of("c3", "x"));

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

    private static Model withCommits(Commit... commits) {
        return Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(commits)))
                .model();
    }

    @Test
    void requestOpensTheQuestionWithTheCommit() {
        Model model = withCommits(TIP, MIDDLE, ROOT);

        Next next = Update.update(model, new DropMsg.Requested(MIDDLE.sha()));

        assertEquals(Option.of(new DropPopup(MIDDLE)), next.model().popup(DropPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theFirstCommitCanBeDroppedWhenCommitsFollowIt() {
        Model model = withCommits(MIDDLE, ROOT);

        assertEquals(
                Option.of(new DropPopup(ROOT)),
                Update.update(model, new DropMsg.Requested(ROOT.sha())).model().popup(DropPopup.class));
    }

    @Test
    void theOnlyCommitCannotBeDropped() {
        Model model = withCommits(ROOT);

        assertEquals(
                Option.none(),
                Update.update(model, new DropMsg.Requested(ROOT.sha())).model().popup(DropPopup.class));
    }

    @Test
    void aMergeCannotBeDropped() {
        Model model = withCommits(MERGE, TIP, MIDDLE, ROOT);

        assertEquals(
                Option.none(),
                Update.update(model, new DropMsg.Requested(MERGE.sha())).model().popup(DropPopup.class));
    }

    @Test
    void anUnknownCommitDoesNothing() {
        Model model = withCommits(TIP, MIDDLE, ROOT);

        assertEquals(
                Option.none(),
                Update.update(model, new DropMsg.Requested(new Sha("elsewhere")))
                        .model()
                        .popup(DropPopup.class));
    }

    @Test
    void confirmingClosesThePopupAndDrops() {
        Model open = Update.update(withCommits(TIP, MIDDLE, ROOT), new DropMsg.Requested(MIDDLE.sha()))
                .model();

        Next next = Update.update(open, new DropMsg.Confirmed());

        assertEquals(List.of(new CommitCmd.Drop(new Sha("c2"))), next.cmds());
        assertEquals(Option.none(), next.model().popup(DropPopup.class));
    }

    @Test
    void cancellingClosesThePopup() {
        Model open = Update.update(withCommits(TIP, MIDDLE, ROOT), new DropMsg.Requested(MIDDLE.sha()))
                .model();

        Next next = Update.update(open, new DropMsg.Cancelled());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup(DropPopup.class));
    }

    @Test
    void aFailureShowsTheError() {
        Next next = Update.update(TestModels.loaded(), failed(new CommitCmd.Drop(new Sha("aaaa")), "conflict"));

        assertEquals(Option.of("Drop failed: conflict"), errorText(next.model()));
    }
}
