package net.vanfleteren.jazygit.features.forgejo.builds;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Part;

/**
 * How the builds of the commits on Forgejo went, as far as they were read (ADR 174).
 *
 * @param states    how the builds of each commit read went
 * @param stale     the commits read before the last fetch, to be read again, as a build may have run again
 * @param reading   the commits being read
 * @param failed    the commits whose read failed since the last fetch, as one of a private repository without a
 *                  token does: they would fail again on every tick
 * @param elsewhere the remotes found not to be on Forgejo, whose tips are not read
 */
public record ForgejoBuilds(
        Map<Sha, CommitStatus.State> states,
        Set<Sha> stale,
        Set<Sha> reading,
        Set<Sha> failed,
        Set<RemoteName> elsewhere) {

    /**
     * Nothing read.
     */
    public static final ForgejoBuilds NONE =
            new ForgejoBuilds(HashMap.empty(), HashSet.empty(), HashSet.empty(), HashSet.empty(), HashSet.empty());

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
     * Of {@code tips}, in their order, those to read: those not being read, nor failed since the last fetch, that
     * were not read, that run or that are stale.
     */
    public List<Tip> due(List<Tip> tips) {
        return tips.filter(tip -> !reading.contains(tip.sha())
                && !failed.contains(tip.sha())
                && states.get(tip.sha())
                        .forAll(state -> state == CommitStatus.State.RUNNING || stale.contains(tip.sha())));
    }

    /**
     * These builds, with {@code tips} being read.
     */
    public ForgejoBuilds reading(List<Tip> tips) {
        return new ForgejoBuilds(states, stale, reading.addAll(tips.map(Tip::sha)), failed, elsewhere);
    }

    /**
     * These builds, with what was read of {@code tips}: {@code read}, which leaves out those that could not be read,
     * and the remotes of them that are not on Forgejo, {@code notOnForgejo}. A tip on Forgejo that is not in
     * {@code read} failed.
     */
    public ForgejoBuilds read(List<Tip> tips, Map<Sha, CommitStatus.State> read, Set<RemoteName> notOnForgejo) {
        Set<Sha> failing = tips.filter(tip -> !notOnForgejo.contains(tip.remote()) && !read.containsKey(tip.sha()))
                .map(Tip::sha)
                .toSet();
        return new ForgejoBuilds(
                states.merge(read, (before, now) -> now),
                stale.diff(read.keySet()),
                reading.diff(tips.map(Tip::sha).toSet()),
                failed.diff(read.keySet()).union(failing),
                elsewhere.union(notOnForgejo));
    }

    /**
     * These builds, with every commit read so far to be read again, and those that failed and the remotes found not
     * to be on Forgejo to be tried again: a host that could not be reached, as one behind a VPN that was not up, may
     * be reached now; one that answered is not asked again (ADR 174).
     */
    public ForgejoBuilds fetched() {
        return new ForgejoBuilds(states, states.keySet(), reading, HashSet.empty(), HashSet.empty());
    }
}
