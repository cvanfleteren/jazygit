package net.vanfleteren.jazygit.feature.commit;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The commit transitions: committing, amending and rewording.
 */
class CommitUpdateTest {

    private static Commit commit(String message, String body) {
        return commit("abc1234", message, body);
    }

    private static Commit commit(String sha, String message, String body) {
        return new Commit(new Sha(sha), "me", "me@example.com", Instant.EPOCH, message, body);
    }

    @Test
    void nothingIsSaidToBeUnchangedBeforeTheStatusIsRead() {
        Model starting = loaded().withStatus(net.vanfleteren.jazygit.state.Loadable.loading());

        for (net.vanfleteren.jazygit.state.Msg msg : List.<net.vanfleteren.jazygit.state.Msg>of(
                new CommitMsg.Requested(),
                new AmendMsg.Requested(),
                new net.vanfleteren.jazygit.feature.stash.StashMsg.Requested())) {
            Model next = Update.update(starting, msg).model();
            assertEquals(Option.none(), next.error(), msg.toString());
            assertEquals(Option.none(), next.popup(), msg.toString());
        }
    }

    private Model withFiles(FileEntry... files) {
        return loaded().withStatus(new Loaded<>(
                new RepoStatus(Head.onBranch(new Branch.Name("main"), new Sha("aaaa")), List.of(files))));
    }

    private Model withCommits(Commit... commits) {
        return loaded().withCommits(new Loaded<>(List.of(commits)));
    }

    @Test
    void rewordOpensTheDialogForTheLastCommit() {
        Commit last = commit("last", "details");
        Model model = Update.update(
                        withCommits(last, commit("def5678", "older", "")), new RewordMsg.Requested(last.sha()))
                .model();

        assertEquals(Option.of(new RewordPopup(last)), model.popup());
    }

    @Test
    void anyCommitCanBeReworded() {
        Commit older = commit("def5678", "older", "why");
        Model model = Update.update(withCommits(commit("last", ""), older), new RewordMsg.Requested(older.sha()))
                .model();

        assertEquals(Option.of(new RewordPopup(older)), model.popup());
    }

    @Test
    void rewordOfAnUnknownCommitDoesNothing() {
        Model model = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(new Sha("elsewhere")))
                .model();

        assertEquals(Option.none(), model.popup());
    }

    @Test
    void confirmingTheRewordRewordsAndClosesTheDialog() {
        Model open = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(new Sha("abc1234")))
                .model();

        Next next = Update.update(open, new RewordMsg.Confirmed(new CommitMessage(" better ", "\nwhy\n")));

        assertEquals(
                List.of(new CommitCmd.Reword(new Sha("abc1234"), new CommitMessage("better", "why"))), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void blankSummaryKeepsTheRewordDialogOpen() {
        Model open = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(new Sha("abc1234")))
                .model();

        Next next = Update.update(open, new RewordMsg.Confirmed(new CommitMessage(" ", "why")));

        assertEquals(List.of(), next.cmds());
        assertEquals(open.popup(), next.model().popup());
    }

    @Test
    void cancellingTheRewordClosesTheDialog() {
        Model open = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(new Sha("abc1234")))
                .model();

        assertEquals(
                Option.none(),
                Update.update(open, new RewordMsg.Cancelled()).model().popup());
    }

    @Test
    void amendAsksForConfirmationThenAmendsWithTheStagedFiles() {
        Model asking = Update.update(
                        withFiles(
                                new FileEntry("a.txt", ChangeType.ADDED), new FileEntry("b.txt", ChangeType.MODIFIED)),
                        new AmendMsg.Requested())
                .model();
        assertTrue(asking.popup(AmendPopup.class).isDefined());

        Next next = Update.update(asking, new AmendMsg.Confirmed());

        assertEquals(List.of(new CommitCmd.Amend(List.of())), next.cmds());
        assertFalse(next.model().popup(AmendPopup.class).isDefined());
    }

    @Test
    void amendStagesAllFilesWhenNoneIsStaged() {
        Model asking = Update.update(
                        withFiles(
                                new FileEntry("a.txt", ChangeType.MODIFIED),
                                new FileEntry("b.txt", ChangeType.UNTRACKED)),
                        new AmendMsg.Requested())
                .model();

        assertEquals(
                List.of(new CommitCmd.Amend(List.of("a.txt", "b.txt"))),
                Update.update(asking, new AmendMsg.Confirmed()).cmds());
    }

    @Test
    void cancellingTheAmendClosesThePrompt() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new AmendMsg.Requested())
                .model();

        Next next = Update.update(asking, new AmendMsg.Cancelled());

        assertFalse(next.model().popup(AmendPopup.class).isDefined());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void amendWithoutChangesIsAnError() {
        assertEquals(
                Option.of(new AppError.NothingToAmend()),
                Update.update(withFiles(), new AmendMsg.Requested()).model().error());
    }

    @Test
    void commitOpensTheDialogWhenSomethingIsStaged() {
        Model model = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        assertTrue(model.popup(CommitPopup.class).isDefined());
        assertFalse(model.popup(StageAllPopup.class).isDefined());
    }

    @Test
    void commitAsksToStageAllWhenNothingIsStaged() {
        Model model = Update.update(withFiles(new FileEntry("a.txt", ChangeType.MODIFIED)), new CommitMsg.Requested())
                .model();

        assertTrue(model.popup(StageAllPopup.class).isDefined());
        assertFalse(model.popup(CommitPopup.class).isDefined());
    }

    @Test
    void commitWithoutChangesIsAnError() {
        Model model = Update.update(withFiles(), new CommitMsg.Requested()).model();

        assertEquals(Option.of("Nothing to commit"), errorText(model));
        assertFalse(model.popup(StageAllPopup.class).isDefined());
    }

    @Test
    void confirmingStageAllStagesEverythingUnstaged() {
        Model asking = Update.update(
                        withFiles(
                                new FileEntry("a.txt", ChangeType.MODIFIED),
                                new FileEntry("b.txt", ChangeType.UNTRACKED)),
                        new CommitMsg.Requested())
                .model();

        Next next = Update.update(asking, new CommitMsg.StageAllConfirmed());

        assertEquals(List.of(new CommitCmd.StageForCommit(List.of("a.txt", "b.txt"))), next.cmds());
        assertFalse(next.model().popup(StageAllPopup.class).isDefined());
        assertTrue(Update.update(
                        next.model(), new CommitMsg.StagedForCommit(new CommitCmd.StageForCommit(List.of()), List.of()))
                .model()
                .popup(CommitPopup.class)
                .isDefined());
    }

    @Test
    void cancellingStageAllClosesThePrompt() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.MODIFIED)), new CommitMsg.Requested())
                .model();

        assertFalse(Update.update(asking, new CommitMsg.StageAllCancelled())
                .model()
                .popup(StageAllPopup.class)
                .isDefined());
    }

    @Test
    void confirmingTheCommitCommitsAndClosesTheDialog() {
        Model open = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        Next next = Update.update(open, new CommitMsg.Confirmed(new CommitMessage(" summary ", "\ndetails\n")));

        assertEquals(List.of(new CommitCmd.Commit(new CommitMessage("summary", "details"))), next.cmds());
        assertFalse(next.model().popup(CommitPopup.class).isDefined());
    }

    @Test
    void blankSummaryKeepsTheCommitDialogOpen() {
        Model open = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        Next next = Update.update(open, new CommitMsg.Confirmed(new CommitMessage("  ", "details")));

        assertEquals(List.of(), next.cmds());
        assertTrue(next.model().popup(CommitPopup.class).isDefined());
    }

    @Test
    void cancellingTheCommitClosesTheDialog() {
        Model open = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        assertFalse(Update.update(open, new CommitMsg.Cancelled())
                .model()
                .popup(CommitPopup.class)
                .isDefined());
    }
}
