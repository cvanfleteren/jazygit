package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.files.Index;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Commands that resolve a conflict, or bring one back.
 */
public sealed interface ResolveCmd extends GitOperationCmd {

    Operation KEEP = Operation.of("midway.log.title.keep", "midway.error.keep.failed");
    Operation RESTORE = Operation.of("midway.log.title.restore", "midway.error.restore.failed");
    Operation MERGE_TOOL = Operation.of("midway.log.title.mergeTool", "midway.error.mergeTool.failed");
    Operation SOLVE = Operation.of("midway.log.title.solve", "midway.error.solve.failed");

    /**
     * What {@code cmd} running {@code ran} on the file at {@code path} comes to: its failure, or the file read again,
     * whether it is still in conflict and for the markers left in it, for the app to say what is left to do. What
     * cannot be read is taken as nothing left to say.
     */
    private static Msg handedBack(
            ResolveCmd cmd, Env env, RepoPath path, Either<GitFailure, List<ExecutedCommand>> ran) {
        Index index = env.git().index();
        return ran.fold(
                failure -> cmd.failed(Failure.of(failure)),
                commands -> new ResolveMsg.HandedBack(
                        cmd,
                        commands,
                        path,
                        index.unmerged(List.of(path)).map(List::nonEmpty).getOrElse(false),
                        index.markers(List.of(path)).toOption().flatMap(List::headOption)));
    }

    /**
     * Keeps {@code version}'s side of the file at {@code path}, in {@code conflict}.
     */
    record Keep(RepoPath path, Conflict conflict, Conflict.Version version) implements ResolveCmd {
        @Override
        public Operation operation() {
            return KEEP;
        }

        @Override
        public Option<String> subject() {
            return Option.of(path.value());
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.index().keep(path, conflict, version);
        }
    }

    /**
     * Brings back the conflict of the file at {@code path}, resolved since the operation stopped.
     */
    record Restore(RepoPath path) implements ResolveCmd {
        @Override
        public Operation operation() {
            return RESTORE;
        }

        @Override
        public Option<String> subject() {
            return Option.of(path.value());
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.index().restoreConflict(path);
        }
    }

    /**
     * Hands the file at {@code path} to git's merge tool, as {@code tools} says to run it, on the terminal (ADR 201).
     */
    record MergeTool(RepoPath path, ConflictTools tools) implements ResolveCmd {
        @Override
        public Operation operation() {
            return MERGE_TOOL;
        }

        @Override
        public Option<String> subject() {
            return Option.of(path.value());
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.editor().mergeTool(path, tools);
        }

        @Override
        public Msg run(Env env) {
            return handedBack(this, env, path, env.terminal().handOver(() -> perform(env.git())));
        }
    }

    /**
     * Solves what mergiraf can of the conflict of the file at {@code path}: written again with its bases first when
     * {@code rewrite}, which loses what was edited of it (ADR 201).
     */
    record Solve(RepoPath path, boolean rewrite) implements ResolveCmd {
        @Override
        public Operation operation() {
            return SOLVE;
        }

        @Override
        public Option<String> subject() {
            return Option.of(path.value());
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return git.index().solve(path, rewrite);
        }

        @Override
        public Msg run(Env env) {
            return handedBack(this, env, path, perform(env.git()));
        }
    }
}
