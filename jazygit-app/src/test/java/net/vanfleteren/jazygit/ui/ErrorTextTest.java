package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.StashNotRestored;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.AppError;
import org.junit.jupiter.api.Test;

class ErrorTextTest {

    // A refusal names its message where it is made, as AppError.refused("error.x", ...).
    private static final Pattern REFUSED = Pattern.compile("refused\\(\\s*\"([^\"]+)\"");

    @Test
    void aStashThatCouldNotBeRestoredSaysTheCheckoutHappened() {
        var error = new StashNotRestored(new CommandFailed(Option.none(), 1, "conflict"));

        assertTrue(ErrorText.of(error).startsWith("Checked out, but your stashed changes"));
    }

    @Test
    void aRefusalIsItsMessageWithWhatItNames() {
        var error = AppError.refused("error.moveCommits.throughMerge", "aaaaaaa", "main", "bbbbbbb", "ccccccc");

        assertEquals(
                "aaaaaaa can't be moved: it came into main through the merge bbbbbbb. It isn't one of main's own"
                        + " commits, so moving it would leave main on ccccccc, a commit of the merged branch. Pick"
                        + " bbbbbbb or a commit above it, or one on main's own line.",
                ErrorText.of(error));
    }

    @Test
    void everyRefusalHasAMessage() throws IOException {
        List<String> keys;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            keys = List.ofAll(files.filter(file -> file.toString().endsWith(".java")))
                    .flatMap(ErrorTextTest::refusedKeys);
        }

        assertFalse(keys.isEmpty());
        assertEquals(List.empty(), keys.filter(key -> Messages.find(key).isEmpty()));
    }

    private static List<String> refusedKeys(Path file) {
        try {
            return List.ofAll(REFUSED.matcher(Files.readString(file)).results()).map(match -> match.group(1));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
