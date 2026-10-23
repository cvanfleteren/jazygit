package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * The name of a branch, e.g. {@code main} or {@code feature/login}: of a local one, or of one on a remote.
 */
public record BranchName(String value) {

    /**
     * The name {@code text} spells, ignoring surrounding whitespace; none when it is blank or has whitespace
     * inside, which no branch name can.
     */
    public static Option<BranchName> parse(String text) {
        String stripped = text.strip();
        return stripped.isEmpty() || stripped.chars().anyMatch(Character::isWhitespace)
                ? Option.none()
                : Option.of(new BranchName(stripped));
    }
}
