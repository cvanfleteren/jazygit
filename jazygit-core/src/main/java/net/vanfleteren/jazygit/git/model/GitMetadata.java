package net.vanfleteren.jazygit.git.model;

/**
 * Stamps of the files of the git directory that the branches, the stashes and the worktrees are read from: while
 * one stays the same, what it stands for does not have to be read again.
 *
 * @param branches  the local and the remote-tracking branches, with their upstreams and which are checked out
 * @param stashes   the stashes
 * @param worktrees the worktrees, with what each has checked out
 */
public record GitMetadata(MetadataStamp branches, MetadataStamp stashes, MetadataStamp worktrees) {}
