package net.vanfleteren.jazygit.core.gh.checks;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.gh.process.GhFailure;
import net.vanfleteren.jazygit.core.gh.process.GhProcess;
import net.vanfleteren.jazygit.core.gh.process.GhToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The log of a job of GitHub Actions, read once and kept in a file (ADR 160).
 */
class JobLogTest {

    private static final String LOG = """
            2026-11-19T10:00:00.0000000Z ##[group]Run ./mvnw verify
            2026-11-19T10:00:01.0000000Z [ERROR] Tests run: 3, Failures: 1
            2026-11-19T10:00:02.0000000Z ##[error]Process completed with exit code 1.
            2026-11-19T10:00:03.0000000Z Cleaning up orphan processes
            """;

    @Test
    void theLogOfTheJobIsReadOverRestOfTheRepositoryItIsOf() {
        assertEquals(
                List.of("gh", "api", "--allow-escape-sequences", "repos/owner/repo/actions/jobs/123/logs"),
                JobLogQuery.command("owner/repo", 123).executed().commandLine());
    }

    @Test
    void itIsKeptByOwnerRepositoryRunAttemptAndJob(@TempDir Path cache) {
        assertEquals(
                Either.right(cache.resolve("logs/owner/repo/7-2-123.log")),
                JobLogQuery.file(cache, "owner/repo", 7, 2, 123));
    }

    @Test
    void whatIsNoOwnerAndNameKeepsNothingOutsideTheCache(@TempDir Path cache) {
        List.of("../repo", "owner/..", "owner", "owner/repo/more", "own er/repo", "")
                .forEach(repository -> assertTrue(
                        JobLogQuery.file(cache, repository, 7, 1, 123).isLeft(), repository));
    }

    @Test
    void itOpensAtTheFirstErrorElseAtTheLastLine() {
        assertEquals(3, JobLogQuery.line(LOG));
        assertEquals(2, JobLogQuery.line("one\ntwo\n"));
        assertEquals(1, JobLogQuery.line(""));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void theLogIsReadOnceAndKept(@TempDir Path dir, @TempDir Path cache) throws Exception {
        Path calls = dir.resolve("calls");
        Path gh = gh(dir, "echo \"$@\" >> '" + calls + "'\ncat <<'EOF'\n" + LOG + "EOF\n");
        CliChecks checks = new CliChecks(new GhProcess(dir, gh.toString(), GhToken.NONE), cache);

        Either<GhFailure, JobLog> read = checks.jobLog("owner/repo", 7, 1, 123);
        Either<GhFailure, JobLog> again = checks.jobLog("owner/repo", 7, 1, 123);

        Path file = cache.resolve("logs/owner/repo/7-1-123.log");
        assertEquals(file, read.get().file());
        assertEquals(3, read.get().line());
        assertEquals(
                List.of("gh api --allow-escape-sequences repos/owner/repo/actions/jobs/123/logs"),
                read.get().commands().map(command -> command.line()));
        assertEquals(LOG, Files.readString(file));
        assertEquals(Either.right(new JobLog(file, 3, List.empty())), again);
        assertEquals(1, Files.readAllLines(calls).size());
        try (var left = Files.list(file.getParent())) {
            assertEquals(1, left.count(), "nothing of it is left halfway");
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aGhOlderThan2104IsAskedWithoutAllowingEscapeSequences(@TempDir Path dir, @TempDir Path cache)
            throws Exception {
        // As gh 2.103 says it, before its usage.
        Path gh = gh(
                dir,
                "case \"$2\" in --allow-escape-sequences) echo 'unknown flag: --allow-escape-sequences' >&2;"
                        + " echo 'Usage:  gh api <endpoint> [flags]' >&2; exit 1;; esac\ncat <<'EOF'\n" + LOG
                        + "EOF\n");
        CliChecks checks = new CliChecks(new GhProcess(dir, gh.toString(), GhToken.NONE), cache);

        Either<GhFailure, JobLog> read = checks.jobLog("owner/repo", 7, 1, 123);

        assertEquals(
                List.of("gh api repos/owner/repo/actions/jobs/123/logs"),
                read.get().commands().map(command -> command.line()));
        assertEquals(LOG, Files.readString(read.get().file()));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aLogGitHubDoesNotGiveIsNotKept(@TempDir Path dir, @TempDir Path cache) throws Exception {
        Path gh = gh(dir, "echo 'gh: Not Found (HTTP 404)' >&2\nexit 1\n");
        CliChecks checks = new CliChecks(new GhProcess(dir, gh.toString(), GhToken.NONE), cache);

        Either<GhFailure, JobLog> read = checks.jobLog("owner/repo", 7, 1, 123);

        assertInstanceOf(GhError.CommandFailed.class, read.getLeft().error());
        assertEquals(1, read.getLeft().commands().size());
        assertFalse(Files.exists(cache.resolve("logs/owner/repo/7-1-123.log")));
    }

    private static Path gh(Path dir, String body) throws Exception {
        Path gh = Files.writeString(dir.resolve("gh"), "#!/bin/sh\n" + body);
        assertTrue(gh.toFile().setExecutable(true));
        return gh;
    }
}
