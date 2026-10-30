package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;

/**
 * The branches of a repository, read at the same moment.
 *
 * @param local  the local branches: the checked out one first, then the most recently committed to
 * @param remote the remote-tracking branches, the most recently committed to first
 */
public record AllBranches(List<Branch> local, List<RemoteRef> remote) {}
