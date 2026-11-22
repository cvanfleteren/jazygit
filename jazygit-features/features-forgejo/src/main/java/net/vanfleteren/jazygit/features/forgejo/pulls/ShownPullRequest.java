package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview;

/**
 * What the dialog shows of a pull request on Forgejo (ADR 176).
 *
 * @param pullRequest the pull request
 * @param review      where its review stands; none without a review that counts, or when the reviews could not be read
 * @param checks      how the builds of the commit it merges went; none known when they could not be read
 */
public record ShownPullRequest(
        ForgejoPullRequestDetails pullRequest, Option<ForgejoReview.Verdict> review, CommitStatus checks) {}
