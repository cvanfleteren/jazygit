package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebugLogTest {

    @Test
    void withoutADebugLogNothingIsLogged() {
        assertEquals(HashMap.of("org.slf4j.simpleLogger.defaultLogLevel", "off"), DebugLog.properties(Option.none()));
    }

    @Test
    void theDebugLogTakesEverythingFromDebugOn() {
        var properties = DebugLog.properties(Option.of(Path.of("/state/debug.log")));

        assertEquals(Option.of("debug"), properties.get("org.slf4j.simpleLogger.defaultLogLevel"));
        assertEquals(Option.of("/state/debug.log"), properties.get("org.slf4j.simpleLogger.logFile"));
    }

    @Test
    void everyLineTellsWhenItHappened() {
        var properties = DebugLog.properties(Option.of(Path.of("debug.log")));

        assertEquals(Option.of("true"), properties.get("org.slf4j.simpleLogger.showDateTime"));
        assertEquals(Option.of("yyyy-MM-dd HH:mm:ss.SSS"), properties.get("org.slf4j.simpleLogger.dateTimeFormat"));
    }

    @Test
    void theEnvironmentsZtExecLogsStayOut() {
        assertEquals(
                Option.of("info"),
                DebugLog.properties(Option.of(Path.of("debug.log")))
                        .get("org.slf4j.simpleLogger.log.org.zeroturnaround.exec"));
    }

    @Test
    void aDebugLogThatCantBeWrittenFails(@TempDir Path dir) throws IOException {
        Path notADirectory = Files.createFile(dir.resolve("state"));

        assertTrue(DebugLog.start(Option.of(notADirectory.resolve("debug.log"))).isFailure());
    }
}
