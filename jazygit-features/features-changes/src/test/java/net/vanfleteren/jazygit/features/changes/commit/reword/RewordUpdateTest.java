package net.vanfleteren.jazygit.features.changes.commit.reword;

import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.MAIN;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.ORIGIN_FEATURE;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.onFollowingBranch;
import static net.vanfleteren.jazygit.features.changes.commit.PushedModels.warning;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedCheckCmd;
import net.vanfleteren.jazygit.kernel.state.Loadable.Loaded;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import org.junit.jupiter.api.Test;

/**
 * Rewording a commit that is pushed already: a warning comes before the new message is asked for (#722).
 */
class RewordUpdateTest {

    private static final Commit LAST =
            Fixtures.commit(new Sha("c2"), "Ada", "ada@example.com", Instant.EPOCH, "Last", "", List.of(new Sha("c1")));

    private static Model withCommits() {
        return TestModels.loaded().mapRepo(repo -> repo.withCommits(new Loaded<>(List.of(LAST))));
    }

    @Test
    void onABranchFollowingARemoteOneTheCommitIsReadToBePushedFirst() {
        RewordMsg.Requested requested = new RewordMsg.Requested(LAST.sha());

        Next next = Update.update(onFollowingBranch(withCommits()), requested);

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(new PushedCheckCmd(LAST.sha(), ORIGIN_FEATURE, Option.of(MAIN), requested)), next.cmds());
    }

    @Test
    void aPushedCommitIsWarnedAboutBeforeTheMessageIsAskedFor() {
        Next next = Update.update(withCommits(), new RewordMsg.Checked(LAST.sha(), Option.of(warning(true))));

        assertEquals(
                Option.of(new RewordPushedPopup(LAST, warning(true))),
                next.model().popup());
    }

    @Test
    void goingOnAfterTheWarningAsksForTheMessage() {
        Model warned = Update.update(withCommits(), new RewordMsg.Checked(LAST.sha(), Option.of(warning(false))))
                .model();

        Next next = Update.update(warned, new RewordMsg.PushedConfirmed());

        assertEquals(Option.of(new RewordPopup(LAST)), next.model().popup());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void cancellingTheWarningRewordsNothing() {
        Model warned = Update.update(withCommits(), new RewordMsg.Checked(LAST.sha(), Option.of(warning(false))))
                .model();

        Next next = Update.update(warned, new PopupMsg.Dismissed(RewordPushedPopup.class));

        assertEquals(Option.none(), next.model().popup());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void aCommitReadNotToBePushedGoesStraightToTheMessage() {
        Next next = Update.update(withCommits(), new RewordMsg.Checked(LAST.sha(), Option.none()));

        assertEquals(Option.of(new RewordPopup(LAST)), next.model().popup());
    }
}
