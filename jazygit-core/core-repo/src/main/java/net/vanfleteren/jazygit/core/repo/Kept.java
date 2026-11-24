package net.vanfleteren.jazygit.core.repo;

import io.vavr.collection.HashSet;
import io.vavr.collection.Seq;
import io.vavr.collection.Set;

/**
 * What keeps the commits of some reflog entries that no branch, tag or remote reaches (ADR 186, 188).
 *
 * @param lost   those nothing but the reflog keeps, until its entries expire
 * @param byHead those HEAD reaches, which are lost once it moves away
 */
public record Kept(Set<Sha> lost, Set<Sha> byHead) {

    /**
     * Every commit looked at is on a branch, a tag or a remote.
     */
    public static final Kept BY_REFS = new Kept(HashSet.empty(), HashSet.empty());

    /**
     * This, with what {@code newer} found of the commits {@code looked} in place of what this knew of them.
     */
    public Kept with(Kept newer, Seq<Sha> looked) {
        return new Kept(
                lost.removeAll(looked).addAll(newer.lost()),
                byHead.removeAll(looked).addAll(newer.byHead()));
    }
}
