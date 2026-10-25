package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
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
import net.vanfleteren.jazygit.settings.CheckTimingsStore.Run;
import net.vanfleteren.jazygit.state.CheckTimesCmd.RecordCheckTimes;
import net.vanfleteren.jazygit.state.CheckTimesCmd.TopUpCheckTimes;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Learning how long the checks of the repository take, from what is read of them (ADR 30).
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
        return model().mapGitHub(gitHub -> gitHub.withCheckTimes(Option.of(CheckTimes.of(REPO, history))));
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
    void anEmptyHistoryIsToppedUpFromGitHubWhenAChecksIsNotDone() {
        Next next = Update.update(model(), new CheckTimesMsg.Recorded(REPO, CheckHistory.EMPTY, true));

        assertEquals(List.of(new TopUpCheckTimes(REPO)), next.cmds());
        assertTrue(times(next).toppingUp());
    }

    @Test
    void notWhenAllAreDoneOrTheHistoryIsFresh() {
        assertEquals(
                List.empty(),
                Update.update(model(), new CheckTimesMsg.Recorded(REPO, CheckHistory.EMPTY, false))
                        .cmds());
        assertEquals(
                List.empty(),
                Update.update(model(), new CheckTimesMsg.Recorded(REPO, fresh(), true))
                        .cmds());
    }

    @Test
    void anOldHistoryIsToppedUpOnTheNextReadOfACheckNotDone() {
        Model old = knowing(fresh()).withNow(NOW.plus(CheckTimings.TOP_UP_AFTER).plusSeconds(1));

        Next next = CheckTimesUpdate.read(Next.of(old), REPO, List.of(PASSED, PENDING));

        assertEquals(List.of(new TopUpCheckTimes(REPO)), next.cmds());
    }

    @Test
    void whatWasToppedUpIsKeptWhenNotKnownYet() {
        Model toppingUp = knowing(fresh())
                .mapGitHub(gitHub -> gitHub.withCheckTimes(gitHub.checkTimes().map(CheckTimes::withTopUp)));
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
    void aFailedTopUpIsNotTriedAgainForAnHour() {
        Model failed = Update.update(
                        Update.update(model(), new CheckTimesMsg.Recorded(REPO, CheckHistory.EMPTY, true))
                                .model(),
                        new CheckTimesMsg.TopUpFailed(REPO))
                .model();

        assertFalse(times(Next.of(failed)).toppingUp());
        assertEquals(
                List.empty(),
                CheckTimesUpdate.read(Next.of(failed), REPO, List.of(PENDING)).cmds());
        assertEquals(
                List.of(new TopUpCheckTimes(REPO)),
                CheckTimesUpdate.read(
                                Next.of(failed.withNow(
                                        NOW.plus(CheckTimings.TOP_UP_RETRY).plusSeconds(1))),
                                REPO,
                                List.of(PENDING))
                        .cmds());
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
        Run kept = new Run("Build/verify", 9, 100, 110, "push", NOW.minusSeconds(600));
        AtomicReference<List<Run>> file = new AtomicReference<>(List.of(kept));
        CheckTimingsStore store = new CheckTimingsStore() {
            @Override
            public Option<List<Run>> load(String repository) {
                return Option.of(file.get());
            }

            @Override
            public Try<List<Run>> change(String repository, UnaryOperator<List<Run>> change) {
                file.set(change.apply(file.get()));
                return Try.success(file.get());
            }
        };
        Env env = new Env(
                new StubGitProvider() {},
                StubGitHub.empty(),
                null,
                null,
                null,
                store,
                Repositories.NONE,
                Terminal.NONE);

        Msg recorded = new RecordCheckTimes(REPO, List.of(LEARNED), NOW, true).run(env);

        CheckHistory history = ((CheckTimesMsg.Recorded) recorded).history();
        assertEquals(
                List.of("Build/package", "Build/verify"),
                history.samples().keySet().toList().sorted());
        assertEquals(
                Duration.ofSeconds(110),
                history.samples().get("Build/verify").get().head().finished());
        assertEquals(2, file.get().size());
        assertEquals(
                history,
                ((CheckTimesMsg.Recorded) new RecordCheckTimes(REPO, List.empty(), NOW, true).run(env)).history());
    }
}
