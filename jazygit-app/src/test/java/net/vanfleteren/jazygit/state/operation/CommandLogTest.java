package net.vanfleteren.jazygit.state.operation;

import static net.vanfleteren.jazygit.git.StubGit.executed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

class CommandLogTest {

    @Test
    void anEntryIsDoneAtTheTimeTheModelIsTold() {
        Instant at = Instant.parse("2026-11-14T14:32:05Z");
        Model model = TestModels.loaded().withNow(at);

        Model logged = CommandLog.append(model, PushCmd.PUSH, executed("git push origin main"));

        assertEquals(
                List.of(new LogEntry(PushCmd.PUSH, executed("git push origin main"), at)),
                logged.outcome().commandLog());
    }

    @Test
    void noCommandsLogNothing() {
        Model model = TestModels.loaded();

        assertEquals(model, CommandLog.append(model, PushCmd.PUSH, List.empty()));
    }
}
