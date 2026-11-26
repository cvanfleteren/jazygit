package net.vanfleteren.jazygit.core.repo;

import io.vavr.control.Option;

/**
 * A branch as a forge names it, in the repository: the name it has on the remote, which may not be the name of the
 * local branch that pushes to it (ADR 40).
 */
public record ForgeBranch(String name) {

    /**
     * The branch the forge names {@code name}; none when the name is empty, which names no branch.
     */
    public static Option<ForgeBranch> named(String name) {
        return Option.when(!name.isEmpty(), () -> new ForgeBranch(name));
    }
}
