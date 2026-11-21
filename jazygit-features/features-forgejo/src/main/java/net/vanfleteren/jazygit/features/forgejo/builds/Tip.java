package net.vanfleteren.jazygit.features.forgejo.builds;

import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * The tip of a remote-tracking branch, whose builds Forgejo may have.
 *
 * @param remote the remote the branch is on, whose repository has the builds
 * @param sha    the commit the branch points to
 */
public record Tip(RemoteName remote, Sha sha) {}
