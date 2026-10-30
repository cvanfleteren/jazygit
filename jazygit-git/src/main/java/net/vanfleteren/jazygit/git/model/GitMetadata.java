package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * Stamps of the files of the git directory that the branches, the stashes, the worktrees and the parents of the
 * branches are read from: while one stays the same, what it stands for does not have to be read again. Each is none
 * when one of its files was modified too recently to tell a change right after it apart, and what it stands for is
 * then to be read anyway.
 *
 * @param branches  the local and the remote-tracking branches, with their upstreams and which are checked out
 * @param stashes   the stashes
 * @param worktrees the worktrees, with the branch or the detached commit each has checked out, but not the commit
 *                  that branch is at
 * @param config    the configuration, which holds the parents of the branches (ADR 49)
 */
public record GitMetadata(
        Option<MetadataStamp> branches,
        Option<MetadataStamp> stashes,
        Option<MetadataStamp> worktrees,
        Option<MetadataStamp> config) {}
