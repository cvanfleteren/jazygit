package net.vanfleteren.jazygit.features.github.checks;

import static net.vanfleteren.jazygit.features.github.checks.RerunPlanTest.check;
import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.checks.StubChecks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.gh.process.GhFailure;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.github.state.BranchChecksMsg;
import net.vanfleteren.jazygit.features.github.state.GitHubChecks;
import net.vanfleteren.jazygit.features.github.state.LoadBranchChecks;
import net.vanfleteren.jazygit.features.github.state.PendingChecks;
import net.vanfleteren.jazygit.features.github.state.Reading;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.ErrorPopup;
import org.junit.jupiter.api.Test;

/**
 * Running the failed checks on the tip of a branch again from the checks popup (ADR 135).
 */
class RerunUpdateTest {

    private static final RemoteBranch FEATURE = new RemoteBranch("origin", new BranchName("feature"));
    private static final BranchRef MINE = new BranchRef.Local(new BranchName("mine"));
    private static final Check BUILD_FAILED = check("verify", 1, "Build", Checks.FAILING, "FAILURE");
    private static final Check E2E_FAILED = check("e2e", 2, "E2E", Checks.FAILING, "FAILURE");
    private static final RerunPlan.Run BUILD = new RerunPlan.Run(1, "Build", "pull_request", 1);
    private static final RerunPlan.Run E2E = new RerunPlan.Run(2, "E2E", "pull_request", 1);
    private static final Instant ASKED = Instant.EPOCH.plusSeconds(100);

    /**
     * The checks popup on {@code mine}, which follows {@code origin/feature}, showing {@code checks} as read.
     */
    private static Model shown(List<Check> checks) {
        Branch mine = Fixtures.branch(
                new BranchName("mine"),
                false,
                new Sha("abc"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(FEATURE), 0, 0)));
        Model model = Update.update(
                        TestModels.localBranchesRead(loaded(), List.of(mine)).model(),
                        new BranchChecksMsg.Loaded(new GitHubChecks(
                                HashSet.of(new RemoteName("origin")),
                                HashMap.of(new BranchName("feature"), Checks.FAILING))))
                .model();
        Model asked = Update.update(model, new ChecksMsg.Requested(MINE)).model();
        return Update.update(asked, new ChecksMsg.Loaded(FEATURE, read(checks)))
                .model()
                .withNow(ASKED);
    }

    private static PendingChecks read(List<Check> checks) {
        return new PendingChecks("owner/repo", HashMap.of(new BranchName("feature"), checks));
    }

    private static ChecksPopup checks(Model model) {
        return model.popup(ChecksPopup.class).get();
    }

    /**
     * The checks popup once both failed runs were asked to run again and confirmed, before GitHub answered.
     */
    private static Next confirmed() {
        Model asking = Update.update(shown(List.of(BUILD_FAILED, E2E_FAILED)), new ChecksMsg.RerunRequested())
                .model();
        return Update.update(asking, new ChecksMsg.RerunConfirmed());
    }

    private static final ExecutedCommand POSTED = ExecutedCommand.of("gh", "api", "--method", "POST", "…");

    @Test
    void rAsksFirstNamingTheRunsItRunsAgain() {
        Model model = shown(List.of(BUILD_FAILED, E2E_FAILED));

        Model asking = Update.update(model, new ChecksMsg.RerunRequested()).model();

        assertEquals(
                Option.of(new RerunPopup(checks(model), new RerunPlan(List.of(BUILD, E2E), List.empty()))),
                asking.popup(RerunPopup.class));
    }

    @Test
    void withNothingToRunAgainTheChecksSayWhy() {
        Model model = shown(List.of(check("verify", 1, "Build", Checks.PASSING, "SUCCESS")));

        Next next = Update.update(model, new ChecksMsg.RerunRequested());

        assertEquals(
                Option.of(new RerunNote.Nothing(RerunPlan.Why.NONE_FAILED)),
                checks(next.model()).rerun());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void escapeGoesBackToTheChecksRunningNothingAgain() {
        Model model = shown(List.of(BUILD_FAILED));
        Model asking = Update.update(model, new ChecksMsg.RerunRequested()).model();

        Next next = Update.update(asking, new ChecksMsg.RerunCancelled());

        assertEquals(Option.of(checks(model)), next.model().popup(ChecksPopup.class));
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void enterGoesBackToTheChecksWhichSayWhenTheRunsWereAskedToRunAgain() {
        Next next = confirmed();

        assertEquals(
                Option.of(RerunNote.Asked.at(ASKED, List.of(1L, 2L))),
                checks(next.model()).rerun());
        assertEquals(List.of(new RerunCmd(FEATURE, List.of(BUILD, E2E))), next.cmds());
    }

    @Test
    void checksReadWhileAskingShowOnceBackToThem() {
        Model asking = Update.update(shown(List.of(BUILD_FAILED)), new ChecksMsg.RerunRequested())
                .model();
        Check passed = check("verify", 1, "Build", Checks.PASSING, "SUCCESS");

        Model read = Update.update(asking, new ChecksMsg.Loaded(FEATURE, read(List.of(passed))))
                .model();
        Model back = Update.update(read, new ChecksMsg.RerunCancelled()).model();

        assertEquals(List.of(passed), checks(back).checksRead());
    }

    @Test
    void onceGitHubRanThemAgainTheChecksSaySoAndAreReadAgainAsAskedAndTheBranchesToo() {
        Next asked = confirmed();
        RerunCmd cmd = new RerunCmd(FEATURE, List.of(BUILD, E2E));
        List<RerunCmd.Outcome> outcomes =
                List.of(new RerunCmd.Outcome(BUILD, Option.none()), new RerunCmd.Outcome(E2E, Option.none()));

        Next next = Update.update(asked.model(), new ChecksMsg.Rerun(cmd, outcomes, List.of(POSTED, POSTED)));

        ChecksPopup popup = checks(next.model());
        assertEquals(Option.of(RerunNote.Asked.at(ASKED, List.of(1L, 2L)).answered(outcomes)), popup.rerun());
        assertEquals(Reading.ASKED, popup.reading());
        assertTrue(next.cmds().contains(new LoadChecks(FEATURE)), next.cmds().toString());
        assertTrue(
                next.cmds().exists(LoadBranchChecks.class::isInstance),
                next.cmds().toString());
        assertEquals(Option.none(), next.model().outcome().error());
        assertEquals(
                List.of(POSTED, POSTED),
                next.model().outcome().commandLog().last().commands());
    }

    @Test
    void aRunGitHubRefusedIsAnErrorTheOthersStayRunAgain() {
        Failure forbidden = refused("Must have admin rights to Repository. (HTTP 403)");
        RerunCmd cmd = new RerunCmd(FEATURE, List.of(BUILD, E2E));
        List<RerunCmd.Outcome> outcomes =
                List.of(new RerunCmd.Outcome(BUILD, Option.none()), new RerunCmd.Outcome(E2E, Option.of(forbidden)));

        Next next = Update.update(confirmed().model(), new ChecksMsg.Rerun(cmd, outcomes, List.of(POSTED)));

        assertEquals(
                Option.of(new AppError.OperationFailed(RerunCmd.RERUN, Option.of("E2E"), forbidden)),
                next.model().outcome().error());
        assertEquals(
                List.of(new RerunCmd.Outcome(E2E, Option.of(forbidden))),
                checks(next.model()).asked().get().refused());
    }

    @Test
    void onceTheChecksAreClosedARefusedRunStillShowsAsAnError() {
        Failure forbidden = refused("Must have admin rights to Repository. (HTTP 403)");
        RerunCmd cmd = new RerunCmd(FEATURE, List.of(BUILD));
        Model closed =
                Update.update(confirmed().model(), new ChecksMsg.Closed()).model();

        Next next = Update.update(
                closed,
                new ChecksMsg.Rerun(cmd, List.of(new RerunCmd.Outcome(BUILD, Option.of(forbidden))), List.empty()));

        assertEquals(
                Option.of(new ErrorPopup(new AppError.OperationFailed(RerunCmd.RERUN, Option.of("Build"), forbidden))),
                next.model().popup(ErrorPopup.class));
        assertEquals(List.empty(), next.cmds().filter(LoadBranchChecks.class::isInstance));
    }

    @Test
    void theChecksAreReadEveryTickUntilARunRunAgainShowsNotDoneForAMinuteAtMost() {
        RerunCmd cmd = new RerunCmd(FEATURE, List.of(BUILD));
        Model asking = Update.update(shown(List.of(BUILD_FAILED)), new ChecksMsg.RerunRequested())
                .model();
        Model confirmed = Update.update(asking, new ChecksMsg.RerunConfirmed()).model();
        Model answered = Update.update(
                        confirmed,
                        new ChecksMsg.Rerun(cmd, List.of(new RerunCmd.Outcome(BUILD, Option.none())), List.empty()))
                .model();
        // GitHub does not show the run run again yet.
        Model stillFailed = Update.update(answered, new ChecksMsg.Loaded(FEATURE, read(List.of(BUILD_FAILED))))
                .model();

        assertEquals(
                List.of(new LoadChecks(FEATURE)),
                Update.update(stillFailed.withNow(ASKED.plusSeconds(10)), new ChecksMsg.Tick())
                        .cmds());
        // A minute on, the checks are done and were read a minute ago, which reads them anyway (ADR 34).
        assertTrue(checks(stillFailed).rerunAwaited(ASKED.plusSeconds(59)));
        assertFalse(checks(stillFailed).rerunAwaited(ASKED.plusSeconds(60)));

        Model running = Update.update(
                        stillFailed,
                        new ChecksMsg.Loaded(FEATURE, read(List.of(check("verify", 1, "Build", Checks.PENDING, "")))))
                .model();
        assertTrue(checks(running).asked().get().seen());
    }

    @Test
    void rDoesNothingWhileTheRunsAskedToRunAgainAreStillBeingRunAgainAndTheAnswerShows() {
        RerunCmd cmd = new RerunCmd(FEATURE, List.of(BUILD, E2E));
        List<RerunCmd.Outcome> outcomes =
                List.of(new RerunCmd.Outcome(BUILD, Option.none()), new RerunCmd.Outcome(E2E, Option.none()));
        Model asked = confirmed().model();

        Next again = Update.update(asked, new ChecksMsg.RerunRequested());
        assertEquals(Next.of(asked), again);

        Model answered = Update.update(again.model(), new ChecksMsg.Rerun(cmd, outcomes, List.empty()))
                .model();
        assertEquals(Option.none(), answered.popup(RerunPopup.class));
        assertEquals(
                Option.of(RerunNote.Asked.at(ASKED, List.of(1L, 2L)).answered(outcomes)),
                checks(answered).rerun());
        // Still waited for to show running, the checks still show them failed and done.
        assertEquals(
                Option.none(),
                Update.update(answered, new ChecksMsg.RerunRequested()).model().popup(RerunPopup.class));
    }

    @Test
    void anAnswerThatComesWhileAskingToRunAgainShowsOnceBackToTheChecks() {
        RerunCmd cmd = new RerunCmd(FEATURE, List.of(BUILD));
        List<RerunCmd.Outcome> outcomes = List.of(new RerunCmd.Outcome(BUILD, Option.none()));
        ChecksPopup checks = checks(confirmed().model());
        Model asking =
                confirmed().model().openPopup(new RerunPopup(checks, new RerunPlan(List.of(BUILD), List.empty())));

        Model answered = Update.update(asking, new ChecksMsg.Rerun(cmd, outcomes, List.empty()))
                .model();
        Model back = Update.update(answered, new ChecksMsg.RerunCancelled()).model();

        assertEquals(
                Option.of(RerunNote.Asked.at(ASKED, List.of(1L, 2L)).answered(outcomes)),
                checks(back).rerun());
    }

    @Test
    void whyNothingIsRunAgainGoesOnceTheChecksAreReadAgain() {
        Model model = shown(List.of(check("verify", 1, "Build", Checks.PASSING, "SUCCESS")));
        Model nothing = Update.update(model, new ChecksMsg.RerunRequested()).model();

        Model read = Update.update(nothing, new ChecksMsg.Loaded(FEATURE, read(List.of(BUILD_FAILED))))
                .model();

        assertEquals(Option.none(), checks(read).rerun());
    }

    @Test
    void onceGitHubRefusedEveryRunNoneIsWaitedForAndRCanAskAgain() {
        Failure forbidden = refused("Must have admin rights to Repository. (HTTP 403)");
        RerunCmd cmd = new RerunCmd(FEATURE, List.of(BUILD, E2E));
        List<RerunCmd.Outcome> outcomes = List.of(
                new RerunCmd.Outcome(BUILD, Option.of(forbidden)), new RerunCmd.Outcome(E2E, Option.of(forbidden)));

        Model answered = Update.update(confirmed().model(), new ChecksMsg.Rerun(cmd, outcomes, List.empty()))
                .model();

        assertFalse(checks(answered).rerunAwaited(ASKED.plusSeconds(10)));
        assertTrue(Update.update(answered, new ChecksMsg.RerunRequested())
                .model()
                .popup(RerunPopup.class)
                .isDefined());
    }

    @Test
    void eachRunIsAskedToRunAgainAndOneRefusedDoesNotStopTheOthers() {
        GhFailure forbidden = new GhFailure(new GhError.TimedOut("rerun"), List.of(POSTED));
        java.util.List<Long> asked = new java.util.ArrayList<>();
        Env env = new Env(StubGit.of(), StubGitHub.of().withChecks(new StubChecks() {
            @Override
            public Either<GhFailure, List<ExecutedCommand>> rerunFailedJobs(long runId) {
                asked.add(runId);
                return runId == 1 ? Either.left(forbidden) : Either.right(List.of(POSTED));
            }
        }));
        RerunCmd cmd = new RerunCmd(FEATURE, List.of(BUILD, E2E));

        assertEquals(
                new ChecksMsg.Rerun(
                        cmd,
                        List.of(
                                new RerunCmd.Outcome(BUILD, Option.of(Failure.of(forbidden))),
                                new RerunCmd.Outcome(E2E, Option.none())),
                        List.of(POSTED, POSTED)),
                cmd.run(env));
        assertEquals(java.util.List.of(1L, 2L), asked);
    }
}
