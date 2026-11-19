package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.diff.*;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import org.junit.jupiter.api.Test;

/**
 * The log of a branch as the content panel shows it: each commit, its message, and the rails of merged branches.
 */
class BranchLogContentTest {

    private static Commit merge(String sha, String... parents) {
        return ContentPanelFixtures.plain(sha, parents);
    }

    @Test
    void logLinesShowTheExtendedMessageFollowedByABlankLine() {
        Commit commit = Fixtures.commit(
                new Sha("a1b2c3d"),
                "Ada Lovelace",
                "ada@example.com",
                Instant.parse("2026-10-09T12:30:05Z"),
                "Add layout",
                "First detail\nSecond detail");

        assertEquals(
                List.of(
                        "* commit a1b2c3d",
                        "| Ada Lovelace <ada@example.com>",
                        "| 2026-10-09 14:30:05",
                        "| ",
                        "|     Add layout",
                        "| ",
                        "|     First detail",
                        "|     Second detail",
                        "| "),
                LogText.logLines(List.of(commit), ZoneId.of("Europe/Brussels")));
    }

    @Test
    void logLinesDrawMergedBranchesWithRails() {
        List<Commit> log = net.vanfleteren.jazygit.core.git.model.CommitGraph.arrange(List.of(
                merge("m", "b", "f"),
                ContentPanelFixtures.plain("f", "a"),
                ContentPanelFixtures.plain("b", "a"),
                ContentPanelFixtures.plain("a")));

        List<String> lines = LogText.logLines(log, ZoneOffset.UTC);

        assertTrue(lines.contains("*   commit m"));
        assertTrue(lines.contains("|\\  Merge: b f"));
        assertTrue(lines.contains("| * commit f"));
        assertTrue(lines.contains("* | commit b"));
        assertTrue(lines.contains("|/  Ada <a@e.com>"));
        assertTrue(lines.contains("* commit a"));
    }

    @Test
    void logLinesShowTheParentsOfAMergeWhoseOtherParentIsNotListed() {
        List<String> lines = LogText.logLines(
                List.of(merge("f2", "f1", "mm"), ContentPanelFixtures.plain("f1", "base")), ZoneOffset.UTC);

        assertEquals("| Merge: f1 mm", lines.get(lines.indexOf("* commit f2") + 1));
    }

    @Test
    void logLinesWithoutExtendedMessageEndAfterTheSubject() {
        List<Commit> commits = List.of(
                Fixtures.commit(
                        new Sha("a1b2c3d"),
                        "Ada",
                        "ada@example.com",
                        Instant.parse("2026-10-09T12:30:00Z"),
                        "Second",
                        ""),
                Fixtures.commit(
                        new Sha("b2c3d4e"),
                        "Ada",
                        "ada@example.com",
                        Instant.parse("2026-10-08T12:30:00Z"),
                        "First",
                        ""));

        assertEquals(
                List.of(
                        "* commit a1b2c3d",
                        "| Ada <ada@example.com>",
                        "| 2026-10-09 12:30:00",
                        "| ",
                        "|     Second",
                        "| ",
                        "* commit b2c3d4e",
                        "| Ada <ada@example.com>",
                        "| 2026-10-08 12:30:00",
                        "| ",
                        "|     First",
                        "| "),
                LogText.logLines(commits, ZoneOffset.UTC));
    }
}
