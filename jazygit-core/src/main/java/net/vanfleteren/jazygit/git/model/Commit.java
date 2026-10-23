package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import java.time.Instant;

/**
 * A git commit.
 *
 * @param sha     the object id
 * @param message the subject: the first line of the commit message
 * @param body    the extended commit message after the subject, or an empty string if there is none
 * @param parents the object ids of the parents; more than one for a merge, the first being the branch
 *                that was merged into
 * @param tags    the tags pointing at the commit
 */
public record Commit(
        Sha sha,
        String authorName,
        String authorEmail,
        Instant authorTime,
        String message,
        String body,
        List<Sha> parents,
        List<Tag> tags) {

    public String shortSha() {
        return sha.shortSha();
    }

    public boolean isMerge() {
        return parents.size() > 1;
    }
}
