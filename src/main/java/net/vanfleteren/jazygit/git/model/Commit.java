package net.vanfleteren.jazygit.git.model;

import java.time.Instant;
import java.util.List;

/**
 * A git commit.
 *
 * @param sha     the full object id
 * @param message the subject: the first line of the commit message
 * @param body    the extended commit message after the subject, or an empty string if there is none
 * @param parents the full object ids of the parents; more than one for a merge, the first being the branch
 *                that was merged into
 */
public record Commit(String sha, String shortSha, String authorName, String authorEmail, Instant authorTime,
                     String message, String body, List<String> parents) {

    public Commit {
        parents = List.copyOf(parents);
    }

    public Commit(String sha, String shortSha, String authorName, String authorEmail, Instant authorTime,
                  String message, String body) {
        this(sha, shortSha, authorName, authorEmail, authorTime, message, body, List.of());
    }

    public boolean isMerge() {
        return parents.size() > 1;
    }
}
