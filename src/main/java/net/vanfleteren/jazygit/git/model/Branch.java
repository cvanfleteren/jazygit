package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

import java.time.Instant;

/**
 * A git branch, with a flag indicating whether it is the currently checked-out branch.
 *
 * @param tipOid  the commit the branch points to
 * @param tipTime when that commit was made
 * @param hasRemote whether the branch also exists on its remote, according to the remote-tracking refs
 * @param isDefault whether this is the repository's main branch, i.e. the default branch of the remote
 * @param upstream  the remote-tracking branch it follows, if that exists
 */
public record Branch(String name, boolean current, Sha tipOid, Instant tipTime, boolean hasRemote,
                     boolean isDefault, Option<Upstream> upstream) {

    public Branch(String name, boolean current, Sha tipOid, Instant tipTime, boolean hasRemote,
                  boolean isDefault) {
        this(name, current, tipOid, tipTime, hasRemote, isDefault, Option.none());
    }

    /**
     * The commits not yet pushed to the upstream branch; 0 without an upstream.
     */
    public int ahead() {
        return upstream.map(Upstream::ahead).getOrElse(0);
    }

    /**
     * The commits on the upstream branch not yet in this branch; 0 without an upstream.
     */
    public int behind() {
        return upstream.map(Upstream::behind).getOrElse(0);
    }

    public Branch(String name, boolean current, Sha tipOid, Instant tipTime, boolean hasRemote) {
        this(name, current, tipOid, tipTime, hasRemote, false);
    }

    /**
     * Whether the branch can be deleted on its remote: it must be there, and it must not be the
     * default branch, which would break every clone.
     */
    public boolean remoteDeletable() {
        return hasRemote && !isDefault;
    }

    public Branch(String name, boolean current, Sha tipOid, Instant tipTime) {
        this(name, current, tipOid, tipTime, false, false);
    }

    public Branch(String name, boolean current, Sha tipOid) {
        this(name, current, tipOid, Instant.EPOCH, false, false);
    }
}
