package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * The object id of a git commit, as git printed it: in full, unless said otherwise.
 */
public record Sha(String value) {

    private static final int SHORT_LENGTH = 7;

    /**
     * The sha {@code text} spells, ignoring surrounding whitespace; none when it is blank or has whitespace
     * inside.
     */
    public static Option<Sha> parse(String text) {
        String stripped = text.strip();
        return stripped.isEmpty() || stripped.chars().anyMatch(Character::isWhitespace)
                ? Option.none()
                : Option.of(new Sha(stripped));
    }

    /**
     * The first 7 characters, or all of them if there are fewer.
     */
    public String shortSha() {
        return value.substring(0, Math.min(SHORT_LENGTH, value.length()));
    }

    @Override
    public String toString() {
        return value;
    }
}
