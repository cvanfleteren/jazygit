package net.vanfleteren.jazygit.features.branches.worktree.add;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.NewBranchStart;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.CaughtUpBranch;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.core.repo.Revision;
import net.vanfleteren.jazygit.kernel.state.Cmd.Guard;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;

/**
 * What a new worktree checks out: a branch, a new branch or a revision, as git adds them, or what another feature adds
 * the worktree for, as a pull request on GitHub (ADR 169), which says itself how it is added.
 */
public interface WorktreeSource {

    /**
     * How it is named to the user, in the titles of the popups.
     */
    String label();

    /**
     * What the directory of the new worktree is named after, next to the main worktree.
     */
    BranchName directory();

    /**
     * The branch that is busy while the worktree is added, if any.
     */
    default Option<BranchRef> busyBranch() {
        return Option.none();
    }

    /**
     * What adding it shares with other commands, as reading the remote-tracking refs; none by default.
     */
    default Option<Guard> guard() {
        return Option.none();
    }

    /**
     * The keys of the git configuration that hold where its worktree starts out, the first that is set taken (ADR
     * 134): the one for every new worktree, by default.
     */
    default List<String> templateKeys() {
        return List.of(ReadWorktreeTemplate.KEY);
    }

    /**
     * The repository on the host of the remotes it is of, as {@code owner/name}, if it knows: a pull request of
     * another repository does. Without it, the template takes the one the features name (ADR 134, 169).
     */
    default Option<RepoSlug> repository() {
        return Option.none();
    }

    /**
     * What the variables of a template stand for that only it has a value for, as {@code {number}} for a pull
     * request (ADR 134); none by default.
     */
    default Map<String, String> values() {
        return LinkedHashMap.empty();
    }

    /**
     * Adds the worktree at {@code path}, with what {@code env} gives: the commands that ran, or why it failed, with
     * those that ran before.
     */
    Either<Failure, List<ExecutedCommand>> add(Env env, Path path);

    /**
     * {@code branch}, which exists and is checked out nowhere, once {@code catchUp} is done to it.
     */
    record Existing(BranchName branch, CatchUp catchUp) implements WorktreeSource {

        /**
         * {@code branch} as it is.
         */
        public Existing(BranchName branch) {
            this(branch, new CatchUp.AsIs());
        }

        @Override
        public String label() {
            return branch.value();
        }

        @Override
        public BranchName directory() {
            return branch;
        }

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(branch));
        }

        @Override
        public Either<Failure, List<ExecutedCommand>> add(Env env, Path path) {
            Git git = env.git();
            return GitFailure.sequence(
                            () -> caughtUp(git), () -> git.worktrees().add(path, branch))
                    .mapLeft(Failure::of);
        }

        /**
         * Fast-forwards the branch to the remote branch it follows when {@code catchUp} says so, without fetching:
         * from the remote-tracking branch, which git refuses when that is no fast-forward (ADR 109).
         */
        private Either<GitFailure, List<ExecutedCommand>> caughtUp(Git git) {
            return catchUp instanceof CatchUp.FastForward(RemoteBranch upstream)
                    ? git.branches().catchUp(List.of(new CaughtUpBranch(branch, upstream, false, Option.none())))
                    : GitFailure.nothing();
        }
    }

    /**
     * The new branch {@code name}, created as {@code from} says.
     */
    record Created(BranchName name, NewBranchStart from) implements WorktreeSource {

        @Override
        public String label() {
            return name.value();
        }

        @Override
        public BranchName directory() {
            return name;
        }

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(new BranchRef.Local(name));
        }

        @Override
        public Either<Failure, List<ExecutedCommand>> add(Env env, Path path) {
            return env.git().worktrees().addWithNewBranch(path, name, from).mapLeft(Failure::of);
        }
    }

    /**
     * {@code start}, a branch or a commit, on no branch.
     *
     * @param label how the user typed it, e.g. a tag, which the directory is named after
     */
    record Detached(Revision start, String label) implements WorktreeSource {

        @Override
        public BranchName directory() {
            return new BranchName(label);
        }

        @Override
        public Either<Failure, List<ExecutedCommand>> add(Env env, Path path) {
            return env.git().worktrees().addDetached(path, start).mapLeft(Failure::of);
        }
    }
}
