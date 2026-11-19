package net.vanfleteren.jazygit.core.askpass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Only an OpenSSH that has {@code SSH_ASKPASS_REQUIRE}, 8.4 or later, asks through jazygit (ADR 150).
 */
class OpenSshTest {

    @Test
    void openSshFromEightFourOnAsks() {
        assertTrue(OpenSsh.asks("OpenSSH_9.6p1 Ubuntu-3ubuntu13.5, OpenSSL 3.0.13 30 Jan 2024"));
        assertTrue(OpenSsh.asks("OpenSSH_8.4p1, OpenSSL 1.1.1k"));
        assertTrue(OpenSsh.asks("OpenSSH_10.0p2, OpenSSL 3.5.0"));
        assertTrue(OpenSsh.asks("OpenSSH_for_Windows_9.5p1, LibreSSL 3.8.2"));
    }

    @Test
    void anOlderOrAnotherSshDoesNot() {
        assertFalse(OpenSsh.asks("OpenSSH_8.2p1 Ubuntu-4ubuntu0.11, OpenSSL 1.1.1f"));
        assertFalse(OpenSsh.asks("OpenSSH_7.4p1, OpenSSL 1.0.2k-fips"));
        assertFalse(OpenSsh.asks("plink: Release 0.81"));
        assertFalse(OpenSsh.asks(""));
    }

    @Test
    void theCommandIsSplitAsAShellSplitsIt() {
        assertEquals(List.of("ssh"), OpenSsh.words("ssh"));
        assertEquals(List.of("ssh", "-i", "~/.ssh/work"), OpenSsh.words("  ssh -i  ~/.ssh/work "));
        assertEquals(
                List.of("C:\\Program Files\\OpenSSH\\ssh.exe", "-F", "my config"),
                OpenSsh.words("\"C:\\\\Program Files\\\\OpenSSH\\\\ssh.exe\" -F 'my config'"));
        assertEquals(List.of("ssh", "-o", "a b", ""), OpenSsh.words("ssh -o a\\ b ''"));
    }

    /**
     * The ssh command is asked its version, with the words it was configured with.
     */
    @Test
    void theSshCommandIsAskedItsVersion(@TempDir Path dir) throws Exception {
        Path ssh = dir.resolve("ssh.sh");
        Files.writeString(
                ssh, "#!/bin/sh\n[ \"$1 $2 $3\" = \"-F my-config -V\" ] && echo 'OpenSSH_9.6p1, OpenSSL 3.0' >&2\n");
        Files.setPosixFilePermissions(ssh, PosixFilePermissions.fromString("rwx------"));

        assertTrue(OpenSsh.asks(dir, OpenSsh.words(ssh + " -F my-config")));
        assertFalse(OpenSsh.asks(dir, List.of(ssh.toString())));
        assertFalse(OpenSsh.asks(dir, List.of(dir.resolve("missing").toString())));
    }
}
