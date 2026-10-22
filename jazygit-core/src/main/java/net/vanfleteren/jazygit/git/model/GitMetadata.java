package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * Stamps of the files of the git directory that the branches, the stashes and the worktrees are read from: while
 * one stays the same, what it stands for does not have to be read again. Each is none when one of its files was
 * modified too recently to tell a change right after it apart, and what it stands for is then to be read anyway.
 *
 * @param branches  the local and the remote-tracking branches, with their upstreams and which are checked out
 * @param stashes   the stashes
 * @param worktrees the worktrees, with what each has checked out
 */
public record GitMetadata(
        Option<MetadataStamp> branches, Option<MetadataStamp> stashes, Option<MetadataStamp> worktrees) {}
