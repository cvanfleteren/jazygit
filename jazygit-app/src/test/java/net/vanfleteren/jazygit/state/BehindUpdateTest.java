package net.vanfleteren.jazygit.state;

import static net.vanfleteren.jazygit.state.TestModels.*;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

/**
 * How many commits of its base each local branch does not have: read after every read of the branches, and kept
 * until a read replaces it (ADR 46).
 */
class BehindUpdateTest {

    private static final BranchName FEATURE = new BranchName("feature");

    @Test
    void everyReadOfTheBranchesReadsHowFarTheyAreBehind() {
        Model model = loaded();

        Update.Next next = Update.update(
                model, new LoadMsg.BranchesLoaded(new AllBranches(BRANCHES, io.vavr.collection.List.empty())));

        assertTrue(
                next.cmds().contains(LoadUpdate.behind(next.model())),
                next.cmds().toString());
    }

    @Test
    void nothingIsBehindBeforeTheFirstRead() {
        assertEquals(HashMap.empty(), loaded().repo().behind());
    }

    @Test
    void theCountsReadAreKept() {
        Model model = loaded();
        Cmd.LoadBehind load = LoadUpdate.behind(model);

        Model read = Update.update(model, new LoadMsg.BehindLoaded(load, HashMap.of(FEATURE, 58)))
                .model();

        assertEquals(HashMap.of(FEATURE, 58), read.repo().behind());
    }

    @Test
    void aReadThatFailsKeepsTheCountsReadBefore() {
        Model model = loaded();
        Cmd.LoadBehind load = LoadUpdate.behind(model);
        Model read = Update.update(model, new LoadMsg.BehindLoaded(load, HashMap.of(FEATURE, 58)))
                .model();

        Model failed = Update.update(
                        read, new LoadMsg.Failed(load, new Failure.Git(new GitError.UnexpectedOutput("garbled"))))
                .model();

        assertEquals(HashMap.of(FEATURE, 58), failed.repo().behind());
    }
}
