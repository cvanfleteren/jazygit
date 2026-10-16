package net.vanfleteren.jazygit.feature.commit;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.failed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Amending any commit of the checked out branch with the staged changes.
 */
class AmendCommitUpdateTest {

    private static final Commit ROOT = commit("c1", List.of());
    private static final Commit MIDDLE = commit("c2", List.of("c1"));
    private static final Commit TIP = commit("c3", List.of("c2"));
    private static final Commit MERGE = commit("c4", List.of("c3", "x"));

    private static final FileEntry STAGED = new FileEntry("a.txt", ChangeType.MODIFIED, true, false);
    private static final FileEntry UNSTAGED = new FileEntry("b.txt", ChangeType.MODIFIED, false, true);
    private static final FileEntry UNTRACKED = new FileEntry("c.txt", ChangeType.UNTRACKED);

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

    private static Model with(List<FileEntry> files, Commit... commits) {
        Model loaded = Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(commits)))
                .model();
        return Update.update(
                        loaded,
                        new LoadMsg.StatusLoaded(
                                new RepoStatus(Head.onBranch(new Branch.Name("main"), new Sha("c3")), files)))
                .model();
    }

    private static Model asked(List<FileEntry> files, Commit commit) {
        return Update.update(with(files, MERGE, TIP, MIDDLE, ROOT), new AmendCommitMsg.Requested(commit.sha()))
                .model();
    }

    @Test
    void requestOpensTheQuestionWithTheCommit() {
        Next next = Update.update(with(List.of(STAGED), TIP, MIDDLE, ROOT), new AmendCommitMsg.Requested(MIDDLE.sha()));

        assertEquals(Option.of(new AmendCommitPopup(MIDDLE)), next.model().popup(AmendCommitPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void aMergeCannotBeAmended() {
        Next next = Update.update(
                with(List.of(STAGED), MERGE, TIP, MIDDLE, ROOT), new AmendCommitMsg.Requested(MERGE.sha()));

        assertEquals(Option.none(), next.model().popup(AmendCommitPopup.class));
        assertEquals(Option.of("A merge commit cannot be amended"), errorText(next.model()));
    }

    @Test
    void withoutChangesThereIsNothingToAmendWith() {
        Next next = Update.update(with(List.of(), TIP, MIDDLE, ROOT), new AmendCommitMsg.Requested(MIDDLE.sha()));

        assertEquals(Option.none(), next.model().popup(AmendCommitPopup.class));
        assertEquals(Option.of("Nothing to amend the commit with"), errorText(next.model()));
    }

    @Test
    void anUnknownCommitDoesNothing() {
        Model model = with(List.of(STAGED), TIP, MIDDLE, ROOT);

        assertEquals(
                model,
                Update.update(model, new AmendCommitMsg.Requested(new Sha("elsewhere")))
                        .model());
    }

    @Test
    void confirmingWithStagedChangesAmendsWithThem() {
        Next next = Update.update(asked(List.of(STAGED, UNSTAGED), MIDDLE), new AmendCommitMsg.Confirmed());

        assertEquals(List.of(new CommitCmd.AmendCommit(MIDDLE.sha(), List.empty())), next.cmds());
        assertEquals(Option.none(), next.model().popup(AmendCommitPopup.class));
    }

    @Test
    void confirmingWithNothingStagedAsksWhetherToAmendWithAllFiles() {
        Next next = Update.update(asked(List.of(UNSTAGED, UNTRACKED), MIDDLE), new AmendCommitMsg.Confirmed());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup(AmendCommitPopup.class));
        assertEquals(
                Option.of(new AmendCommitStageAllPopup(MIDDLE)), next.model().popup(AmendCommitStageAllPopup.class));
    }

    @Test
    void agreeingToAllFilesStagesThemAndAmends() {
        Model stageAll = Update.update(asked(List.of(UNSTAGED, UNTRACKED), MIDDLE), new AmendCommitMsg.Confirmed())
                .model();

        Next next = Update.update(stageAll, new AmendCommitMsg.StageAllConfirmed());

        assertEquals(List.of(new CommitCmd.AmendCommit(MIDDLE.sha(), List.of("b.txt", "c.txt"))), next.cmds());
        assertEquals(Option.none(), next.model().popup(AmendCommitStageAllPopup.class));
    }

    @Test
    void cancellingEitherQuestionLeavesEverythingAsItWas() {
        Model first = asked(List.of(UNSTAGED), MIDDLE);
        Model second = Update.update(first, new AmendCommitMsg.Confirmed()).model();

        Next cancelFirst = Update.update(first, new AmendCommitMsg.Cancelled());
        Next cancelSecond = Update.update(second, new AmendCommitMsg.Cancelled());

        assertEquals(List.of(), cancelFirst.cmds());
        assertEquals(Option.none(), cancelFirst.model().popup(AmendCommitPopup.class));
        assertEquals(List.of(), cancelSecond.cmds());
        assertEquals(Option.none(), cancelSecond.model().popup(AmendCommitStageAllPopup.class));
    }

    @Test
    void aFailureShowsTheError() {
        Next next = Update.update(
                TestModels.loaded(), failed(new CommitCmd.AmendCommit(new Sha("aaaa"), List.empty()), "conflict"));

        assertEquals(Option.of("Amend commit failed: conflict"), errorText(next.model()));
    }
}
