package net.vanfleteren.jazygit.features.changes.fetch;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.vanfleteren.jazygit.app.keys.Features;
import net.vanfleteren.jazygit.kernel.state.Events;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * A forge that merged or opened a pull request says the remote changed, and the app's features fetch for it (ADR 210),
 * as the forge names no fetch.
 */
class RemoteChangedTest {

    @Test
    void theAppFetchesWhenAFeatureChangedARemote() {
        Next next =
                Events.happened(Next.of(loaded().withReactions(Features.REACTIONS)), new KernelEvent.RemoteChanged());

        assertTrue(next.cmds().contains(new FetchCmd.ListRemotes()), next.cmds().toString());
    }
}
