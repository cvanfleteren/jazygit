package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.merge.MergeCmd;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.StubStatus;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.MetadataStamp;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd.RefreshIndex;
import org.junit.jupiter.api.Test;

/**
 * The index is written anew once it changed, and every so often anyway, as the status every tick reads takes no
 * optional locks (ADR 93, ADR 96).
 */
class IndexRefreshUpdateTest {

    private static final MergeCmd MERGE = new MergeCmd(new BranchName("feature"), MergeMode.MERGE_COMMIT);
    private static final Instant NOW = Instant.parse("2026-11-09T22:00:00Z");
    private static final Option<MetadataStamp> CHECKED_OUT = stamp("checked out");
    private static final Option<MetadataStamp> REFRESHED = stamp("refreshed");
    private static final Option<MetadataStamp> COMMITTED = stamp("committed");
    private static final Option<MetadataStamp> RECENT = Option.none();

    @Test
    void theFirstStampThatCanBeToldWritesTheIndexAnew() {
        Model model = loaded().withNow(NOW).mapJobs(jobs -> jobs.withIndexRefresh(IndexRefresh.NONE));

        assertFalse(refreshes(checked(model, RECENT)));
        assertTrue(refreshes(checked(model, CHECKED_OUT)));
    }

    @Test
    void aChangedIndexIsWrittenAnewOnceItSettledAndWhatThatLeftIsNoChange() {
        Model checkedOut = checked(refreshedAt(CHECKED_OUT, NOW), RECENT).model();
        assertFalse(refreshes(checked(checkedOut, RECENT)));

        Update.Next changed = checked(checkedOut, COMMITTED);
        assertTrue(refreshes(changed));

        Model refreshed = new Msg.IndexRefreshed(true).apply(changed.model()).model();
        // The refresh wrote the index: it is too recent to tell, then looks as the refresh left it.
        assertFalse(refreshes(checked(refreshed, RECENT)));
        Model adopted = checked(refreshed, REFRESHED).model();
        assertFalse(refreshes(Update.update(adopted, new LoadMsg.MetadataChecked(List.empty(), REFRESHED))));
        assertTrue(refreshes(checked(adopted, CHECKED_OUT)));
    }

    @Test
    void anUnchangedIndexIsWrittenAnewOnlyEveryFiveMinutes() {
        Model model = refreshedAt(CHECKED_OUT, NOW);

        assertFalse(
                refreshes(checked(model.withNow(NOW.plus(IndexUpdate.FALLBACK).minusSeconds(1)), CHECKED_OUT)));
        assertTrue(refreshes(checked(model.withNow(NOW.plus(IndexUpdate.FALLBACK)), CHECKED_OUT)));
        assertTrue(refreshes(checked(model.withNow(NOW.plus(IndexUpdate.FALLBACK)), RECENT)));
    }

    @Test
    void aRefreshThatCouldNotWriteIsTriedAgainOnceTheIndexChanges() {
        Update.Next changed = checked(refreshedAt(CHECKED_OUT, NOW), COMMITTED);
        Model failed = new Msg.IndexRefreshed(false).apply(changed.model()).model();

        assertFalse(refreshes(checked(failed, COMMITTED)));
        assertTrue(refreshes(checked(failed, CHECKED_OUT)));
    }

    @Test
    void awayWhileAnOperationRunsOrWhileARefreshRunsTheIndexIsLeftAlone() {
        Model model = refreshedAt(CHECKED_OUT, NOW);
        Model away = Update.update(model, new Msg.TerminalFocused(false)).model();

        assertFalse(refreshes(checked(away, COMMITTED)));
        assertFalse(refreshes(checked(TestModels.running(model, MERGE), COMMITTED)));
        assertFalse(refreshes(checked(TestModels.running(model, new RefreshIndex()), COMMITTED)));
    }

    @Test
    void aRefreshChangesNothingShownWhetherItWroteOrNot() {
        Model model = refreshedAt(CHECKED_OUT, NOW);

        assertEquals(
                model.drawn(), new Msg.IndexRefreshed(true).apply(model).model().drawn());
        assertEquals(model.drawn(), checked(model, COMMITTED).model().drawn());
        assertEquals(new Msg.IndexRefreshed(true), new RefreshIndex().run(env(StubGit.of())));
        assertEquals(
                new Msg.IndexRefreshed(false),
                new RefreshIndex().run(env(StubGit.of().withStatus(new StubStatus() {
                    @Override
                    public Either<GitFailure, List<ExecutedCommand>> refreshIndex() {
                        return Either.left(new GitFailure(
                                new GitError.CommandFailed("fatal: Unable to create index.lock: File exists.", 128),
                                List.empty()));
                    }
                }))));
    }

    private static Update.Next checked(Model model, Option<MetadataStamp> index) {
        return Update.update(model, new LoadMsg.MetadataChecked(List.empty(), index));
    }

    private static boolean refreshes(Update.Next next) {
        return next.cmds().contains(new RefreshIndex());
    }

    private static Model refreshedAt(Option<MetadataStamp> index, Instant at) {
        return loaded().withNow(at)
                .mapJobs(jobs -> jobs.withIndexRefresh(new IndexRefresh(index, Option.of(at), false)));
    }

    private static Option<MetadataStamp> stamp(String name) {
        return Option.of(new MetadataStamp(List.of(new MetadataStamp.Entry(Path.of(name), Option.none()))));
    }

    private static Env env(Git git) {
        return new Env(git, StubGitHub.empty());
    }
}
