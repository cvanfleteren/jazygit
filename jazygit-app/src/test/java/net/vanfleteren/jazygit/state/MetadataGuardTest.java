package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.AllBranches;
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
        return loaded();
    }

    /**
     * {@code model} once the loads that {@code next} started came back.
     */
    private static Model finished(Next next) {
        return finished(next.model(), next.cmds());
    }

    private static Model finished(Model model, List<Cmd> loads) {
        return loads.foldLeft(
                model,
                (done, load) -> TestModels.done(done, load, cameBack(load)).model());
    }

    private static Msg cameBack(Cmd cmd) {
        return switch (cmd) {
            case LoadBranches() -> TestModels.branchesLoaded(new AllBranches(TestModels.BRANCHES, List.empty()));
            case LoadStashes() -> new LoadMsg.StashesLoaded(List.empty());
            case LoadWorktrees() -> new LoadMsg.WorktreesLoaded(List.empty());
            default -> throw new IllegalArgumentException("Not guarded: " + cmd);
        };
    }

    @Test
    void aTickChecksTheFilesInsteadOfReadingWhatTheyGuard() {
        List<Cmd> cmds = Update.update(idle(), new Msg.Tick()).cmds();

        assertTrue(cmds.contains(new CheckMetadata()));
        assertFalse(cmds.exists(List.<Cmd>narrow(CheckMetadata.GUARDED)::contains));
    }

    @Test
    void aTickChecksAgainOnceTheLastCheckCameBack() {
        Next checking = TestModels.stepped(idle(), new Msg.Tick());

        assertFalse(Update.update(checking.model(), new Msg.Tick()).cmds().contains(new CheckMetadata()));

        Model answered = finished(TestModels.done(checking.model(), new CheckMetadata(), checked("b1", "s1", "w1")));
        assertTrue(Update.update(answered, new Msg.Tick()).cmds().contains(new CheckMetadata()));
    }

    @Test
    void anOperationStillReadsEverythingAtOnce() {
        List<Cmd> cmds = Update.refresh(idle()).cmds();

        assertTrue(CheckMetadata.GUARDED.forAll(cmds::contains));
        assertFalse(cmds.contains(new CheckMetadata()));
    }

    @Test
    void onlyWhatChangedIsReadAgain() {
        Next first = TestModels.stepped(idle(), checked("b1", "s1", "w1"));
        assertEquals(List.of(BRANCHES, STASHES, WORKTREES), first.cmds());

        Next same = TestModels.stepped(finished(first), checked("b1", "s1", "w1"));
        assertEquals(List.of(), same.cmds());

        Next stashed = TestModels.stepped(finished(same), checked("b1", "s2", "w1"));
        assertEquals(List.of(STASHES), stashed.cmds());
        assertTrue(stashed.model().jobs().runs(STASHES));
    }

    @Test
    void aLoadThatStillRunsIsLookedAtAgainOnTheNextCheck() {
        Next running = TestModels.stepped(idle(), checked("b1", "s1", "w1"));

        // It may have read before the branches changed.
        Next whileRunning = TestModels.stepped(running.model(), checked("b2", "s1", "w1"));
        assertEquals(List.of(), whileRunning.cmds());

        Next after = TestModels.stepped(finished(whileRunning.model(), running.cmds()), checked("b2", "s1", "w1"));
        assertEquals(List.of(BRANCHES), after.cmds());
    }

    @Test
    void whatCannotBeToldIsReadEveryTime() {
        Msg unknown = new LoadMsg.MetadataChecked(CheckMetadata.GUARDED.map(load -> Tuple.of(load, Option.none())));

        Next first = TestModels.stepped(idle(), unknown);
        Next second = TestModels.stepped(finished(first), unknown);

        assertEquals(CheckMetadata.GUARDED, first.cmds());
        assertEquals(CheckMetadata.GUARDED, second.cmds());
    }

    @Test
    void aLoadThatFailedIsTriedAgainOnTheNextCheck() {
        Model read = finished(TestModels.stepped(idle(), checked("b1", "s1", "w1")));

        Model failed = Update.update(read, new LoadMsg.Failed(BRANCHES, refused("boom")))
                .model();

        assertEquals(
                List.of(BRANCHES),
                Update.update(failed, checked("b1", "s1", "w1")).cmds());
    }

    @Test
    void aCheckThatFailedReadsWhatItGuards() {
        Model read = finished(TestModels.stepped(idle(), checked("b1", "s1", "w1")));

        Next next = Update.update(read, new LoadMsg.Failed(new CheckMetadata(), refused("boom")));

        assertEquals(CheckMetadata.GUARDED, next.cmds());
    }

    @Test
    void theStampsAreNotDrawn() {
        Model model = idle();
        List<Tuple2<Cmd.Load, Option<MetadataStamp>>> none = List.empty();

        Model read = Update.update(model, checked("b1", "s1", "w1")).model();

        assertNotEquals(model, read);
        assertEquals(model.drawn(), read.drawn());
        assertEquals(
                List.of(),
                Update.update(read, new LoadMsg.MetadataChecked(none)).cmds());
    }
}
