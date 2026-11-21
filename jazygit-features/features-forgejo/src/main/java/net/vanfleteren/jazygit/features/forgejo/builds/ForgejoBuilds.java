package net.vanfleteren.jazygit.features.forgejo.builds;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Part;

/**
 * How the builds of the commits on Forgejo went, as far as they were read (ADR 174).
 *
 * @param states  how the builds of each commit read went
 * @param stale   the commits read before the last fetch, to be read again, as a build may have run again
 * @param reading the commits being read
 */
public record ForgejoBuilds(Map<Sha, CommitStatus.State> states, Set<Sha> stale, Set<Sha> reading) {

    /**
     * Nothing read.
     */
    public static final ForgejoBuilds NONE = new ForgejoBuilds(HashMap.empty(), HashSet.empty(), HashSet.empty());

    /**
     * The part of the model it is kept in.
     */
    public static final Part<ForgejoBuilds> PART = Part.of("forgejo.builds", NONE);

    /**
     * How the builds of {@code sha} went, if they were read.
     */
    public Option<CommitStatus.State> state(Sha sha) {
        return states.get(sha);
    }

    /**
     * Of {@code tips}, in their order, those to read: those not being read that were not read, that run or that are
     * stale.
     */
    public List<Tip> due(List<Tip> tips) {
        return tips.filter(tip -> !reading.contains(tip.sha())
                && states.get(tip.sha())
                        .forAll(state -> state == CommitStatus.State.RUNNING || stale.contains(tip.sha())));
    }

    /**
     * These builds, with {@code tips} being read.
     */
    public ForgejoBuilds reading(List<Tip> tips) {
        return new ForgejoBuilds(states, stale, reading.addAll(tips.map(Tip::sha)));
    }

    /**
     * These builds, with what was read of {@code tips}: {@code read}, which leaves out those that could not be read.
     */
    public ForgejoBuilds read(List<Tip> tips, Map<Sha, CommitStatus.State> read) {
        Set<Sha> shas = tips.map(Tip::sha).toSet();
        return new ForgejoBuilds(
                states.merge(read, (before, now) -> now), stale.diff(read.keySet()), reading.diff(shas));
    }

    /**
     * These builds, with every commit read so far to be read again.
     */
    public ForgejoBuilds fetched() {
        return new ForgejoBuilds(states, states.keySet(), reading);
    }
}
