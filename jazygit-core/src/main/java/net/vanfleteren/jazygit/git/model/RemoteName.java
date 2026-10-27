package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * The name of a remote, e.g. {@code origin} (ADR 40).
 */
public record RemoteName(String value) {

    /**
     * The name {@code text} spells, ignoring surrounding whitespace; none when it is blank or has whitespace
     * inside, or that starts with {@code -}, which git would read as an option. No remote name can.
     */
    public static Option<RemoteName> parse(String text) {
        String stripped = text.strip();
        return stripped.isEmpty()
                        || stripped.startsWith("-")
                        || stripped.chars().anyMatch(Character::isWhitespace)
                ? Option.none()
                : Option.of(new RemoteName(stripped));
    }
}
