package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * The name of a branch, e.g. {@code main} or {@code feature/login}: of a local one, or of one on a remote.
 */
public record BranchName(String value) implements Comparable<BranchName> {

    /**
     * Ordered by name, as git lists branches.
     */
    @Override
    public int compareTo(BranchName other) {
        return value.compareTo(other.value);
    }

    /**
     * The name {@code text} spells, ignoring surrounding whitespace; none when it is blank or has whitespace
     * inside, or that starts with {@code -}, which git would read as an option. No branch name can.
     */
    public static Option<BranchName> parse(String text) {
        String stripped = text.strip();
        return stripped.isEmpty()
                        || stripped.startsWith("-")
                        || stripped.chars().anyMatch(Character::isWhitespace)
                ? Option.none()
                : Option.of(new BranchName(stripped));
    }
}
