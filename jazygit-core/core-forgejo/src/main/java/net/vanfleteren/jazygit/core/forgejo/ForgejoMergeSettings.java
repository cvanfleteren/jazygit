package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.Set;

/**
 * How a repository on Forgejo lets its pull requests be merged (ADR 180), as {@code GET /repos/{owner}/{repo}} gives
 * it to anyone who may read it.
 *
 * @param allowed      the styles it allows
 * @param deleteBranch whether the head branch is deleted once merged, by default
 */
public record ForgejoMergeSettings(Set<ForgejoMergeStyle> allowed, boolean deleteBranch) {

    /**
     * Whether it allows merging as {@code style}.
     */
    public boolean allows(ForgejoMergeStyle style) {
        return allowed.contains(style);
    }
}
