package net.vanfleteren.jazygit.git;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliBranchesTest {

    @Test
    void theRemoteBranchIsTheUpstreamsNameOnItsRemote() {
        assertEquals(Option.of("feature/login"), CliBranches.remoteBranch("refs/heads/feature/login\n"));
        assertEquals(Option.none(), CliBranches.remoteBranch("\n"));
    }
}
