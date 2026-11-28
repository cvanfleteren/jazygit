package net.vanfleteren.jazygit.core.repo;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;

/**
 * Which remotes a repository's branches go by, as read with the branches (ADR 215).
 *
 * @param base        the base remote ({@link BaseRemote}); none when the repository has no remote
 * @param pushDefault {@code remote.pushDefault}
 * @param pushRemotes {@code branch.<name>.pushRemote}, by the branch
 */
public record RemoteSetup(
        Option<RemoteName> base, Option<RemoteName> pushDefault, Map<BranchName, RemoteName> pushRemotes) {

    /**
     * No remote at all, as before the branches are read.
     */
    public static final RemoteSetup NONE = new RemoteSetup(Option.none(), Option.none(), HashMap.empty());

    /**
     * A repository whose only configuration is the remote {@code origin}.
     */
    public static final RemoteSetup ORIGIN = of(RemoteBranch.DEFAULT_REMOTE);

    /**
     * A repository whose base remote is {@code base} and that sets nothing else.
     */
    public static RemoteSetup of(RemoteName base) {
        return new RemoteSetup(Option.of(base), Option.none(), HashMap.empty());
    }

    /**
     * The remote {@code branch} is pushed to when it follows nothing: the one set for it, else {@code remote.pushDefault},
     * else the base remote.
     */
    public Option<RemoteName> pushRemote(BranchName branch) {
        return pushRemotes.get(branch).orElse(() -> pushDefault).orElse(() -> base);
    }
}
