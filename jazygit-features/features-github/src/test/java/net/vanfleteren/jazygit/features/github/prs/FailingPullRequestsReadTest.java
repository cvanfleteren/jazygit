package net.vanfleteren.jazygit.features.github.prs;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import java.time.Duration;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.github.GitHubState;
import org.junit.jupiter.api.Test;

/**
 * The read of the pull requests of the repository, when it keeps failing, is started by the timers only once it
 * waited longer the more it failed (ADR 58).
 */
class FailingPullRequestsReadTest {

    private static final Failure FAILURE = new Failure.GitHub(new GhError.CommandFailed(
            io.vavr.control.Option.of("none of the git remotes point to a known GitHub host"), 1));

    @Test
    void aReadThatFailedOnceIsTriedAgainByTheNextTimer() {
        Model failed = PullRequestReads.failed(loaded(), FAILURE).model();

        assertTrue(PullRequestReads.poll(Next.of(failed)).cmds().contains(new LoadRepositoryPullRequests()));
    }

    @Test
    void aReadThatKeepsFailingWaitsLongerTheMoreItFailed() {
        Model failed = failedTimes(3);

        assertFalse(PullRequestReads.poll(Next.of(failed.withNow(failed.now().plus(Duration.ofMinutes(4)))))
                .cmds()
                .contains(new LoadRepositoryPullRequests()));
        assertTrue(PullRequestReads.poll(Next.of(failed.withNow(failed.now().plus(Duration.ofMinutes(5)))))
                .cmds()
                .contains(new LoadRepositoryPullRequests()));
    }

    @Test
    void whatTheUserDoesReadsItAtOnce() {
        assertTrue(
                PullRequestReads.loadAnyway(Next.of(failedTimes(4))).cmds().contains(new LoadRepositoryPullRequests()));
    }

    @Test
    void aReadThatSucceedsIsNoLongerFailing() {
        Model read = PullRequestReads.loaded(failedTimes(4), List.empty(), "owner/repo")
                .model();

        assertTrue(read.part(GitHubState.PART).failing().isEmpty());
        assertTrue(PullRequestReads.poll(Next.of(read)).cmds().contains(new LoadRepositoryPullRequests()));
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
                model.part(GitHubState.PART)
                        .failing()
                        .get(new LoadRepositoryPullRequests())
                        .get()
                        .times());
        assertFalse(PullRequestReads.poll(Next.of(model)).cmds().contains(new LoadRepositoryPullRequests()));
        assertTrue(PullRequestReads.poll(Next.of(model.withNow(model.now().plus(Duration.ofMinutes(2)))))
                .cmds()
                .contains(new LoadRepositoryPullRequests()));
    }

    private static Model failedTimes(int times) {
        Model model = loaded();
        for (int i = 0; i < times; i++) {
            model = PullRequestReads.failed(model, FAILURE).model();
        }
        return model;
    }
}
