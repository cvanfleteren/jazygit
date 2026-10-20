package net.vanfleteren.jazygit.gh.model;

import java.time.Instant;

/**
 * PROTOTYPE (comments dialog, option C): a comment on a pull request, or the summary of a review.
 *
 * @param state the state of the review (APPROVED, CHANGES_REQUESTED, COMMENTED, …); empty for a comment
 * @param url   where it is on GitHub; empty for a review
 */
public record PrototypeComment(String author, Instant createdAt, String state, String body, String url) {}
