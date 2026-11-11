package net.vanfleteren.jazygit.gh.model;

import io.vavr.control.Option;

/**
 * A branch as GitHub names it, in the repository: the name it has on the remote, which may not be the name of the
 * local branch that pushes to it (ADR 40).
 */
public record GhBranch(String name) {

    /**
     * The branch GitHub names {@code name}; none when the name is empty, which names no branch.
     */
    public static Option<GhBranch> named(String name) {
        return Option.when(!name.isEmpty(), () -> new GhBranch(name));
    }
}
