package net.vanfleteren.jazygit.feature.start;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.state.Msg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link StartCmd} against real folders, made repositories with the git CLI.
 */
class StartCmdTest {

    private static final Path HOME = Path.of(System.getProperty("user.home"));

    @TempDir
    Path dir;

    private Path plain;

    @BeforeEach
    void setUp() throws Exception {
        plain = Files.createDirectories(dir.resolve("plain"));
        // The temporary directory could be inside a repository itself.
        assumeTrue(
                new StartCmd.Open(plain.toString(), ".").run() instanceof StartMsg.NoRepository,
                "the temporary directory is inside a repository");
    }

    @Test
    void aPathIsFromTheFolderAskedAboutOrFromHome() {
        assertEquals(Path.of("/home/me/code"), StartCmd.resolve(Path.of("/home/me/notes"), "../code"));
        assertEquals(Path.of("/srv/code"), StartCmd.resolve(Path.of("/home/me/notes"), "/srv/code"));
        assertEquals(HOME.resolve("code"), StartCmd.resolve(Path.of("/home/me/notes"), "~/code"));
        assertEquals(HOME, StartCmd.resolve(Path.of("/home/me/notes"), "~"));
    }

    @Test
    void aFolderInNoRepositoryIsSaidToBe() {
        assertEquals(
                new StartMsg.NoRepository(plain.toString(), true), new StartCmd.Open(dir.toString(), "plain").run());
    }

    @Test
    void aPathToNoFolderIsSaidToBe() {
        assertEquals(
                new StartMsg.NoRepository(dir.resolve("missing").toString(), false),
                new StartCmd.Open(dir.toString(), "missing").run());
    }

    @Test
    void initMakesTheFolderARepositoryAndOpensIt() throws Exception {
        Msg initialised = new StartCmd.Init(plain.toString()).run();

        StartMsg.Opened opened = assertInstanceOf(StartMsg.Opened.class, initialised);
        assertEquals(plain.toRealPath(), Path.of(opened.worktree()).toRealPath());
        assertInstanceOf(StartMsg.Opened.class, new StartCmd.Open(dir.toString(), "plain").run());
    }

    @Test
    void initFailingIsSaid() {
        assertInstanceOf(
                StartMsg.Failed.class, new StartCmd.Init(dir.resolve("missing").toString()).run());
    }
}
