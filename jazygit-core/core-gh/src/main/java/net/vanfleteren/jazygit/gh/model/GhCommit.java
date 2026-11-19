package net.vanfleteren.jazygit.gh.model;

import io.vavr.control.Option;

/**
 * The object id of a commit, as GitHub reported it (ADR 40).
 */
public record GhCommit(String oid) {

    /**
     * The commit {@code text} names, ignoring surrounding whitespace; none when it is blank, as GitHub leaves it for a
     * pull request whose branch is gone.
     */
    public static Option<GhCommit> parse(String text) {
        String stripped = text.strip();
        return stripped.isEmpty() ? Option.none() : Option.of(new GhCommit(stripped));
    }
}
