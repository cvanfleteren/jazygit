package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * Which text spells a branch on a remote.
 */
class RemoteBranchTest {

    @Test
    void parseRejectsAnOptionLookalike() {
        assertEquals(Option.none(), RemoteBranch.parse("--force main"));
        assertEquals(Option.none(), RemoteBranch.parse("origin -x"));
    }

    @Test
    void parseReadsARemoteAndABranch() {
        assertEquals(Option.of(new RemoteBranch("origin", new BranchName("main"))), RemoteBranch.parse("origin main"));
    }
}
