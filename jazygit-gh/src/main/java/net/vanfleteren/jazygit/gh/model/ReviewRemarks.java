package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The review threads of a pull request, and the commits its changes were diffed between when they were read
 * (ADR 55).
 *
 * @param head    the commit its branch is at; none when its branch is gone
 * @param base    the commit its base branch is at
 * @param threads its review threads, those of a review not submitted yet left out
 */
public record ReviewRemarks(Option<GhCommit> head, Option<GhCommit> base, List<ReviewThread> threads) {}
