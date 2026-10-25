package net.vanfleteren.jazygit.feature.github.checks;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.CheckSample;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.state.BranchChecksMsg;
import net.vanfleteren.jazygit.state.CheckTimesCmd;
import net.vanfleteren.jazygit.state.GitHubChecks;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PendingChecks;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Showing the checks on the tip of a branch one by one, and reading them again (ADR 32).
 */
class ChecksUpdateTest {

    private static final RemoteBranch FEATURE = new RemoteBranch("origin", new BranchName("feature"));
    private static final BranchRef MINE = new BranchRef.Local(new BranchName("mine"));
    private static final Check PASSED = new Check(
            "package",
            Checks.PASSING,
            Option.of(new CheckRun(
                    1,
                    "Build",
                    "SUCCESS",
                    Option.of(Instant.EPOCH.plusSeconds(2)),
                    Option.of(Instant.EPOCH.plusSeconds(60)),
                    1,
                    Instant.EPOCH,
                    1,
                    "push")));
    private static final PendingChecks READ =
            new PendingChecks("owner/repo", HashMap.of(new BranchName("feature"), List.of(PASSED)));

    /**
     * The sample model with a local branch {@code mine} that follows {@code origin/feature}, whose checks are
     * {@code checks} on GitHub.
     */
    private static Model model(Checks checks) {
        Branch mine = Fixtures.branch(
                new BranchName("mine"),
                false,
                new Sha("abc"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(FEATURE), 0, 0)));
        Model model = TestModels.localBranchesRead(loaded(), List.of(mine)).model();
        return Update.update(
                        model,
                        new BranchChecksMsg.Loaded(
                                new GitHubChecks(HashSet.of("origin"), HashMap.of(new BranchName("feature"), checks))))
                .model();
    }

    private static Model shown() {
        return Update.update(model(Checks.PENDING), new ChecksMsg.Requested(MINE))
                .model();
    }

    @Test
    void theChecksOfALocalBranchAreThoseOfTheBranchItFollowsOnGitHub() {
        Next next = Update.update(model(Checks.PENDING), new ChecksMsg.Requested(MINE));

        assertEquals(Option.of(ChecksPopup.loading(MINE, FEATURE)), next.model().popup(ChecksPopup.class));
        assertTrue(next.cmds().contains(new LoadChecks(FEATURE)), next.cmds().toString());
    }

    @Test
    void aRemoteBranchShowsItsOwnChecks() {
        Next next = Update.update(model(Checks.FAILING), new ChecksMsg.Requested(new BranchRef.Remote(FEATURE)));

        assertEquals(
                Option.of(ChecksPopup.loading(new BranchRef.Remote(FEATURE), FEATURE)),
                next.model().popup(ChecksPopup.class));
        assertEquals(List.of(new LoadChecks(FEATURE)), next.cmds());
    }

    @Test
    void aBranchWithoutChecksShowsNothing() {
        assertEquals(
                Option.none(),
                Update.update(model(Checks.NONE), new ChecksMsg.Requested(MINE))
                        .model()
                        .popup(ChecksPopup.class));
        assertEquals(
                Option.none(),
                Update.update(
                                model(Checks.PENDING),
                                new ChecksMsg.Requested(
                                        new BranchRef.Remote(new RemoteBranch("fork", new BranchName("feature")))))
                        .model()
                        .popup(ChecksPopup.class));
    }

    @Test
    void whatWasReadIsShownAndLearnedFrom() {
        Next next = Update.update(shown(), new ChecksMsg.Loaded(FEATURE, READ));

        assertEquals(
                Option.of(ChecksPopup.loading(MINE, FEATURE).with(Either.right(READ))),
                next.model().popup(ChecksPopup.class));
        assertEquals(
                List.of(new CheckTimesCmd.RecordCheckTimes(
                        "owner/repo",
                        List.of(CheckSample.of(PASSED).get()),
                        next.model().now(),
                        false)),
                next.cmds());
    }

    @Test
    void whatWasReadOfAnotherBranchLeavesThePopupAsItIs() {
        RemoteBranch other = new RemoteBranch("origin", new BranchName("other"));

        assertEquals(
                shown(),
                Update.update(shown(), new ChecksMsg.Loaded(other, READ)).model());
        assertEquals(
                shown(),
                Update.update(shown(), new ChecksMsg.Failed(other, refused("offline")))
                        .model());
    }

    @Test
    void whatIsReadAfterClosingIsLearnedFromWithoutShowingItAgain() {
        Model closed = Update.update(shown(), new ChecksMsg.Closed()).model();

        Next next = Update.update(closed, new ChecksMsg.Loaded(FEATURE, READ));

        assertEquals(Option.none(), next.model().popup(ChecksPopup.class));
        assertEquals(
                List.of(new CheckTimesCmd.RecordCheckTimes(
                        "owner/repo",
                        List.of(CheckSample.of(PASSED).get()),
                        next.model().now(),
                        false)),
                next.cmds());
    }

    @Test
    void theChecksKeepWhereTheyAreScrolledToWhenReadAgainAndStartAtTheTopWhenShownAgain() {
        Model scrolled = Update.update(
                        Update.update(shown(), new ChecksMsg.Loaded(FEATURE, READ))
                                .model(),
                        new Msg.ListCursorChanged(ListCursor.START.at(3)))
                .model();

        Model readAgain = Update.update(
                        Update.update(scrolled, new ChecksMsg.RefreshChosen()).model(),
                        new ChecksMsg.Loaded(FEATURE, READ))
                .model();
        assertEquals(ListCursor.START.at(3), readAgain.listCursor());

        Model shownAgain = Update.update(
                        Update.update(readAgain, new ChecksMsg.Closed()).model(), new ChecksMsg.Requested(MINE))
                .model();
        assertEquals(ListCursor.START, shownAgain.listCursor());
    }

    @Test
    void theChecksAreReadAgainByHandAndEveryMinuteOnceRead() {
        Model none = model(Checks.PENDING);
        assertEquals(Next.of(none), Update.update(none, new ChecksMsg.Tick()));

        assertEquals(List.empty(), Update.update(shown(), new ChecksMsg.Tick()).cmds());

        Model read = Update.update(shown(), new ChecksMsg.Loaded(FEATURE, READ)).model();
        Next again = Update.update(read, new ChecksMsg.RefreshChosen());

        assertEquals(List.of(new LoadChecks(FEATURE)), again.cmds());
        assertTrue(again.model().popup(ChecksPopup.class).get().refreshing());
        assertEquals(
                List.empty(), Update.update(again.model(), new ChecksMsg.Tick()).cmds());
    }

    @Test
    void failingToReadThemAgainKeepsTheChecksMarkedStale() {
        Model read = Update.update(shown(), new ChecksMsg.Loaded(FEATURE, READ)).model();

        ChecksPopup failed = Update.update(read, new ChecksMsg.Failed(FEATURE, refused("offline")))
                .model()
                .popup(ChecksPopup.class)
                .get();

        assertEquals(Option.of(Either.right(READ)), failed.checks());
        assertTrue(failed.refreshFailed().isDefined());
    }

    @Test
    void closingHidesThePopup() {
        assertEquals(
                Option.none(),
                Update.update(shown(), new ChecksMsg.Closed()).model().popup(ChecksPopup.class));
    }
}
