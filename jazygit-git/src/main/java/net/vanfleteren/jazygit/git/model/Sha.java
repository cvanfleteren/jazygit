package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;
import java.util.regex.Pattern;

/**
 * The object id of a git commit, as git printed it: in full, unless said otherwise.
 */
public record Sha(String value) {

    private static final int SHORT_LENGTH = 7;
    // A full SHA-1 or SHA-256 object id. An abbreviated one is not taken: a ref of the same name would win over it.
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}");

    /**
     * The sha {@code text} spells, ignoring surrounding whitespace; none unless that is a full object id, of 40 or 64
     * hex digits, so it can be handed to git as it is (ADR 40).
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
