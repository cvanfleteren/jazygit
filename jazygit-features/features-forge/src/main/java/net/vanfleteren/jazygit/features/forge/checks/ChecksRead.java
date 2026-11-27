package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * The checks read of the tip of a branch on a forge (ADR 209).
 *
 * @param repository the repository they are of, as {@code owner/name}
 * @param commit     the commit they are of, when the forge read them by it
 * @param checks     the checks, in the order the forge gave them
 */
public record ChecksRead(RepoSlug repository, Option<Sha> commit, List<ForgeCheck> checks) {}
