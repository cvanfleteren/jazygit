package net.vanfleteren.jazygit.core.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The branches a branch could be built on, as git tells them (ADR 143).
 *
 * @param candidates  every local branch and branch on {@code origin} that does not have the tip of the branch, one
 *                    per name, in no particular order
 * @param createdFrom the branch git made the branch from, as its reflog says, if it still does: only in the clone it
 *                    was made in, and not once the reflog expired
 */
public record BaseCandidates(List<BaseCandidate> candidates, Option<BranchName> createdFrom) {}
