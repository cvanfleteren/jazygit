package net.vanfleteren.jazygit.kernel.state.forge;

import net.vanfleteren.jazygit.core.repo.RemoteName;

/**
 * What the user chose before as the home of a repository (ADR 177), as kept in its git configuration.
 */
public sealed interface ForgeChoice {

    /**
     * The user chose nothing yet: the kernel decides, or asks.
     */
    record Unchosen() implements ForgeChoice {}

    /**
     * The user chose no forge.
     */
    record NoForge() implements ForgeChoice {}

    /**
     * The user chose {@code remote} as the home.
     */
    record Remote(RemoteName remote) implements ForgeChoice {}
}
