package net.vanfleteren.jazygit.git.model;

import java.time.Instant;
import java.util.List;

/**
 * A git commit.
 *
 * @param sha     the object id
 * @param message the subject: the first line of the commit message
 * @param body    the extended commit message after the subject, or an empty string if there is none
 * @param parents the object ids of the parents; more than one for a merge, the first being the branch
 *                that was merged into
 */
public record Commit(Sha sha, String authorName, String authorEmail, Instant authorTime,
                     String message, String body, List<Sha> parents) {

    public Commit {
        parents = List.copyOf(parents);
    }

    public Commit(Sha sha, String authorName, String authorEmail, Instant authorTime, String message, String body) {
        this(sha, authorName, authorEmail, authorTime, message, body, List.of());
    }

    public String shortSha() {
        return sha.shortSha();
    }

    public boolean isMerge() {
        return parents.size() > 1;
    }
}
