package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

/**
 * How far each local branch is from its base: read after every read of the branches, and kept
 * until a read replaces it (ADR 46).
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

        Model read = Update.update(
                        model, new LoadMsg.BaseDistanceLoaded(load, HashMap.empty(), HashMap.of(FEATURE, DISTANCE)))
                .model();

        assertEquals(HashMap.of(FEATURE, DISTANCE), read.repo().baseDistance());
    }

    @Test
    void aReadThatFailsKeepsTheCountsReadBefore() {
        Model model = loaded();
        Cmd.LoadBaseDistance load = LoadUpdate.baseDistance(model);
        Model read = Update.update(
                        model, new LoadMsg.BaseDistanceLoaded(load, HashMap.empty(), HashMap.of(FEATURE, DISTANCE)))
                .model();

        Model failed = Update.update(
                        read, new LoadMsg.Failed(load, new Failure.Git(new GitError.UnexpectedOutput("garbled"))))
                .model();

        assertEquals(HashMap.of(FEATURE, DISTANCE), failed.repo().baseDistance());
    }
}
