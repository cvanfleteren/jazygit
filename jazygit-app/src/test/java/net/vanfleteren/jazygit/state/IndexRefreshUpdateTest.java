package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.feature.branch.merge.MergeCmd;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.StubStatus;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd.RefreshIndex;
import org.junit.jupiter.api.Test;

/**
 * The index is written anew every so often, as the status every tick reads takes no optional locks (ADR 93).
 */
class IndexRefreshUpdateTest {

    private static final MergeCmd MERGE = new MergeCmd(new BranchName("feature"), MergeMode.MERGE_COMMIT);

    @Test
    void everyThirtiethTickWritesTheIndexAnew() {
        List<Boolean> refreshed = List.range(0, 2 * Update.INDEX_REFRESH_TICKS)
                .map(ticks -> Update.update(beforeTick(loaded(), ticks), new Msg.Tick())
                        .cmds()
                        .contains(new RefreshIndex()));

        assertEquals(
                List.of(Update.INDEX_REFRESH_TICKS - 1, 2 * Update.INDEX_REFRESH_TICKS - 1),
                refreshed.zipWithIndex().filter(t -> t._1()).map(t -> t._2()));
    }

    @Test
    void awayTheIndexIsLeftAlone() {
        Model away = Update.update(loaded(), new Msg.TerminalFocused(false)).model();

        assertFalse(Update.update(beforeRefresh(away), new Msg.Tick()).cmds().contains(new RefreshIndex()));
    }

    @Test
    void whileAnOperationRunsTheIndexIsLeftAlone() {
        Model merging = TestModels.running(loaded(), MERGE);

        assertFalse(Update.update(beforeRefresh(merging), new Msg.Tick()).cmds().contains(new RefreshIndex()));
    }

    @Test
    void aRefreshThatStillRunsIsNotStartedAgain() {
        Model refreshing = TestModels.running(loaded(), new RefreshIndex());

        assertFalse(
                Update.update(beforeRefresh(refreshing), new Msg.Tick()).cmds().contains(new RefreshIndex()));
    }

    @Test
    void aRefreshChangesNothingShownWhetherItWorkedOrNot() {
        Model model = loaded();

        assertEquals(Update.Next.of(model), new Msg.IndexRefreshed().apply(model));
        assertInstanceOf(Msg.IndexRefreshed.class, new RefreshIndex().run(env(StubGit.of())));
        assertInstanceOf(
                Msg.IndexRefreshed.class,
                new RefreshIndex().run(env(StubGit.of().withStatus(new StubStatus() {
                    @Override
                    public Either<GitFailure, List<ExecutedCommand>> refreshIndex() {
                        return Either.left(new GitFailure(
                                new GitError.CommandFailed("fatal: Unable to create index.lock: File exists.", 128),
                                List.empty()));
                    }
                }))));
    }

    private static Env env(Git git) {
        return new Env(git, StubGitHub.empty());
    }

    private static Model beforeRefresh(Model model) {
        return beforeTick(model, Update.INDEX_REFRESH_TICKS - 1);
    }

    private static Model beforeTick(Model model, int ticks) {
        return model.mapJobs(jobs -> jobs.withTicks(ticks));
    }
}
