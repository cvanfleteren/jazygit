package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;
import java.util.regex.Pattern;

/**
 * The object id of a git commit, as git printed it: in full, unless said otherwise.
 */
public record Sha(String value) {

    private static final int SHORT_LENGTH = 7;
    // From the shortest abbreviation git accepts to a full SHA-256 object id.
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{4,64}");

    /**
     * The sha {@code text} spells, ignoring surrounding whitespace; none unless that is 4 to 64 hex digits, so it
     * can be handed to git as it is (ADR 40).
     */
    public static Option<Sha> parse(String text) {
        String stripped = text.strip();
        return HEX.matcher(stripped).matches() ? Option.of(new Sha(stripped)) : Option.none();
    }

    /**
     * The first 7 characters, or all of them if there are fewer.
     */
    public String shortSha() {
        return value.substring(0, Math.min(SHORT_LENGTH, value.length()));
    }
}
