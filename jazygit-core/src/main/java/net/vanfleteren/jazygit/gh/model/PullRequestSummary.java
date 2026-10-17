package net.vanfleteren.jazygit.gh.model;

import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;

/**
 * An open pull request as a list of them shows it, in any repository.
 *
 * @param repository the repository it is in, as {@code owner/name}
 * @param number     its number in that repository
 * @param title      its title
 * @param author     the login of who opened it
 * @param headBranch the branch it merges from, in the repository it comes from
 * @param draft      whether it is still a draft
 * @param fromFork   whether it comes from a fork
 * @param checks     how its CI checks are doing
 * @param review     where its review stands
 * @param updatedAt  when it last changed
 * @param url        where it is on GitHub
 */
public record PullRequestSummary(
        String repository,
        int number,
        String title,
        String author,
        String headBranch,
        boolean draft,
        boolean fromFork,
        Checks checks,
        Review review,
        Instant updatedAt,
        String url) {

    /**
     * It as the list of pull requests of its repository has it.
     */
    public PullRequest pullRequest() {
        return new PullRequest(number, headBranch, draft, checks, review);
    }
}
