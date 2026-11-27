package net.vanfleteren.jazygit.core.gh.model;

import net.vanfleteren.jazygit.core.repo.BranchName;

/**
 * The builds on some commits of the default branch of the repository on GitHub, read in one call, with its name
 * (ADR 212).
 *
 * @param branch the default branch
 * @param page   the builds on its commits read
 */
public record DefaultBranchPage(BranchName branch, BuildsPage page) {}
