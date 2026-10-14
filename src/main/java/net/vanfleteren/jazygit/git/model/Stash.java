package net.vanfleteren.jazygit.git.model;

/**
 * A stash entry.
 *
 * @param ref     how git names it, e.g. {@code stash@{0}}; the newest stash is {@code stash@{0}}, so the name of
 *                a stash changes as stashes are added and removed
 * @param sha     the commit git keeps it as, which does not change
 * @param message what it was stashed as, without git's {@code On <branch>:} prefix
 */
public record Stash(String ref, Sha sha, String message) {
}
