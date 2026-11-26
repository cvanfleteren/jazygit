package net.vanfleteren.jazygit.core.repo;

/**
 * The URL a git remote fetches from, as {@code remote.<name>.url} gives it: {@code https://…}, {@code ssh://…}, an
 * scp-like {@code git@host:owner/name.git} or a local path.
 */
public record RemoteUrl(String value) {}
