package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.AllBranches;

/**
 * Reads the local and the remote-tracking branches, at once, and with them the parents of the branches and how
 * far each local branch is from its base (ADR 46): those go by the branches and the parents just read, so they
 * come back with them, in one message (ADR 58). When the parents cannot be read, the branches come back without
 * them, and the parents and counts read before stay. The parent of a branch is the one GitHub says it is built on,
 * by {@code gitHub}, before the one recorded (ADR 72), so a load is told what GitHub read last.
 */
public record LoadBranches(GitHubBases gitHub) implements Load {

    /**
     * The load as the reads of the repository list it, before they are told what GitHub said
     * ({@link #current}).
     */
    public static final LoadBranches LISTED = new LoadBranches(GitHubBases.NONE);

    /**
     * {@code load}, but a load of the branches as {@code model} would ask for it now: told what GitHub read last.
     */
    static Load current(Model model, Load load) {
        return load instanceof LoadBranches ? new LoadBranches(GitHubBases.of(model.gitHub())) : load;
    }

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.branches"));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .branches()
                .all()
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
                .map(recorded -> gitHub.parents(branches.local(), branches.main(), recorded))
                .map(parents -> new LoadMsg.Bases(
                        parents, env.git().branches().baseDistance(branches.tips(), parents, branches.main())));
    }
}
