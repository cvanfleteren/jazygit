package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.MetadataStamp;
import net.vanfleteren.jazygit.state.Cmd.CheckMetadata;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Cmd.LoadStashes;
import net.vanfleteren.jazygit.state.Cmd.LoadWorktrees;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * A tick reads the branches, the stashes and the worktrees again only once the files they are read from changed.
 */
class MetadataGuardTest {

    private static final LoadBranches BRANCHES = new LoadBranches();
    private static final LoadStashes STASHES = new LoadStashes();
    private static final LoadWorktrees WORKTREES = new LoadWorktrees();

    private static MetadataStamp stamp(String file) {
        return new MetadataStamp(List.of(new MetadataStamp.Entry(Path.of(file), Option.none())));
    }

    private static Msg checked(String branches, String stashes, String worktrees) {
        return new LoadMsg.MetadataChecked(List.of(
                Tuple.of(BRANCHES, Option.of(stamp(branches))),
                Tuple.of(STASHES, Option.of(stamp(stashes))),
                Tuple.of(WORKTREES, Option.of(stamp(worktrees)))));
    }

    // Nothing running, so a tick asks for the check.
    private static Model idle() {
        return loaded().mapJobs(jobs -> jobs.withRefreshing(HashSet.empty()));
    }

    /**
     * {@code model} once the loads that {@code next} started are done.
     */
    private static Model finished(Next next) {
        return next.model().mapJobs(jobs -> jobs.withRefreshing(HashSet.empty()));
    }

    @Test
    void aTickChecksTheFilesInsteadOfReadingWhatTheyGuard() {
        List<Cmd> cmds = Update.update(idle(), new Msg.Tick(Instant.EPOCH)).cmds();

        assertTrue(cmds.contains(new CheckMetadata()));
        assertFalse(cmds.exists(List.<Cmd>narrow(CheckMetadata.GUARDED)::contains));
    }

    @Test
    void anOperationStillReadsEverythingAtOnce() {
        List<Cmd> cmds = Update.refresh(idle()).cmds();

        assertTrue(CheckMetadata.GUARDED.forAll(cmds::contains));
        assertFalse(cmds.contains(new CheckMetadata()));
    }

    @Test
    void onlyWhatChangedIsReadAgain() {
        Next first = Update.update(idle(), checked("b1", "s1", "w1"));
        assertEquals(List.of(BRANCHES, STASHES, WORKTREES), first.cmds());

        Next same = Update.update(finished(first), checked("b1", "s1", "w1"));
        assertEquals(List.of(), same.cmds());

        Next stashed = Update.update(finished(same), checked("b1", "s2", "w1"));
        assertEquals(List.of(STASHES), stashed.cmds());
        assertTrue(stashed.model().jobs().refreshing().contains(STASHES));
    }

    @Test
    void aLoadThatStillRunsIsLookedAtAgainOnTheNextCheck() {
        Model running = Update.update(idle(), checked("b1", "s1", "w1")).model();

        // It may have read before the branches changed.
        Next whileRunning = Update.update(running, checked("b2", "s1", "w1"));
        assertEquals(List.of(), whileRunning.cmds());

        Next after = Update.update(finished(whileRunning), checked("b2", "s1", "w1"));
        assertEquals(List.of(BRANCHES), after.cmds());
    }

    @Test
    void whatCannotBeToldIsReadEveryTime() {
        Msg unknown = new LoadMsg.MetadataChecked(CheckMetadata.GUARDED.map(load -> Tuple.of(load, Option.none())));

        Next first = Update.update(idle(), unknown);
        Next second = Update.update(finished(first), unknown);

        assertEquals(CheckMetadata.GUARDED, first.cmds());
        assertEquals(CheckMetadata.GUARDED, second.cmds());
    }

    @Test
    void aLoadThatFailedIsTriedAgainOnTheNextCheck() {
        Model read = finished(Update.update(idle(), checked("b1", "s1", "w1")));

        Model failed = Update.update(read, new LoadMsg.Failed(BRANCHES, refused("boom")))
                .model();

        assertEquals(
                List.of(BRANCHES),
                Update.update(failed, checked("b1", "s1", "w1")).cmds());
    }

    @Test
    void aCheckThatFailedReadsWhatItGuards() {
        Model read = finished(Update.update(idle(), checked("b1", "s1", "w1")));

        Next next = Update.update(read, new LoadMsg.Failed(new CheckMetadata(), refused("boom")));

        assertEquals(CheckMetadata.GUARDED, next.cmds());
    }

    @Test
    void theStampsAreNotDrawn() {
        Model model = idle();
        List<Tuple2<Cmd.Load, Option<MetadataStamp>>> none = List.empty();

        Model read = finished(Update.update(model, checked("b1", "s1", "w1")));

        assertNotEquals(model, read);
        assertEquals(model.drawn(), read.drawn());
        assertEquals(
                List.of(),
                Update.update(read, new LoadMsg.MetadataChecked(none)).cmds());
    }
}
