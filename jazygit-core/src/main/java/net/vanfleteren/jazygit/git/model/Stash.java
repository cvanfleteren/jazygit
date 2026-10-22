package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;
import java.time.Instant;

/**
 * A stash entry.
 *
 * @param ref     how git names it, e.g. {@code stash@{0}}; the newest stash is {@code stash@{0}}, so the name of
 *                a stash changes as stashes are added and removed
 * @param sha     the commit git keeps it as, which does not change
 * @param message what it was stashed as, without git's {@code On <branch>:} prefix
 * @param branch  the branch it was stashed on, as git's message names it; none when the message names none
 * @param time    when it was stashed
 */
public record Stash(String ref, Sha sha, String message, Option<String> branch, Instant time) {}
