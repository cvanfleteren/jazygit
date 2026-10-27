package net.vanfleteren.jazygit.feature.commit.create;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.typed;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.commit.amend.AmendMsg;
import net.vanfleteren.jazygit.feature.commit.reword.RewordCmd;
import net.vanfleteren.jazygit.feature.commit.reword.RewordMsg;
import net.vanfleteren.jazygit.feature.commit.reword.RewordPopup;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The commit transitions: committing and rewording.
 */
class CommitUpdateTest {

    private static Commit commit(String message, String body) {
        return commit("abc1234", message, body);
    }

    private static Commit commit(String sha, String message, String body) {
        return Fixtures.commit(new Sha(sha), "me", "me@example.com", Instant.EPOCH, message, body);
    }

    @Test
    void nothingIsSaidToBeUnchangedBeforeTheStatusIsRead() {
        Model starting = loaded().mapRepo(repo -> repo.withStatus(net.vanfleteren.jazygit.state.Loadable.loading()));

        for (net.vanfleteren.jazygit.state.Msg msg : List.<net.vanfleteren.jazygit.state.Msg>of(
                new CommitMsg.Requested(),
                new AmendMsg.Requested(),
                new net.vanfleteren.jazygit.feature.stash.StashMsg.Requested())) {
            Model next = Update.update(starting, msg).model();
            assertEquals(Option.none(), next.outcome().error(), msg.toString());
            assertEquals(Option.none(), next.popup(), msg.toString());
        }
    }

    private Model withFiles(FileEntry... files) {
        return loaded().mapRepo(repo -> repo.withStatus(
                new Loaded<>(new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("aaaa")), List.of(files)))));
    }

    private Model withCommits(Commit... commits) {
        return loaded().mapRepo(repo -> repo.withCommits(new Loaded<>(List.of(commits))));
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

        Next next = Update.update(typed(open, new CommitMessage(" better ", "\nwhy\n")), new RewordMsg.Confirmed());

        assertEquals(List.of(new RewordCmd(new Sha("abc1234"), new CommitMessage("better", "why"))), next.cmds());
        assertEquals(Option.none(), next.model().popup());
    }

    @Test
    void blankSummaryKeepsTheRewordDialogOpen() {
        Model open = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(new Sha("abc1234")))
                .model();

        Model typed = typed(open, new CommitMessage(" ", "why"));
        Next next = Update.update(typed, new RewordMsg.Confirmed());

        assertEquals(List.of(), next.cmds());
        assertEquals(typed.popup(), next.model().popup());
    }

    @Test
    void cancellingTheRewordClosesTheDialog() {
        Model open = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(new Sha("abc1234")))
                .model();

        assertEquals(
                Option.none(),
                Update.update(open, new Msg.PopupDismissed(RewordPopup.class))
                        .model()
                        .popup());
    }

    @Test
    void commitOpensTheDialogWhenSomethingIsStaged() {
        Model model = Update.update(withFiles(Fixtures.fileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        assertTrue(model.popup(CommitPopup.class).isDefined());
        assertFalse(model.popup(StageAllPopup.class).isDefined());
    }

    @Test
    void commitAsksToStageAllWhenNothingIsStaged() {
        Model model = Update.update(
                        withFiles(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)), new CommitMsg.Requested())
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
                                Fixtures.fileEntry("a.txt", ChangeType.MODIFIED),
                                Fixtures.fileEntry("b.txt", ChangeType.UNTRACKED)),
                        new CommitMsg.Requested())
                .model();

        Next next = Update.update(asking, new CommitMsg.StageAllConfirmed());

        assertEquals(List.of(new CreateCommitCmd.StageForCommit(List.of("a.txt", "b.txt"))), next.cmds());
        assertFalse(next.model().popup(StageAllPopup.class).isDefined());
        assertTrue(Update.update(
                        next.model(),
                        new CommitMsg.StagedForCommit(new CreateCommitCmd.StageForCommit(List.of()), List.of()))
                .model()
                .popup(CommitPopup.class)
                .isDefined());
    }

    @Test
    void cancellingStageAllClosesThePrompt() {
        Model asking = Update.update(
                        withFiles(Fixtures.fileEntry("a.txt", ChangeType.MODIFIED)), new CommitMsg.Requested())
                .model();

        assertFalse(Update.update(asking, new Msg.PopupDismissed(StageAllPopup.class))
                .model()
                .popup(StageAllPopup.class)
                .isDefined());
    }

    @Test
    void confirmingTheCommitCommitsAndClosesTheDialog() {
        Model open = Update.update(withFiles(Fixtures.fileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        Next next =
                Update.update(typed(open, new CommitMessage(" summary ", "\ndetails\n")), new CommitMsg.Confirmed());

        assertEquals(List.of(new CreateCommitCmd.Commit(new CommitMessage("summary", "details"))), next.cmds());
        assertFalse(next.model().popup(CommitPopup.class).isDefined());
    }

    @Test
    void blankSummaryKeepsTheCommitDialogOpen() {
        Model open = Update.update(withFiles(Fixtures.fileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        Next next = Update.update(typed(open, new CommitMessage("  ", "details")), new CommitMsg.Confirmed());

        assertEquals(List.of(), next.cmds());
        assertTrue(next.model().popup(CommitPopup.class).isDefined());
    }

    @Test
    void cancellingTheCommitClosesTheDialog() {
        Model open = Update.update(withFiles(Fixtures.fileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        assertFalse(Update.update(open, new Msg.PopupDismissed(CommitPopup.class))
                .model()
                .popup(CommitPopup.class)
                .isDefined());
    }
}
