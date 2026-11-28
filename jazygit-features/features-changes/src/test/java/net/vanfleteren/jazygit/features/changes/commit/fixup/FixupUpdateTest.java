package net.vanfleteren.jazygit.features.changes.commit.fixup;

import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.MAIN;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.ORIGIN_FEATURE;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.onFollowingBranch;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.warning;
import static net.vanfleteren.jazygit.kernel.state.TestModels.failed;
import static net.vanfleteren.jazygit.kernel.ui.text.ErrorTexts.errorText;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.SquashMode;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedCheckCmd;
import net.vanfleteren.jazygit.features.changes.commit.search.SearchedModels;
import net.vanfleteren.jazygit.features.changes.commit.squash.SquashCmd;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import org.junit.jupiter.api.Test;

/**
 * Fixing up a commit into the one below it.
 */
class FixupUpdateTest {

    private static final Commit ROOT = commit("c1", List.of());
    private static final Commit MIDDLE = commit("c2", List.of("c1"));
    private static final Commit TIP = commit("c3", List.of("c2"));
    private static final Commit MERGE = commit("c4", List.of("c3", "x"));

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

    private static Model withCommits(Commit... commits) {
        return Update.update(TestModels.loaded(), TestModels.commitsLoaded(List.of(commits)))
                .model();
    }

    private static Model opened() {
        return Update.update(withCommits(TIP, MIDDLE, ROOT), new FixupMsg.Requested(TIP.sha()))
                .model();
    }

    @Test
    void requestOpensTheQuestionWithTheCommitAndItsParent() {
        Next next = Update.update(withCommits(TIP, MIDDLE, ROOT), new FixupMsg.Requested(TIP.sha()));

        assertEquals(Option.of(new FixupPopup(TIP, MIDDLE.sha())), next.model().popup(FixupPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void aCommitTheSearchFoundOlderThanTheLogGoesIntoItsParent() {
        Commit old = commit("o2", List.of("o1"));
        Model model = SearchedModels.searched(withCommits(TIP, MIDDLE, ROOT), "old", List.of(old));

        assertEquals(
                Option.of(new FixupPopup(old, new Sha("o1"))),
                Update.update(model, new FixupMsg.Requested(old.sha())).model().popup(FixupPopup.class));
    }

    @Test
    void theFirstCommitHasNothingToFixUpInto() {
        Model model = withCommits(MIDDLE, ROOT);

        assertEquals(
                Option.none(),
                Update.update(model, new FixupMsg.Requested(ROOT.sha())).model().popup(FixupPopup.class));
    }

    @Test
    void aMergeCannotBeFixedUp() {
        Model model = withCommits(MERGE, TIP, MIDDLE, ROOT);

        assertEquals(
                Option.none(),
                Update.update(model, new FixupMsg.Requested(MERGE.sha()))
                        .model()
                        .popup(FixupPopup.class));
    }

    @Test
    void anUnknownCommitDoesNothing() {
        Model model = withCommits(TIP, MIDDLE, ROOT);

        assertEquals(
                Option.none(),
                Update.update(model, new FixupMsg.Requested(new Sha("elsewhere")))
                        .model()
                        .popup(FixupPopup.class));
    }

    @Test
    void choosingClosesThePopupAndFixesUpTheWayChosen() {
        for (SquashMode mode : List.of(SquashMode.FIXUP, SquashMode.FIXUP_KEEP_MESSAGE)) {
            Next next = Update.update(opened(), new FixupMsg.Chosen(mode));

            assertEquals(List.of(new SquashCmd(new Sha("c3"), mode)), next.cmds());
            assertEquals(Option.none(), next.model().popup(FixupPopup.class));
        }
    }

    @Test
    void cancellingClosesThePopup() {
        Next next = Update.update(opened(), new PopupMsg.Dismissed(FixupPopup.class));

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), next.model().popup(FixupPopup.class));
    }

    @Test
    void aFixupIsLoggedAndReportedAsOne() {
        assertEquals(SquashCmd.FIXUP, new SquashCmd(new Sha("aaaa"), SquashMode.FIXUP).operation());
        assertEquals(SquashCmd.FIXUP, new SquashCmd(new Sha("aaaa"), SquashMode.FIXUP_KEEP_MESSAGE).operation());

        Next next = Update.update(
                TestModels.loaded(), failed(new SquashCmd(new Sha("aaaa"), SquashMode.FIXUP), "conflict"));

        assertEquals(Option.of("Fixup failed: conflict"), errorText(next.model()));
    }

    // #722: a commit that may be pushed is read first, and a pushed one is warned about before the choice.
    @Test
    void onABranchFollowingARemoteOneTheCommitIsReadToBePushedFirst() {
        Model model = onFollowingBranch(withCommits(TIP, MIDDLE, ROOT));
        FixupMsg.Requested requested = new FixupMsg.Requested(TIP.sha());

        Next next = Update.update(model, requested);

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(new PushedCheckCmd(TIP.sha(), ORIGIN_FEATURE, Option.of(MAIN), requested)), next.cmds());
    }

    @Test
    void aPushedCommitIsWarnedAboutBeforeTheChoice() {
        Next next = Update.update(
                withCommits(TIP, MIDDLE, ROOT), new FixupMsg.Checked(TIP.sha(), Option.of(warning(false))));

        assertEquals(
                Option.of(new FixupPushedPopup(TIP, MIDDLE.sha(), warning(false))),
                next.model().popup(FixupPushedPopup.class));
        assertEquals(Option.none(), next.model().popup(FixupPopup.class));
    }

    @Test
    void goingOnAfterTheWarningAsksWhichMessageToKeep() {
        Model warned = Update.update(
                        withCommits(TIP, MIDDLE, ROOT), new FixupMsg.Checked(TIP.sha(), Option.of(warning(false))))
                .model();

        Next next = Update.update(warned, new FixupMsg.PushedConfirmed());

        assertEquals(Option.none(), next.model().popup(FixupPushedPopup.class));
        assertEquals(Option.of(new FixupPopup(TIP, MIDDLE.sha())), next.model().popup(FixupPopup.class));
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void aCommitReadNotToBePushedGoesStraightToTheChoice() {
        Next next = Update.update(withCommits(TIP, MIDDLE, ROOT), new FixupMsg.Checked(TIP.sha(), Option.none()));

        assertEquals(Option.of(new FixupPopup(TIP, MIDDLE.sha())), next.model().popup(FixupPopup.class));
    }
}
