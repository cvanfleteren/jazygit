package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import java.time.Duration;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.state.prs.PrOverviewMsg;
import org.junit.jupiter.api.Test;

/**
 * A read from GitHub that keeps failing is started by the timers only once it waited longer the more it failed
 * (ADR 58).
 */
class FailingReadTest {

    private static final Failure FAILURE = new Failure.GitHub(new GhError.CommandFailed(
            io.vavr.control.Option.of("none of the git remotes point to a known GitHub host"), 1));

    @Test
    void aReadThatFailedOnceIsTriedAgainByTheNextTimer() {
        Model failed =
                Update.update(loaded(), new PullRequestsMsg.LoadFailed(FAILURE)).model();

        assertTrue(PullRequestsUpdate.poll(Next.of(failed)).cmds().contains(new LoadRepositoryPullRequests()));
    }

    @Test
    void aReadThatKeepsFailingWaitsLongerTheMoreItFailed() {
        Model failed = failedTimes(3);

        assertFalse(PullRequestsUpdate.poll(Next.of(failed.withNow(failed.now().plus(Duration.ofMinutes(4)))))
                .cmds()
                .contains(new LoadRepositoryPullRequests()));
        assertTrue(PullRequestsUpdate.poll(Next.of(failed.withNow(failed.now().plus(Duration.ofMinutes(5)))))
                .cmds()
                .contains(new LoadRepositoryPullRequests()));
    }

    @Test
    void whatTheUserDoesReadsItAtOnce() {
        assertTrue(PullRequestsUpdate.loadAnyway(Next.of(failedTimes(4)))
                .cmds()
                .contains(new LoadRepositoryPullRequests()));
    }

    @Test
    void aReadThatSucceedsIsNoLongerFailing() {
        Model read = Update.update(failedTimes(4), new PullRequestsMsg.Loaded(List.empty()))
                .model();

        assertTrue(read.gitHub().failing().isEmpty());
        assertTrue(PullRequestsUpdate.poll(Next.of(read)).cmds().contains(new LoadRepositoryPullRequests()));
    }

    @Test
    void theReadOfThePullRequestsOfTheRepositoryFailsOnceAsTheOverviewAndTheBranchesShareIt() {
        Model model = loaded();
        for (int i = 0; i < 2; i++) {
            model = Update.update(model, new PrOverviewMsg.RepositoryLoadFailed(FAILURE))
                    .model();
        }

        assertEquals(
                2,
                model.gitHub()
                        .failing()
                        .get(new LoadRepositoryPullRequests())
                        .get()
                        .times());
        assertFalse(PullRequestsUpdate.poll(Next.of(model)).cmds().contains(new LoadRepositoryPullRequests()));
        assertTrue(PullRequestsUpdate.poll(Next.of(model.withNow(model.now().plus(Duration.ofMinutes(2)))))
                .cmds()
                .contains(new LoadRepositoryPullRequests()));
    }

    private static Model failedTimes(int times) {
        Model model = loaded();
        for (int i = 0; i < times; i++) {
            model = Update.update(model, new PullRequestsMsg.LoadFailed(FAILURE))
                    .model();
        }
        return model;
    }
}
