package net.vanfleteren.jazygit.feature.fetch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fetching from the remotes every few seconds.
 */
class FetchUpdateTest {

    @Test
    void aTickFetchesUnlessAFetchIsStillRunning() {
        Next started = Update.update(loaded(), new FetchMsg.Tick());

        assertEquals(List.of(new FetchCmd.FetchAll()), started.cmds());
        assertEquals(true, started.model().fetching());
        assertEquals(List.of(), Update.update(started.model(), new FetchMsg.Tick()).cmds());
    }

    @Test
    void aFetchReloadsTheBranchesWithoutLoggingAnything() {
        Model fetching = Update.update(loaded(), new FetchMsg.Tick()).model();

        Next done = Update.update(fetching, new FetchMsg.Done(List.of("git fetch --all --quiet")));

        assertEquals(false, done.model().fetching());
        assertTrue(done.cmds().contains(new LoadBranches()));
        assertEquals(List.of(), done.model().commandLog());
    }

    @Test
    void aFailedFetchIsNotReportedAndTheNextTickTriesAgain() {
        Model fetching = Update.update(loaded(), new FetchMsg.Tick()).model();

        Model failed = Update.update(fetching, new FetchMsg.Failed("Could not resolve host")).model();

        assertEquals(Option.none(), failed.error());
        assertEquals(List.of(new FetchCmd.FetchAll()), Update.update(failed, new FetchMsg.Tick()).cmds());
    }
}
