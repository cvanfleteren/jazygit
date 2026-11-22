package net.vanfleteren.jazygit.core.repo;

import io.vavr.collection.List;
import java.time.Instant;

/**
 * A git commit.
 *
 * @param sha     the object id
 * @param message the commit message: its summary, the first line, and its description, which may be empty
 * @param parents the object ids of the parents; more than one for a merge, the first being the branch
 *                that was merged into
 * @param tags    the tags pointing at the commit
 */
public record Commit(
        Sha sha,
        String authorName,
        String authorEmail,
        Instant authorTime,
        CommitMessage message,
        List<Sha> parents,
        List<Tag> tags) {

    public String shortSha() {
        return sha.shortSha();
    }

    public boolean isMerge() {
        return parents.size() > 1;
    }
}
