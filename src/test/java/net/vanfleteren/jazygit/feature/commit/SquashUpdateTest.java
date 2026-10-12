package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.Sha;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Squashing a commit into the one below it.
 */
class SquashUpdateTest {

    private static final Commit ROOT = commit("c1", List.of());
    private static final Commit MIDDLE = commit("c2", List.of("c1"));
    private static final Commit TIP = commit("c3", List.of("c2"));
    private static final Commit MERGE = commit("c4", List.of("c3", "x"));

    private static Commit commit(String sha, List<String> parents) {
        return new Commit(new Sha(sha), "Ada", "ada@example.com", Instant.EPOCH, "Commit " + sha, "", parents.stream().map(Sha::new).toList());
    }

    private static Model withCommits(Commit... commits) {
        return Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(commits))).model();
    }

    @Test
    void requestOpensTheQuestionWithTheCommitAndItsParent() {
        Model model = withCommits(TIP, MIDDLE, ROOT);

        Next next = Update.update(model, new SquashMsg.Requested(0));

        assertEquals(Optional.of(new SquashPopup(TIP, MIDDLE)), next.model().popup(SquashPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theFirstCommitHasNothingToSquashInto() {
        Model model = withCommits(MIDDLE, ROOT);

        assertEquals(Optional.empty(), Update.update(model, new SquashMsg.Requested(1)).model().popup(SquashPopup.class));
    }

    @Test
    void aMergeCannotBeSquashed() {
        Model model = withCommits(MERGE, TIP, MIDDLE, ROOT);

        assertEquals(Optional.empty(), Update.update(model, new SquashMsg.Requested(0)).model().popup(SquashPopup.class));
    }

    @Test
    void anUnknownIndexDoesNothing() {
        Model model = withCommits(TIP, MIDDLE, ROOT);

        assertEquals(Optional.empty(), Update.update(model, new SquashMsg.Requested(7)).model().popup(SquashPopup.class));
    }

    @Test
    void confirmingClosesThePopupAndSquashes() {
        Model open = Update.update(withCommits(TIP, MIDDLE, ROOT), new SquashMsg.Requested(0)).model();

        Next next = Update.update(open, new SquashMsg.Confirmed());

        assertEquals(List.of(new CommitCmd.Squash(new Sha("c3"))), next.cmds());
        assertEquals(Optional.empty(), next.model().popup(SquashPopup.class));
    }

    @Test
    void cancellingClosesThePopup() {
        Model open = Update.update(withCommits(TIP, MIDDLE, ROOT), new SquashMsg.Requested(0)).model();

        Next next = Update.update(open, new SquashMsg.Cancelled());

        assertEquals(List.of(), next.cmds());
        assertEquals(Optional.empty(), next.model().popup(SquashPopup.class));
    }

    @Test
    void aFailureShowsTheError() {
        Next next = Update.update(TestModels.loaded(), new SquashMsg.Failed("conflict"));

        assertEquals(Optional.of("Squash failed: conflict"), next.model().error());
    }
}
