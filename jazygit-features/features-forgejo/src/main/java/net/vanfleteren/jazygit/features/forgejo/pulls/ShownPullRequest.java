package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeSettings;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview;

/**
 * What the dialog shows of a pull request on Forgejo (ADR 176), and how it may be merged (ADR 180).
 *
 * @param pullRequest the pull request
 * @param review      where its review stands; none without a review that counts, or when the reviews could not be read
 * @param checks      how the builds of the commit it merges went; none known when they could not be read
 * @param settings    how its repository lets it be merged; none when that could not be read, so that every style is
 *                    offered, and Forgejo refuses one it does not allow
 */
public record ShownPullRequest(
        ForgejoPullRequestDetails pullRequest,
        Option<ForgejoReview.Verdict> review,
        CommitStatus checks,
        Option<ForgejoMergeSettings> settings) {

    /**
     * A pull request shown without knowing how its repository lets it be merged.
     */
    public ShownPullRequest(
            ForgejoPullRequestDetails pullRequest, Option<ForgejoReview.Verdict> review, CommitStatus checks) {
        this(pullRequest, review, checks, Option.none());
    }
}
