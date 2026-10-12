package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.stage.StageMsg;
import org.junit.jupiter.api.Test;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LoggedMsgTest {

    @Test
    void logsTheCommandsAndThenAppliesTheFailure() {
        Update.Next next = Update.update(loaded(), new LoggedMsg("log.title.stage",
                List.of("git add -- a.txt"), new StageMsg.Failed("fatal: pathspec")));

        assertEquals(List.of(new LogEntry("Stage", List.of("git add -- a.txt"))), next.model().commandLog());
        assertEquals(Option.of("Staging failed: fatal: pathspec"), next.model().error());
    }
}
