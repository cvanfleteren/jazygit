package net.vanfleteren.jazygit.gh.model;

/**
 * An account gh is logged in to on a host, as {@code gh auth status} lists it.
 *
 * @param host   the GitHub host
 * @param login  the login of the account
 * @param active whether gh uses it for the host, unless told otherwise
 * @param valid  whether its token works; it does not after it was revoked, say
 */
public record GhLogin(String host, String login, boolean active, boolean valid) {}
