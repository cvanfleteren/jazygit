package net.vanfleteren.jazygit.git.model;

import java.time.Instant;

/**
 * A git commit.
 *
 * @param sha     the full object id
 * @param message the subject: the first line of the commit message
 * @param body    the extended commit message after the subject, or an empty string if there is none
 */
public record Commit(String sha, String shortSha, String authorName, String authorEmail, Instant authorTime,
                     String message, String body) {
}
