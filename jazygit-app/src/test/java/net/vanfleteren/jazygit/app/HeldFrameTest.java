package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.assertSame;

import io.vavr.collection.List;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Jobs;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Repo;
import org.junit.jupiter.api.Test;

/**
 * The screen of the repository shown before stays while the one opened in its place reads what its panes show, for
 * {@link Jobs#SHOWN_AFTER} at most, so the panes do not show that they are loading for a frame.
 */
class HeldFrameTest {

    private static final Instant SWITCHED = Instant.parse("2026-11-23T10:00:00Z");
    private static final Model BEFORE = Model.initial("repo(main)");
    private static final Model OPENING = Model.initial("repo(wt)");
    private static final Model READ = OPENING.withRepo(Repo.LOADING
            .withStatus(new Loadable.Loaded<>(new RepoStatus(new Head.Detached(new Sha("1111")), List.empty())))
            .withBranches(new Loadable.Loaded<>(List.empty()))
            .withCommits(new Loadable.Loaded<>(List.empty()))
            .withStashes(new Loadable.Loaded<>(List.empty()))
            .withWorktrees(new Loadable.Loaded<>(List.empty())));

    private static final HeldFrame HELD = HeldFrame.of(BEFORE, SWITCHED);

    @Test
    void theScreenBeforeIsDrawnWhileThePanesOfTheRepositoryOpenedAreRead() {
        assertSame(BEFORE, HELD.drawn(OPENING, SWITCHED));
        assertSame(BEFORE, HELD.drawn(OPENING, SWITCHED.plus(Jobs.SHOWN_AFTER).minusMillis(1)));
    }

    @Test
    void theRepositoryOpenedIsDrawnOnceItsPanesWereRead() {
        assertSame(READ, HELD.drawn(READ, SWITCHED.plusMillis(50)));
    }

    @Test
    void theRepositoryOpenedIsDrawnLoadingOnceReadingTakesLongerThanACommandTakesToShow() {
        assertSame(OPENING, HELD.drawn(OPENING, SWITCHED.plus(Jobs.SHOWN_AFTER)));
    }
}
