package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.stream.Collectors;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.diff.*;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The text the content panel shows for a commit and a stash: a header, the message, a divider and the changes.
 */
class CommitContentTest {

    private static final DiffLook PLAIN_LOOK = new DiffLook(DiffStyle.PLAIN, true, true, true);

    @Test
    void commitTextShowsTheHeaderTheMessageADividerAndTheChanges() {
        Commit commit = Fixtures.commit(
                new Sha("a1b2c3d4e5f6"),
                "Ada Lovelace",
                "ada@example.com",
                Instant.parse("2026-10-09T12:30:00Z"),
                "Subject",
                "More\ntext");

        List<String> lines = ContentText.commitText(
                        commit,
                        Shows.shown(DiffText.of(" a.txt | 1 +\n 1 file changed\n\n+added\n")),
                        PLAIN_LOOK,
                        Theme.SpanStyles.NONE)
                .lines()
                .stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .collect(List.collector());

        assertEquals("commit a1b2c3d4e5f6", lines.get(0));
        assertEquals("Author: Ada Lovelace <ada@example.com>", lines.get(1));
        assertTrue(lines.get(2).startsWith("Date: "), lines.get(2));
        assertEquals(
                List.of(
                        "",
                        "    Subject",
                        "",
                        "    More",
                        "    text",
                        "---",
                        " a.txt | 1 +",
                        " 1 file changed",
                        "",
                        "+added"),
                lines.drop(3));
    }

    @Test
    void stashTextShowsTheStashItsBranchWhenItWasStashedADividerAndTheChanges() {
        Stash stash =
                new Stash("stash@{0}", new Sha("5555"), "my work", Option.of(new BranchName("main")), Instant.EPOCH);

        List<String> lines = ContentText.stashText(
                        stash,
                        Shows.shown(DiffText.of(" a.txt | 1 +\n\n+added line\n")),
                        PLAIN_LOOK,
                        Theme.SpanStyles.NONE)
                .lines()
                .stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .collect(List.collector());

        assertEquals("stash@{0}: my work", lines.get(0));
        assertEquals("Branch: main", lines.get(1));
        assertTrue(lines.get(2).startsWith("Date: "), lines.get(2));
        assertEquals(List.of("---", " a.txt | 1 +", "", "+added line"), lines.drop(3));
    }

    @Test
    void commitTextHasNoBlankLineWithoutAnExtendedMessage() {
        Commit commit =
                Fixtures.commit(new Sha("a1b2c3d4e5f6"), "Ada", "ada@example.com", Instant.EPOCH, "Subject", "");

        List<String> lines =
                ContentText.commitText(commit, Shows.shown(DiffText.of("x")), PLAIN_LOOK, Theme.SpanStyles.NONE)
                        .lines()
                        .stream()
                        .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                        .collect(List.collector());

        assertEquals(List.of("", "    Subject", "---", "x"), lines.drop(3));
    }

    @Test
    void aTruncatedDiffEndsWithALineSayingSo() {
        assertEquals("+a\n", Shows.shown(DiffText.of("+a\n")).text());
        assertEquals("+a\n… truncated\n", Shows.shown(DiffText.of("+a\n", true)).text());
    }
}
