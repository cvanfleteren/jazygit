package net.vanfleteren.jazygit.app.askpass;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * With {@code java -jar}, ssh runs a launcher that runs the helper on the same {@code java} and class path (ADR 150).
 */
class AskpassLauncherTest {

    @Test
    void theShellLauncherRunsTheHelperAndOnlyItsOwnerCanRunIt(@TempDir Path dir) throws Exception {
        Path launcher = AskpassLauncher.written(dir, Path.of("/opt/my java/bin/java"), "/it's/jazygit.jar", false)
                .get();

        assertEquals(dir.resolve("askpass.sh"), launcher);
        assertEquals(
                "#!/bin/sh\nexec '/opt/my java/bin/java' -cp '/it'\\''s/jazygit.jar'"
                        + " net.vanfleteren.jazygit.app.askpass.AskpassHelper \"$@\"\n",
                Files.readString(launcher));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(launcher)));
    }

    @Test
    void onWindowsItIsABatchFile(@TempDir Path dir) throws Exception {
        Path launcher = AskpassLauncher.written(dir, Path.of("java.exe"), "jazygit.jar", true)
                .get();

        assertEquals(dir.resolve("askpass.cmd"), launcher);
        assertEquals(
                "@\"java.exe\" -cp \"jazygit.jar\" net.vanfleteren.jazygit.app.askpass.AskpassHelper %*\r\n",
                Files.readString(launcher));
    }
}
