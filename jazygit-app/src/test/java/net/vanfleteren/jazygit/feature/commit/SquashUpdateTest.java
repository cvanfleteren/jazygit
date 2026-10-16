package net.vanfleteren.jazygit.feature.commit;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.failed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Squashing a commit into the one below it.
 */
class SquashUpdateTest {

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
    void requestOpensTheQuestionWithTheCommitAndItsParent() {
        Model model = withCommits(TIP, MIDDLE, ROOT);

        Next next = Update.update(model, new SquashMsg.Requested(TIP.sha()));

        assertEquals(Option.of(new SquashPopup(TIP, MIDDLE)), next.model().popup(SquashPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theFirstCommitHasNothingToSquashInto() {
        Model model = withCommits(MIDDLE, ROOT);

        assertEquals(
                Option.none(),
                Update.update(model, new SquashMsg.Requested(ROOT.sha()))
                        .model()
                        .popup(SquashPopup.class));
    }

    @Test
    void aMergeCannotBeSquashed() {
        Model model = withCommits(MERGE, TIP, MIDDLE, ROOT);

        assertEquals(
                Option.none(),
                Update.update(model, new SquashMsg.Requested(MERGE.sha()))
                        .model()
                        .popup(SquashPopup.class));
    }

    @Test
    void anUnknownCommitDoesNothing() {
        Model model = withCommits(TIP, MIDDLE, ROOT);

        assertEquals(
                Option.none(),
                Update.update(model, new SquashMsg.Requested(new Sha("elsewhere")))
                        .model()
                        .popup(SquashPopup.class));
    }

    @Test
    void confirmingClosesThePopupAndSquashes() {
        Model open = Update.update(withCommits(TIP, MIDDLE, ROOT), new SquashMsg.Requested(TIP.sha()))
                .model();

        Next next = Update.update(open, new SquashMsg.Confirmed());

        assertEquals(List.of(new CommitCmd.Squash(new Sha("c3"), SquashMode.SQUASH)), next.cmds());
        assertEquals(Option.none(), next.model().popup(SquashPopup.class));
    }

    @Test
    void cancellingClosesThePopup() {
        Model open = Update.update(withCommits(TIP, MIDDLE, ROOT), new SquashMsg.Requested(TIP.sha()))
                .model();

        Next next = Update.update(open, new SquashMsg.Cancelled());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup(SquashPopup.class));
    }

    @Test
    void aFailureShowsTheError() {
        Next next = Update.update(
                TestModels.loaded(), failed(new CommitCmd.Squash(new Sha("aaaa"), SquashMode.SQUASH), "conflict"));

        assertEquals(Option.of("Squash failed: conflict"), errorText(next.model()));
    }
}
