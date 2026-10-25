package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.RecentChecks;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.CheckSample;
import net.vanfleteren.jazygit.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.settings.CheckTimingsStore;
import net.vanfleteren.jazygit.settings.CheckTimingsStore.Kept;
import net.vanfleteren.jazygit.settings.CheckTimingsStore.Run;
import net.vanfleteren.jazygit.state.CheckTimesCmd.RecordCheckTimes;
import net.vanfleteren.jazygit.state.CheckTimesCmd.TopUpCheckTimes;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Learning how long the checks of the repository take, from what is read of them (ADR 32).
 */
class CheckTimesUpdateTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final String REPO = "owner/repo";
    private static final Check PASSED = check(1, "package", "SUCCESS", 1);
    private static final Check PENDING = check(2, "acceptance", "", 1);
    private static final CheckSample LEARNED = CheckSample.of(PASSED).get();

    private static Check check(long id, String name, String conclusion, int attempt) {
        boolean done = !conclusion.isEmpty();
        return new Check(
                name,
                done ? Checks.PASSING : Checks.PENDING,
                Option.of(new CheckRun(
                        id,
                        "Build",
                        conclusion,
                        Option.of(NOW.minusSeconds(100)),
                        Option.when(done, NOW.minusSeconds(40)),
                        7,
                        NOW.minusSeconds(120),
                        attempt,
                        "pull_request")));
    }

    private static Model model() {
        return loaded().withNow(NOW);
    }

    private static Model knowing(CheckHistory history) {
        return knowing(history, Option.none());
    }

    private static Model knowing(CheckHistory history, Option<Instant> toppedUp) {
        return model().mapGitHub(gitHub -> gitHub.withCheckTimes(Option.of(CheckTimes.of(REPO, history, toppedUp))));
    }

    /**
     * Keeps what is kept of the repository in {@code file}.
     */
    private static CheckTimingsStore keeping(AtomicReference<Kept> file) {
        return new CheckTimingsStore() {
            @Override
            public Option<Kept> load(String repository) {
                return Option.of(file.get());
            }

            @Override
            public Try<Kept> change(String repository, UnaryOperator<Kept> change) {
                file.set(change.apply(file.get()));
                return Try.success(file.get());
            }
        };
    }

    private static Env env(CheckTimingsStore store) {
        return new Env(
                new StubGitProvider() {},
                StubGitHub.empty(),
                null,
                null,
                null,
                store,
                Repositories.NONE,
                Terminal.NONE);
    }

    /**
     * {@code model} after {@code next}'s cmds ran against {@code env}, and what they told came back.
     */
    private static Next ran(Next next, Env env) {
        return next.cmds().foldLeft(Next.of(next.model()), (done, cmd) -> {
            Next after = Update.update(done.model(), cmd.run(env));
            return new Next(after.model(), done.cmds().appendAll(after.cmds()));
        });
    }

    private static CheckHistory fresh() {
        return CheckHistory.EMPTY.with(List.of(LEARNED), NOW);
    }

    private static CheckTimes times(Next next) {
        return next.model().gitHub().checkTimes().get();
    }

    @Test
    void theFirstReadOfARepositoryReadsItsHistoryAndKeepsWhatPassed() {
        Next next = CheckTimesUpdate.read(Next.of(model()), REPO, List.of(PASSED, PENDING));

        assertEquals(List.of(new RecordCheckTimes(REPO, List.of(LEARNED), NOW, true)), next.cmds());
    }

    @Test
    void aReadOfNoRepositoryLearnsNothing() {
        assertEquals(
                List.empty(),
                CheckTimesUpdate.read(Next.of(model()), "", List.of(PASSED)).cmds());
    }

    @Test
    void onlyRunsNotKnownYetAreKept() {
        Check another = check(3, "verify", "SUCCESS", 1);

        Next next = CheckTimesUpdate.read(Next.of(knowing(fresh())), REPO, List.of(PASSED, another));

        assertEquals(
                List.of(new RecordCheckTimes(
                        REPO, List.of(CheckSample.of(another).get()), NOW, false)),
                next.cmds());
    }

    @Test
    void aReRunAndAFailureTeachNothing() {
        Next next = CheckTimesUpdate.read(
                Next.of(knowing(fresh())),
                REPO,
                List.of(check(3, "verify", "SUCCESS", 2), check(4, "lint", "FAILURE", 1)));

        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void aRepositoryNeverToppedUpIsToppedUpFromGitHubWhenACheckIsNotDone() {
        Next next = Update.update(model(), new CheckTimesMsg.Recorded(REPO, CheckHistory.EMPTY, Option.none(), true));

        assertEquals(List.of(new TopUpCheckTimes(REPO, NOW)), next.cmds());
        assertTrue(times(next).toppingUp());
        assertEquals(Option.of(NOW), times(next).toppedUp());
    }

    @Test
    void aNewRepositoryIsToppedUpThoughTheReadBringsACheckThatPassed() {
        Next next = ran(
                CheckTimesUpdate.read(Next.of(model()), REPO, List.of(PASSED, PENDING)), env(CheckTimingsStore.NONE));

        assertEquals(List.of(new TopUpCheckTimes(REPO, NOW)), next.cmds());
        assertEquals(fresh(), times(next).history());
    }

    @Test
    void notWhenAllAreDoneOrItWasToppedUpWithinADay() {
        assertEquals(
                List.empty(),
                Update.update(model(), new CheckTimesMsg.Recorded(REPO, CheckHistory.EMPTY, Option.none(), false))
                        .cmds());
        assertEquals(
                List.empty(),
                Update.update(
                                model(),
                                new CheckTimesMsg.Recorded(
                                        REPO,
                                        CheckHistory.EMPTY,
                                        Option.of(NOW.minus(CheckTimings.TOP_UP_AFTER)),
                                        true))
                        .cmds());
    }

    @Test
    void aTopUpOverADayAgoIsDoneAgainThoughTheHistoryIsFresh() {
        Model old =
                knowing(fresh(), Option.of(NOW.minus(CheckTimings.TOP_UP_AFTER).minusSeconds(1)));

        Next next = CheckTimesUpdate.read(Next.of(old), REPO, List.of(PASSED, PENDING));

        assertEquals(List.of(new TopUpCheckTimes(REPO, NOW)), next.cmds());
    }

    @Test
    void whatWasToppedUpIsKeptWhenNotKnownYet() {
        Model toppingUp = knowing(fresh())
                .mapGitHub(gitHub -> gitHub.withCheckTimes(gitHub.checkTimes().map(times -> times.withTopUp(NOW))));
        Check another = check(3, "verify", "SUCCESS", 1);

        Next next = Update.update(
                toppingUp, new CheckTimesMsg.ToppedUp(REPO, new RecentChecks(REPO, List.of(PASSED, another))));

        assertFalse(times(next).toppingUp());
        assertEquals(Option.of(NOW), times(next).toppedUp());
        assertEquals(
                List.of(new RecordCheckTimes(
                        REPO, List.of(CheckSample.of(another).get()), NOW, false)),
                next.cmds());
    }

    @Test
    void aFailedOrEmptyTopUpIsNotTriedAgainForADay() {
        Model started = Update.update(
                        model(), new CheckTimesMsg.Recorded(REPO, CheckHistory.EMPTY, Option.none(), true))
                .model();

        for (CheckTimesMsg done : List.of(
                new CheckTimesMsg.TopUpFailed(REPO),
                new CheckTimesMsg.ToppedUp(REPO, new RecentChecks(REPO, List.empty())))) {
            Model after = Update.update(started, done).model();

            assertFalse(times(Next.of(after)).toppingUp());
            assertEquals(
                    List.empty(),
                    CheckTimesUpdate.read(
                                    Next.of(after.withNow(NOW.plus(CheckTimings.TOP_UP_AFTER))), REPO, List.of(PENDING))
                            .cmds());
            assertEquals(
                    List.of(new TopUpCheckTimes(
                            REPO, NOW.plus(CheckTimings.TOP_UP_AFTER).plusSeconds(1))),
                    CheckTimesUpdate.read(
                                    Next.of(after.withNow(
                                            NOW.plus(CheckTimings.TOP_UP_AFTER).plusSeconds(1))),
                                    REPO,
                                    List.of(PENDING))
                            .cmds());
        }
    }

    @Test
    void aFileThatCannotBeWrittenDoesNotBringTheTopUpBack() {
        Model failed = Update.update(
                        Update.update(
                                        model(),
                                        new CheckTimesMsg.Recorded(REPO, CheckHistory.EMPTY, Option.none(), true))
                                .model(),
                        new CheckTimesMsg.TopUpFailed(REPO))
                .model();

        // The file still says it was never topped up, or could not keep the samples.
        assertEquals(
                List.empty(),
                Update.update(failed, new CheckTimesMsg.Recorded(REPO, fresh(), Option.none(), true))
                        .cmds());
        assertEquals(
                List.empty(),
                Update.update(failed, new CheckTimesMsg.NotRecorded(REPO, List.of(LEARNED), true))
                        .cmds());
        assertEquals(Option.of(NOW), times(Next.of(failed)).toppedUp());
    }

    @Test
    void aTopUpAnotherJazygitDidWithinADayIsNotDoneAgain() {
        Instant other = NOW.minusSeconds(3600);
        AtomicReference<Kept> file = new AtomicReference<>(new Kept(List.empty(), Option.of(other)));
        Env env = new Env(
                new StubGitProvider() {},
                new StubGitHub() {
                    @Override
                    public Either<GhError, RecentChecks> recentChecks() {
                        throw new AssertionError("read from GitHub again");
                    }
                },
                null,
                null,
                null,
                keeping(file),
                Repositories.NONE,
                Terminal.NONE);

        Msg skipped = new TopUpCheckTimes(REPO, NOW).run(env);

        assertEquals(new CheckTimesMsg.TopUpSkipped(REPO, other), skipped);
        assertEquals(Option.of(other), file.get().toppedUp());
        Next next = Update.update(
                knowing(fresh())
                        .mapGitHub(gitHub ->
                                gitHub.withCheckTimes(gitHub.checkTimes().map(times -> times.withTopUp(NOW)))),
                skipped);
        assertFalse(times(next).toppingUp());
        assertEquals(Option.of(other), times(next).toppedUp());
    }

    @Test
    void whenAnotherJazygitToppedUpLaterThatCounts() {
        Instant later = NOW.minusSeconds(60);

        Next next = Update.update(
                knowing(fresh(), Option.of(NOW.minus(CheckTimings.TOP_UP_AFTER).minusSeconds(1))),
                new CheckTimesMsg.Recorded(REPO, fresh(), Option.of(later), true));

        assertEquals(List.empty(), next.cmds());
        assertEquals(Option.of(later), times(next).toppedUp());
    }

    @Test
    void runsThatCouldNotBeKeptAreStillLearned() {
        Next next = Update.update(model(), new CheckTimesMsg.NotRecorded(REPO, List.of(LEARNED), false));

        assertEquals(fresh(), times(next).history());
    }

    @Test
    void readingThePullRequestLearnsFromItsChecks() {
        PullRequestDetails details = new PullRequestDetails(
                42,
                "t",
                "",
                PullRequestDetails.State.OPEN,
                false,
                "feature",
                "main",
                NOW,
                NOW,
                0,
                0,
                0,
                1,
                PullRequestDetails.Mergeable.UNKNOWN,
                net.vanfleteren.jazygit.gh.model.PullRequest.Review.NONE,
                0,
                PullRequestDetails.ReviewThreads.NONE,
                List.empty(),
                List.empty(),
                List.of(PASSED),
                "abc",
                Option.none(),
                Option.none(),
                REPO);

        Next next = Update.update(model(), new PullRequestMsg.DetailsLoaded(42, details));

        assertTrue(next.cmds().contains(new RecordCheckTimes(REPO, List.of(LEARNED), NOW, false)), next.toString());
    }

    @Test
    void recordingAddsToWhatIsKeptAndTellsAllThatIsKept() {
        Run run = new Run("Build/verify", 9, 100, 110, "push", NOW.minusSeconds(600));
        Instant toppedUp = NOW.minus(CheckTimings.TOP_UP_AFTER).minusSeconds(1);
        AtomicReference<Kept> file = new AtomicReference<>(new Kept(List.of(run), Option.of(toppedUp)));
        Env env = env(keeping(file));

        CheckTimesMsg.Recorded recorded =
                (CheckTimesMsg.Recorded) new RecordCheckTimes(REPO, List.of(LEARNED), NOW, true).run(env);

        CheckHistory history = recorded.history();
        assertEquals(Option.of(toppedUp), recorded.toppedUp());
        assertEquals(
                List.of("Build/package", "Build/verify"),
                history.samples().keySet().toList().sorted());
        assertEquals(
                Duration.ofSeconds(110),
                history.samples().get("Build/verify").get().head().finished());
        assertEquals(2, file.get().runs().size());
        assertEquals(
                history,
                ((CheckTimesMsg.Recorded) new RecordCheckTimes(REPO, List.empty(), NOW, true).run(env)).history());

        new TopUpCheckTimes(REPO, NOW).run(env);

        assertEquals(Option.of(NOW), file.get().toppedUp());
        assertEquals(2, file.get().runs().size());
    }
}
