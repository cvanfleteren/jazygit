package net.vanfleteren.jazygit.kernel.state.forge;

import net.vanfleteren.jazygit.core.repo.RemoteName;

/**
 * A remote the home of a repository can be (ADR 177): one a forge claimed.
 *
 * @param feature the id of the feature of its forge
 * @param forge   the forge's name, as the user is shown it
 * @param remote  the remote
 * @param url     where it is
 */
public record ForgeCandidate(String feature, String forge, RemoteName remote, String url) {}
