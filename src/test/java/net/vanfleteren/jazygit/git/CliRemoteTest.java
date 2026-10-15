package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Talking to the remotes.
 */
class CliRemoteTest {

    @Test
    void remotesWithSkipFetchAllAreLeftOut() {
        assertEquals(List.of("Mirror", "old"),
                CliRemote.skipped("remote.Mirror.skipfetchall true\nremote.origin.skipfetchall false\nremote.old.skipfetchall true\n"));
    }

    @Test
    void theUrlOfEachRemoteIsReadByItsName() {
        assertEquals(HashMap.of("origin", "git@github.com:owner/repo.git", "work.mirror", "https://example.com/repo"),
                CliRemote.urls("remote.origin.url git@github.com:owner/repo.git\n"
                        + "remote.work.mirror.url https://example.com/repo\n"
                        + "remote.origin.url https://github.com/owner/other\n"));
    }

    @Test
    void sshRunsInBatchModeKeepingTheConfiguredCommand() {
        assertEquals("ssh -o BatchMode=yes", CliRemote.batchMode(Option.none()));
        assertEquals("ssh -i ~/.ssh/work -o BatchMode=yes", CliRemote.batchMode(Option.of("ssh -i ~/.ssh/work")));
    }
}
