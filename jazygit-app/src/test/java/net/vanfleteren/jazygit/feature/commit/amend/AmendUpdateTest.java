package net.vanfleteren.jazygit.feature.commit.amend;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.failed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.commit.amend.AmendTarget.LastCommit;
import net.vanfleteren.jazygit.feature.commit.amend.AmendTarget.Older;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Amending the last commit, or any other commit of the checked out branch, with the staged changes.
 */
class AmendUpdateTest {

    private static final Commit ROOT = commit("c1", List.of());
    private static final Commit MIDDLE = commit("c2", List.of("c1"));
    private static final Commit TIP = commit("c3", List.of("c2"));
    private static final Commit MERGE = commit("c4", List.of("c3", "x"));

    private static final FileEntry STAGED = new FileEntry("a.txt", ChangeType.MODIFIED, true, false);
    private static final FileEntry UNSTAGED = new FileEntry("b.txt", ChangeType.MODIFIED, false, true);
    private static final FileEntry UNTRACKED = Fixtures.fileEntry("c.txt", ChangeType.UNTRACKED);

    private static Commit commit(String sha, List<String> parents) {
        return Fixtures.commit(
                new Sha(sha),
                "Ada",
                "ada@example.com",
                Instant.EPOCH,
                "Commit " + sha,
                "",
                parents.map(Sha::new).toList());
    }

    /**
     * The commits loaded, with HEAD at the first one.
     */
    private static Model with(List<FileEntry> files, Commit... commits) {
        Model loaded = Update.update(TestModels.loaded(), TestModels.commitsLoaded(List.of(commits)))
                .model();
        return Update.update(
                        loaded,
                        new LoadMsg.StatusLoaded(
                                new RepoStatus(Head.onBranch(new BranchName("main"), commits[0].sha()), files)))
                .model();
    }

    private static Model asked(List<FileEntry> files, Option<Sha> sha) {
        return Update.update(with(files, TIP, MIDDLE, ROOT), new AmendMsg.Requested(sha))
                .model();
    }

    @Test
    void fromTheFilesTheLastCommitIsAmended() {
        Next next = Update.update(with(List.of(STAGED), TIP, MIDDLE, ROOT), new AmendMsg.Requested());

        assertEquals(Option.of(new AmendPopup(new LastCommit())), next.model().popup(AmendPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void anOlderCommitIsAmendedAsSuch() {
        Next next = Update.update(
                with(List.of(STAGED), TIP, MIDDLE, ROOT), new AmendMsg.Requested(Option.of(MIDDLE.sha())));

        assertEquals(
                Option.of(new AmendPopup(new Older(MIDDLE.sha()))), next.model().popup(AmendPopup.class));
    }

    @Test
    void theCommitHeadIsIsTheLastCommit() {
        Next next =
                Update.update(with(List.of(STAGED), TIP, MIDDLE, ROOT), new AmendMsg.Requested(Option.of(TIP.sha())));

        assertEquals(Option.of(new AmendPopup(new LastCommit())), next.model().popup(AmendPopup.class));
    }

    @Test
    void anOlderMergeCannotBeAmended() {
        Commit merge = commit("c5", List.of("c2", "x"));
        Next next = Update.update(
                with(List.of(STAGED), TIP, merge, MIDDLE, ROOT), new AmendMsg.Requested(Option.of(merge.sha())));

        assertEquals(Option.none(), next.model().popup(AmendPopup.class));
        assertEquals(Option.of("A merge commit cannot be amended"), errorText(next.model()));
    }

    @Test
    void aMergeThatIsTheLastCommitCanBeAmended() {
        Next next = Update.update(
                with(List.of(STAGED), MERGE, TIP, MIDDLE, ROOT), new AmendMsg.Requested(Option.of(MERGE.sha())));

        assertEquals(Option.of(new AmendPopup(new LastCommit())), next.model().popup(AmendPopup.class));
    }

    @Test
    void withoutChangesThereIsNothingToAmendWith() {
        for (Option<Sha> sha : List.of(Option.<Sha>none(), Option.of(MIDDLE.sha()))) {
            Next next = Update.update(with(List.of(), TIP, MIDDLE, ROOT), new AmendMsg.Requested(sha));

            assertEquals(Option.none(), next.model().popup(AmendPopup.class), sha.toString());
            assertEquals(Option.of("Nothing to amend the commit with"), errorText(next.model()), sha.toString());
        }
    }

    @Test
    void nothingHappensBeforeTheStatusIsRead() {
        Model starting = with(List.of(STAGED), TIP, MIDDLE, ROOT).mapRepo(repo -> repo.withStatus(Loadable.loading()));

        Next next = Update.update(starting, new AmendMsg.Requested());

        assertEquals(Option.none(), next.model().popup());
        assertEquals(Option.none(), next.model().outcome().error());
    }

    @Test
    void anUnknownCommitDoesNothing() {
        Model model = with(List.of(STAGED), TIP, MIDDLE, ROOT);

        assertEquals(
                model,
                Update.update(model, new AmendMsg.Requested(Option.of(new Sha("elsewhere"))))
                        .model());
    }

    @Test
    void confirmingWithStagedChangesAmendsWithThem() {
        for (AmendTarget target : List.of(new LastCommit(), new Older(MIDDLE.sha()))) {
            Option<Sha> sha = target instanceof Older(Sha older) ? Option.of(older) : Option.none();
            Next next = Update.update(asked(List.of(STAGED, UNSTAGED), sha), new AmendMsg.Confirmed());

            assertEquals(List.of(new AmendCmd(target, List.empty())), next.cmds());
            assertEquals(Option.none(), next.model().popup(AmendPopup.class));
        }
    }

    @Test
    void confirmingWithNothingStagedAsksWhetherToAmendWithAllFiles() {
        Next next = Update.update(asked(List.of(UNSTAGED, UNTRACKED), Option.none()), new AmendMsg.Confirmed());

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup(AmendPopup.class));
        assertEquals(
                Option.of(new AmendStageAllPopup(new LastCommit())),
                next.model().popup(AmendStageAllPopup.class));
    }

    @Test
    void agreeingToAllFilesStagesThemAndAmends() {
        Model stageAll = Update.update(
                        asked(List.of(UNSTAGED, UNTRACKED), Option.of(MIDDLE.sha())), new AmendMsg.Confirmed())
                .model();

        Next next = Update.update(stageAll, new AmendMsg.StageAllConfirmed());

        assertEquals(List.of(new AmendCmd(new Older(MIDDLE.sha()), List.of("b.txt", "c.txt"))), next.cmds());
        assertEquals(Option.none(), next.model().popup(AmendStageAllPopup.class));
    }

    @Test
    void cancellingEitherQuestionLeavesEverythingAsItWas() {
        Model first = asked(List.of(UNSTAGED), Option.of(MIDDLE.sha()));
        Model second = Update.update(first, new AmendMsg.Confirmed()).model();

        Next cancelFirst = Update.update(first, new AmendMsg.Cancelled());
        Next cancelSecond = Update.update(second, new AmendMsg.Cancelled());

        assertEquals(List.of(), cancelFirst.cmds());
        assertEquals(Option.none(), cancelFirst.model().popup(AmendPopup.class));
        assertEquals(List.of(), cancelSecond.cmds());
        assertEquals(Option.none(), cancelSecond.model().popup(AmendStageAllPopup.class));
    }

    @Test
    void onlyAmendingAnOlderCommitWaitsUntilNothingIsInProgress() {
        assertFalse(new AmendCmd(new LastCommit(), List.empty()).operation().waitsForUnfinished());
        assertTrue(
                new AmendCmd(new Older(MIDDLE.sha()), List.empty()).operation().waitsForUnfinished());
    }

    @Test
    void aFailureShowsTheError() {
        Next last =
                Update.update(TestModels.loaded(), failed(new AmendCmd(new LastCommit(), List.empty()), "no commit"));
        Next older = Update.update(
                TestModels.loaded(), failed(new AmendCmd(new Older(new Sha("aaaa")), List.empty()), "conflict"));

        assertEquals(Option.of("Amend failed: no commit"), errorText(last.model()));
        assertEquals(Option.of("Amend commit failed: conflict"), errorText(older.model()));
    }
}
