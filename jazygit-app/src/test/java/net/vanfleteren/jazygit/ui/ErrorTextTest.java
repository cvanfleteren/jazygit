package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.vanfleteren.jazygit.feature.branch.BranchRefusal;
import net.vanfleteren.jazygit.feature.worktree.WorktreeRefusal;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.StashNotRestored;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;
import org.junit.jupiter.api.Test;

class ErrorTextTest {

    // The keys a refusal names its message with, as AppError.Refusal.key() returns them.
    private static final Pattern KEY = Pattern.compile("\"(error\\.[\\w.]+)\"");

    @Test
    void aStashThatCouldNotBeRestoredSaysTheCheckoutHappened() {
        var error = new StashNotRestored(new CommandFailed(Option.none(), 1, "conflict"));

        assertTrue(ErrorText.of(error).startsWith("Checked out, but your stashed changes"));
    }

    @Test
    void aCommitThatCameInThroughAMergeNamesTheMergeAndWhereTheBranchWouldBeLeft() {
        var error = new BranchRefusal.MovedThroughMerge(
                new Sha("aaaaaaaaaa"), new BranchName("main"), new Sha("bbbbbbbbbb"), new Sha("cccccccccc"));

        assertEquals(
                "aaaaaaa can't be moved: it came into main through the merge bbbbbbb. It isn't one of main's own"
                        + " commits, so moving it would leave main on ccccccc, a commit of the merged branch. Pick"
                        + " bbbbbbb or a commit above it, or one on main's own line.",
                ErrorText.of(error));
    }

    @Test
    void aLockedWorktreeSaysWhyItWasLockedWhenItWasGivenAReason() {
        assertEquals(
                Messages.get("error.removeWorktree.locked", "wt"),
                ErrorText.of(new WorktreeRefusal.WorktreeLocked("wt", " ")));
        assertEquals(
                Messages.get("error.removeWorktree.lockedBecause", "wt", "on a USB drive"),
                ErrorText.of(new WorktreeRefusal.WorktreeLocked("wt", "on a USB drive")));
    }

    @Test
    void everyRefusalHasAMessage() throws IOException {
        List<String> keys;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            keys = List.ofAll(files.filter(ErrorTextTest::declaresRefusals)).flatMap(ErrorTextTest::keys);
        }

        assertFalse(keys.isEmpty());
        assertEquals(List.empty(), keys.filter(key -> Messages.find(key).isEmpty()));
    }

    private static boolean declaresRefusals(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith("Refusal.java") || name.equals("AppError.java");
    }

    private static List<String> keys(Path file) {
        try {
            return List.ofAll(KEY.matcher(Files.readString(file)).results()).map(match -> match.group(1));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
