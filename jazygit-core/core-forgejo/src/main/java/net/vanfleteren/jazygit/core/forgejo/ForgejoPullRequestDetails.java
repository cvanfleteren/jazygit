package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.List;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * A pull request on Forgejo, as it is read to be shown (ADR 176). Forgejo gives no count of its commits.
 *
 * @param number       its number in the repository
 * @param title        its title
 * @param author       the login of who opened it; empty when that is not known
 * @param status       whether it is open, a draft, and merges without conflicts
 * @param branches     what it merges into what
 * @param activity     when it was opened and last changed
 * @param size         how much it changes
 * @param conversation what was said of it, and who is asked to review it
 * @param url          where it is on the web
 */
public record ForgejoPullRequestDetails(
        PrNumber number,
        String title,
        String author,
        Status status,
        Branches branches,
        Activity activity,
        Size size,
        Conversation conversation,
        String url) {

    /**
     * Whether it is open, merged or closed without merging.
     */
    public enum State {
        OPEN,
        CLOSED,
        MERGED
    }

    /**
     * @param state     whether it is open, merged or closed
     * @param draft     whether it is a draft
     * @param mergeable whether it merges without conflicts, as Forgejo last found
     */
    public record Status(State state, boolean draft, boolean mergeable) {}

    /**
     * @param head    the branch it merges
     * @param base    the branch it merges into
     * @param headSha the commit at the tip of what it merges
     */
    public record Branches(String head, String base, Sha headSha) {}

    /**
     * @param createdAt when it was opened
     * @param updatedAt when it last changed
     */
    public record Activity(Instant createdAt, Instant updatedAt) {}

    /**
     * @param additions    the lines it adds
     * @param deletions    the lines it removes
     * @param changedFiles the files it changes
     */
    public record Size(int additions, int deletions, int changedFiles) {}

    /**
     * @param comments  how many comments it has
     * @param labels    the names of its labels
     * @param reviewers the logins of who is asked to review it
     */
    public record Conversation(int comments, List<String> labels, List<String> reviewers) {}
}
