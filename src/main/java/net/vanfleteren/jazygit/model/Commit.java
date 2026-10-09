package net.vanfleteren.jazygit.model;

import java.time.Instant;

/**
 * A git commit.
 *
 * @param message the subject: the first line of the commit message
 * @param body    the extended commit message after the subject, or an empty string if there is none
 */
public record Commit(String shortSha, String authorName, String authorEmail, Instant authorTime,
                     String message, String body) {
}
