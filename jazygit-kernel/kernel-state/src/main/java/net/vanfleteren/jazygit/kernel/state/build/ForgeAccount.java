package net.vanfleteren.jazygit.kernel.state.build;

/**
 * The account a repository is configured to use on its forge, for a program that jazygit starts and that may use the
 * forge's own program, as an agent (ADR 162). It names no forge, and holds no secret: only how to run as it.
 *
 * @param user  the login of the account
 * @param host  the host of the forge
 * @param runAs how to run the forge's program as this account, rather than its active one, as the forge side writes
 *              it, so that what reads this knows no forge's program
 */
public record ForgeAccount(String user, String host, String runAs) {}
