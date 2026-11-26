package net.vanfleteren.jazygit.core.repo;

/**
 * A repository on a forge, as the forge names it, {@code owner/name} on GitHub: an opaque value that the forge side
 * writes, which the rest only compares, keeps and shows.
 */
public record RepoSlug(String value) {}
