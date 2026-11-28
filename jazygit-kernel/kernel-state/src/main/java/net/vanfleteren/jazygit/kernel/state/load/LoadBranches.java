package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.AllBranches;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.BranchBases;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeHome;

/**
 * Reads the local and the remote-tracking branches, at once, and with them the parents of the branches and how
 * far each local branch is from its base (ADR 46): those go by the branches and the parents just read, so they
 * come back with them, in one message (ADR 58). When the parents cannot be read, the branches come back without
 * them, and the parents and counts read before stay. The parent of a branch is the one the features state it is
 * built on, by {@code stated}, as GitHub does, before the one recorded (ADR 72, 169), so a load is told what they read
 * last. The base remote is the one the forge of the repository opens pull requests against, when a forge is on
 * ({@code preferred}, ADR 215), so a load is told that too.
 */
public record LoadBranches(BranchBases stated, Option<RemoteName> preferred) implements Load {

    /**
     * The load as the reads of the repository list it, before they are told what the features state
     * ({@link #current}).
     */
    public static final LoadBranches LISTED = new LoadBranches(BranchBases.NONE, Option.none());

    /**
     * {@code load}, but a load of the branches as {@code model} would ask for it now: told what the features read
     * last.
     */
    public static Load current(Model model, Load load) {
        return load instanceof LoadBranches ? new LoadBranches(BranchBases.in(model), ForgeHome.of(model)) : load;
    }

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.branches"));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .branches()
                .all(preferred)
                .mapLeft(Failure::of)
                .fold(this::failed, branches -> new LoadMsg.BranchesLoaded(this, branches, bases(env, branches)));
    }

    /**
     * The parents of {@code branches} and how far each is from its base, or none when the parents cannot be read.
     */
    private Option<LoadMsg.Bases> bases(Env env, AllBranches branches) {
        return env.git()
                .branches()
                .parents()
                .toOption()
                .map(recorded -> stated.parents(branches.local(), branches.onBase(), branches.main(), recorded))
                .map(parents -> new LoadMsg.Bases(
                        parents,
                        env.git()
                                .branches()
                                .baseDistance(
                                        branches.tips(),
                                        parents,
                                        branches.main(),
                                        branches.setup().base())));
    }
}
