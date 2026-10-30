package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

class RemoteNameTest {

    @Test
    void aRemoteNameIsOneWordThatIsNoOption() {
        assertEquals(Option.of(new RemoteName("origin")), RemoteName.parse(" origin "));
        assertEquals(Option.none(), RemoteName.parse(" "));
        assertEquals(Option.none(), RemoteName.parse("my remote"));
        assertEquals(Option.none(), RemoteName.parse("--mirror"));
    }

    @Test
    void aRemoteBranchIsReadAsARemoteAndABranch() {
        assertEquals(
                Option.of(new RemoteBranch(new RemoteName("upstream"), new BranchName("main"))),
                RemoteBranch.parse("upstream main"));
        assertEquals(Option.none(), RemoteBranch.parse("-f main"));
        assertEquals("upstream/main", RemoteBranch.parse("upstream main").get().shortName());
        assertEquals(
                "refs/remotes/upstream/main",
                RemoteBranch.parse("upstream main").get().tracking().toArg());
    }
}
