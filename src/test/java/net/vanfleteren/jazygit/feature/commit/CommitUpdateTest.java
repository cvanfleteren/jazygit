package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The commit transitions: committing, amending and rewording.
 */
class CommitUpdateTest {

    private Model withFiles(FileEntry... files) {
        return loaded().withStatus(new Loaded<>(new RepoStatus("main", "aaaa", List.of(files))));
    }

    private Model withCommits(Commit... commits) {
        return loaded().withCommits(new Loaded<>(List.of(commits)));
    }

    private static Commit commit(String message, String body) {
        return new Commit("abc1234", "me", "me@example.com", Instant.EPOCH, message, body);
    }

    @Test
    void rewordOpensTheDialogForTheLastCommit() {
        Commit last = commit("last", "details");
        Model model = Update.update(withCommits(last, commit("older", "")), new RewordMsg.Requested(0)).model();

        assertEquals(Optional.of(last), model.rewording());
    }

    @Test
    void onlyTheLastCommitCanBeReworded() {
        Model model = Update.update(withCommits(commit("last", ""), commit("older", "")), new RewordMsg.Requested(1))
                .model();

        assertEquals(Optional.empty(), model.rewording());
        assertEquals(Optional.of("Only the last commit can be reworded"), model.error());
    }

    @Test
    void confirmingTheRewordRewordsAndClosesTheDialog() {
        Model open = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(0)).model();

        Next next = Update.update(open, new RewordMsg.Confirmed(" better ", "\nwhy\n"));

        assertEquals(List.of(new CommitCmd.Reword("better", "why")), next.cmds());
        assertEquals(Optional.empty(), next.model().rewording());
    }

    @Test
    void blankSummaryKeepsTheRewordDialogOpen() {
        Model open = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(0)).model();

        Next next = Update.update(open, new RewordMsg.Confirmed(" ", "why"));

        assertEquals(List.of(), next.cmds());
        assertEquals(open.rewording(), next.model().rewording());
    }

    @Test
    void cancellingTheRewordClosesTheDialog() {
        Model open = Update.update(withCommits(commit("last", "")), new RewordMsg.Requested(0)).model();

        assertEquals(Optional.empty(), Update.update(open, new RewordMsg.Cancelled()).model().rewording());
    }

    @Test
    void amendAsksForConfirmationThenAmendsWithTheStagedFiles() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED),
                new FileEntry("b.txt", ChangeType.MODIFIED)), new AmendMsg.Requested()).model();
        assertEquals(true, asking.amendPrompt());

        Next next = Update.update(asking, new AmendMsg.Confirmed());

        assertEquals(List.of(new CommitCmd.Amend(List.of())), next.cmds());
        assertEquals(false, next.model().amendPrompt());
    }

    @Test
    void amendStagesAllFilesWhenNoneIsStaged() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.MODIFIED),
                new FileEntry("b.txt", ChangeType.UNTRACKED)), new AmendMsg.Requested()).model();

        assertEquals(List.of(new CommitCmd.Amend(List.of("a.txt", "b.txt"))),
                Update.update(asking, new AmendMsg.Confirmed()).cmds());
    }

    @Test
    void cancellingTheAmendClosesThePrompt() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new AmendMsg.Requested())
                .model();

        Next next = Update.update(asking, new AmendMsg.Cancelled());

        assertEquals(false, next.model().amendPrompt());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void amendWithoutChangesIsAnError() {
        assertEquals(Optional.of("Nothing to amend the last commit with"),
                Update.update(withFiles(), new AmendMsg.Requested()).model().error());
    }

    @Test
    void commitOpensTheDialogWhenSomethingIsStaged() {
        Model model = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        assertEquals(true, model.commitOpen());
        assertEquals(false, model.stageAllPrompt());
    }

    @Test
    void commitAsksToStageAllWhenNothingIsStaged() {
        Model model = Update.update(withFiles(new FileEntry("a.txt", ChangeType.MODIFIED)), new CommitMsg.Requested())
                .model();

        assertEquals(true, model.stageAllPrompt());
        assertEquals(false, model.commitOpen());
    }

    @Test
    void commitWithoutChangesIsAnError() {
        Model model = Update.update(withFiles(), new CommitMsg.Requested()).model();

        assertEquals(Optional.of("Nothing to commit"), model.error());
        assertEquals(false, model.stageAllPrompt());
    }

    @Test
    void confirmingStageAllStagesEverythingUnstaged() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.MODIFIED),
                new FileEntry("b.txt", ChangeType.UNTRACKED)), new CommitMsg.Requested()).model();

        Next next = Update.update(asking, new CommitMsg.StageAllConfirmed());

        assertEquals(List.of(new CommitCmd.StageForCommit(List.of("a.txt", "b.txt"))), next.cmds());
        assertEquals(false, next.model().stageAllPrompt());
        assertEquals(true, Update.update(next.model(), new CommitMsg.StagedForCommit()).model().commitOpen());
    }

    @Test
    void cancellingStageAllClosesThePrompt() {
        Model asking = Update.update(withFiles(new FileEntry("a.txt", ChangeType.MODIFIED)),
                new CommitMsg.Requested()).model();

        assertEquals(false, Update.update(asking, new CommitMsg.StageAllCancelled()).model().stageAllPrompt());
    }

    @Test
    void confirmingTheCommitCommitsAndClosesTheDialog() {
        Model open = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        Next next = Update.update(open, new CommitMsg.Confirmed(" summary ", "\ndetails\n"));

        assertEquals(List.of(new CommitCmd.Commit("summary", "details")), next.cmds());
        assertEquals(false, next.model().commitOpen());
    }

    @Test
    void blankSummaryKeepsTheCommitDialogOpen() {
        Model open = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        Next next = Update.update(open, new CommitMsg.Confirmed("  ", "details"));

        assertEquals(List.of(), next.cmds());
        assertEquals(true, next.model().commitOpen());
    }

    @Test
    void cancellingTheCommitClosesTheDialog() {
        Model open = Update.update(withFiles(new FileEntry("a.txt", ChangeType.ADDED)), new CommitMsg.Requested())
                .model();

        assertEquals(false, Update.update(open, new CommitMsg.Cancelled()).model().commitOpen());
    }
}
