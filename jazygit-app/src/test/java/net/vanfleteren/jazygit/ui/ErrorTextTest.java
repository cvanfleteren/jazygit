package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.StashNotRestored;
import org.junit.jupiter.api.Test;

class ErrorTextTest {

    @Test
    void aStashThatCouldNotBeRestoredSaysTheCheckoutHappened() {
        var error = new StashNotRestored(new CommandFailed(Option.none(), 1, "conflict"));

        assertTrue(ErrorText.of(error).startsWith("Checked out, but your stashed changes"));
    }
}
