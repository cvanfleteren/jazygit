package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoBranchRules;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeSettings;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview;
import net.vanfleteren.jazygit.core.forgejo.ForgejoScheduledMerge;

/**
 * What the dialog shows of a pull request on Forgejo (ADR 176), and how it may be merged (ADR 180, 181).
 *
 * @param pullRequest the pull request
 * @param review      where its review stands; none without a review that counts, or when the reviews could not be read
 * @param checks      how the builds of the commit it merges went; none known when they could not be read
 * @param settings    how its repository lets it be merged; none when that could not be read, so that every style is
 *                    offered, and Forgejo refuses one it does not allow
 * @param rules       what its base branch requires before it is merged; none when that could not be read, so that it
 *                    is merged now, and Forgejo refuses it while they are not met
 * @param approvals   how many reviewers approve it; 0 when the reviews could not be read
 * @param scheduled   its merge scheduled for when its checks succeed; none when none is, or that could not be read
 */
public record ShownPullRequest(
        ForgejoPullRequestDetails pullRequest,
        Option<ForgejoReview.Verdict> review,
        CommitStatus checks,
        Option<ForgejoMergeSettings> settings,
        Option<ForgejoBranchRules> rules,
        int approvals,
        Option<ForgejoScheduledMerge> scheduled) {

    /**
     * A pull request shown without knowing how its repository lets it be merged.
     */
    public ShownPullRequest(
            ForgejoPullRequestDetails pullRequest, Option<ForgejoReview.Verdict> review, CommitStatus checks) {
        this(pullRequest, review, checks, Option.none());
    }

    /**
     * A pull request shown knowing how its repository lets it be merged, as {@code settings} says, if it does, but
     * not what its base branch requires, and with no merge scheduled.
     */
    public ShownPullRequest(
            ForgejoPullRequestDetails pullRequest,
            Option<ForgejoReview.Verdict> review,
            CommitStatus checks,
            Option<ForgejoMergeSettings> settings) {
        this(pullRequest, review, checks, settings, Option.none(), 0, Option.none());
    }

    /**
     * Whether its base branch does not let it be merged yet, as far as was read: a check it requires has not passed,
     * or approvals are missing. It is merged when its checks succeed then (ADR 181).
     */
    public boolean waits() {
        return rules.exists(required -> required.waitFor(checks, approvals));
    }
}
