package net.vanfleteren.jazygit.gh.model;

/**
 * A branch as GitHub names it, in the repository: the name it has on the remote, which may not be the name of the
 * local branch that pushes to it (ADR 40).
 */
public record GhBranch(String name) {}
