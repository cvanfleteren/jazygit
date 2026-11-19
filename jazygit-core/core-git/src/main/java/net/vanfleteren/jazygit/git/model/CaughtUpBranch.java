package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * A local branch to be at its branch on a remote, as that was last fetched (ADR 99).
 *
 * @param branch   the local branch
 * @param upstream its branch on the remote
 * @param made     whether it is made, as it does not exist yet, following {@code upstream}; else it exists, follows
 *                 {@code upstream} already, and is fast-forwarded
 * @param parent   the branch it is built on, recorded as {@code gh} records the branch one is made from, for one that
 *                 is made
 */
public record CaughtUpBranch(BranchName branch, RemoteBranch upstream, boolean made, Option<BranchName> parent) {}
