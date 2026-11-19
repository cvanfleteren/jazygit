package net.vanfleteren.jazygit.git.model;

import java.time.Instant;

/**
 * A remote-tracking branch, as the last fetch left it: e.g. {@code origin/feature}.
 *
 * @param branch  the branch on its remote
 * @param tip     the commit it points to
 * @param tipTime when that commit was made
 */
public record RemoteRef(RemoteBranch branch, Sha tip, Instant tipTime) {}
