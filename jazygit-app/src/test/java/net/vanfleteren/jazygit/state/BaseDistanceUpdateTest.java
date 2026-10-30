package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

/**
 * How far each local branch is from its base: read after every read of the branches, and after a read of the
 * parents that changed them, and kept until a read replaces it (ADR 46, ADR 56).
 */
class BaseDistanceUpdateTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final BaseDistance DISTANCE =
            new BaseDistance(new BranchRef.Local(new BranchName("main")), new Sha("1"), 1, 58);

    @Test
    void everyReadOfTheBranchesReadsHowFarTheyAreFromTheirBase() {
        Model model = loaded();

        Update.Next next = Update.update(
                model, new LoadMsg.BranchesLoaded(new AllBranches(BRANCHES, io.vavr.collection.List.empty())));

        assertTrue(
                next.cmds().contains(LoadUpdate.baseDistance(next.model())),
                next.cmds().toString());
    }

    @Test
    void noDistanceIsKnownBeforeTheFirstRead() {
        assertEquals(HashMap.empty(), loaded().repo().baseDistance());
    }

    @Test
    void theCountsReadAreKept() {
        Model model = loaded();
        Cmd.LoadBaseDistance load = LoadUpdate.baseDistance(model);

        Model read = Update.update(model, new LoadMsg.BaseDistanceLoaded(load, HashMap.of(FEATURE, DISTANCE)))
                .model();

        assertEquals(HashMap.of(FEATURE, DISTANCE), read.repo().baseDistance());
    }

    @Test
    void aReadForBranchesReadSinceIsDropped() {
        Model model = loaded();
        Cmd.LoadBaseDistance older = LoadUpdate.baseDistance(model);
        // A fetch moved feature: the branches are read again, which asks for the distances again.
        Model moved = localBranchesRead(
                        model,
                        io.vavr.collection.List.of(
                                Fixtures.branch(new BranchName("main"), true, new Sha("aaaa")),
                                Fixtures.branch(FEATURE, false, new Sha("eeee"))))
                .model();

        Update.Next late = Update.update(moved, new LoadMsg.BaseDistanceLoaded(older, HashMap.of(FEATURE, DISTANCE)));

        assertNotEquals(older, LoadUpdate.baseDistance(moved));
        assertEquals(moved.repo(), late.model().repo());
        assertEquals(io.vavr.collection.List.empty(), late.cmds());
    }

    @Test
    void parentsThatChangedAreKeptAndCountedAgainst() {
        Model model = loaded();
        HashMap<BranchName, BranchName> parents = HashMap.of(FEATURE, new BranchName("main"));

        Update.Next read = Update.update(model, new LoadMsg.ParentsLoaded(parents));

        assertEquals(parents, read.model().repo().parents());
        assertEquals(parents, LoadUpdate.baseDistance(read.model()).parents());
        assertTrue(
                read.cmds().contains(LoadUpdate.baseDistance(read.model())),
                read.cmds().toString());
    }

    @Test
    void theSameParentsAgainCountNothingAgain() {
        Model model = loaded();

        Update.Next read =
                Update.update(model, new LoadMsg.ParentsLoaded(model.repo().parents()));

        assertEquals(io.vavr.collection.List.empty(), read.cmds());
    }

    @Test
    void aReadForParentsReadSinceIsDropped() {
        Model model = loaded();
        Cmd.LoadBaseDistance older = LoadUpdate.baseDistance(model);
        Model reparented = Update.update(model, new LoadMsg.ParentsLoaded(HashMap.of(FEATURE, new BranchName("main"))))
                .model();

        Update.Next late =
                Update.update(reparented, new LoadMsg.BaseDistanceLoaded(older, HashMap.of(FEATURE, DISTANCE)));

        assertEquals(reparented.repo(), late.model().repo());
    }

    @Test
    void aReadThatFailsKeepsTheCountsReadBefore() {
        Model model = loaded();
        Cmd.LoadBaseDistance load = LoadUpdate.baseDistance(model);
        Model read = Update.update(model, new LoadMsg.BaseDistanceLoaded(load, HashMap.of(FEATURE, DISTANCE)))
                .model();

        Model failed = Update.update(
                        read, new LoadMsg.Failed(load, new Failure.Git(new GitError.UnexpectedOutput("garbled"))))
                .model();

        assertEquals(HashMap.of(FEATURE, DISTANCE), failed.repo().baseDistance());
    }

    @Test
    void parentsThatCannotBeReadStayAsReadBefore() {
        HashMap<BranchName, BranchName> parents = HashMap.of(FEATURE, new BranchName("main"));
        Model read = Update.update(loaded(), new LoadMsg.ParentsLoaded(parents)).model();

        Model failed = Update.update(
                        read,
                        new LoadMsg.Failed(
                                new Cmd.LoadParents(), new Failure.Git(new GitError.UnexpectedOutput("garbled"))))
                .model();

        assertEquals(parents, failed.repo().parents());
    }
}
