package net.vanfleteren.jazygit.git.model;

/**
 * How far a branch is from its base, the branch it was made from (ADR 46).
 *
 * @param base   the branch compared with
 * @param tip    the commit of the branch they were counted from
 * @param ahead  the commits of the branch that the base does not have
 * @param behind the commits of the base that the branch does not have
 */
public record BaseDistance(BranchRef base, Sha tip, int ahead, int behind) {}
