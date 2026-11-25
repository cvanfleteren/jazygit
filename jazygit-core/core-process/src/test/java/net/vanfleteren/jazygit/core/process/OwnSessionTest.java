package net.vanfleteren.jazygit.core.process;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnSessionTest {

    @TempDir
    Path dir;

    @Test
    void aProgramOnThePathRunsBehindSetsidWhenItIsThere() throws IOException {
        Path bin = program(program(Files.createDirectory(dir.resolve("bin")), "setsid"), "code");

        assertEquals(List.of("setsid", "code", "."), OwnSession.of(dir, List.of("code", "."), "/nowhere:" + bin, ":"));
    }

    @Test
    void aProgramRunsAsItIsWithoutSetsid() throws IOException {
        Path bin = program(Files.createDirectory(dir.resolve("bin")), "code");

        assertEquals(List.of("code", "."), OwnSession.of(dir, List.of("code", "."), bin.toString(), ":"));
    }

    @Test
    void aProgramThatIsNotFoundRunsAsItIsSoThatItFailsToStart() throws IOException {
        Path bin = program(Files.createDirectory(dir.resolve("bin")), "setsid");

        assertEquals(List.of("code"), OwnSession.of(dir, List.of("code"), bin.toString(), ":"));
        assertEquals(List.of("./code"), OwnSession.of(dir, List.of("./code"), bin.toString(), ":"));
    }

    @Test
    void aFileThatCannotRunIsNotFound() throws IOException {
        Path bin = program(Files.createDirectory(dir.resolve("bin")), "setsid");
        Files.writeString(bin.resolve("code"), "");

        assertEquals(List.of("code"), OwnSession.of(dir, List.of("code"), bin.toString(), ":"));
    }

    @Test
    void aProgramNamedByAPathIsLookedForInTheDirectoryItRunsIn() throws IOException {
        Path bin = program(Files.createDirectory(dir.resolve("bin")), "setsid");
        program(Files.createDirectory(dir.resolve("tools")), "code");

        assertEquals(List.of("setsid", "tools/code"), OwnSession.of(dir, List.of("tools/code"), bin.toString(), ":"));
    }

    private static Path program(Path directory, String name) throws IOException {
        Files.createFile(
                directory.resolve(name),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        return directory;
    }
}
