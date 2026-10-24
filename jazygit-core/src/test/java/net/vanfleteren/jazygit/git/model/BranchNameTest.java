package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * Which text spells a branch name.
 */
class BranchNameTest {

    @Test
    void parseRejectsANameThatGitWouldReadAsAnOption() {
        assertEquals(Option.none(), BranchName.parse("-x"));
        assertEquals(Option.none(), BranchName.parse("--force"));
    }

    @Test
    void parseAcceptsAnOrdinaryName() {
        assertEquals(Option.of(new BranchName("feature/login")), BranchName.parse(" feature/login "));
    }
}
