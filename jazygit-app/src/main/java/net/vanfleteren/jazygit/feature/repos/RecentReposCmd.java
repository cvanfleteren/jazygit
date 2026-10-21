package net.vanfleteren.jazygit.feature.repos;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.HeadFile;
import net.vanfleteren.jazygit.git.RepositoryIdentity;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Landing;
import net.vanfleteren.jazygit.state.Msg;

/**
 * The IO of switching to a recent repository. That the recent repositories could not be kept is not worth
 * telling: they are only a convenience.
 */
public sealed interface RecentReposCmd extends Cmd {

    /**
     * Reads the recent repositories but the one shown, with what their HEAD points to. The ones whose directory
     * is gone are not kept anymore.
     */
    record ListRecent() implements RecentReposCmd {

        @Override
        public Msg run(Env env) {
            List<Path> recent = env.recent().load();
            List<Path> gone = recent.filter(directory -> !Files.isDirectory(directory));
            if (gone.nonEmpty()) {
                env.recent().forget(gone);
            }
            return new RecentReposMsg.Listed(recent.removeAll(gone)
                    .remove(env.git().workTree())
                    .map(directory -> new RecentRepo(
                            directory.toString(),
                            RepositoryIdentity.read(directory)
                                    .map(RepositoryIdentity::label)
                                    // The root directory has no name.
                                    .getOrElse(() -> Option.of(directory.getFileName())
                                            .map(Path::toString)
                                            .getOrElse(directory::toString)),
                            HeadFile.read(directory))));
        }

        @Override
        public Msg failed(Failure failure) {
            return new RecentReposMsg.Listed(List.empty());
        }
    }

    /**
     * Opens the repository at {@code directory} instead of the one shown, shown where {@code landing} says, which
     * makes it the last one opened. One that cannot be opened is not kept anymore.
     */
    record SwitchTo(String directory, Landing landing) implements RecentReposCmd {

        /**
         * Where another worktree of the repository is shown: on the worktrees, focused, as switching to one is
         * done from there.
         */
        public static final Landing WORKTREES = new Landing(BranchesTab.WORKTREES, Option.of(Scopes.BRANCHES.id()));

        /**
         * Opens the worktree at {@code location}, shown on the worktrees.
         */
        public static SwitchTo worktree(String location) {
            return new SwitchTo(location, WORKTREES);
        }

        @Override
        public Msg run(Env env) {
            Path chosen = Path.of(directory);
            return env.repositories()
                    .switchTo(chosen, landing)
                    .fold(
                            error -> {
                                env.recent().forget(List.of(chosen));
                                return failedWith(error);
                            },
                            workTree -> {
                                env.recent().opened(workTree);
                                return new RecentReposMsg.Switched(workTree.toString());
                            });
        }

        @Override
        public Msg failed(Failure failure) {
            return new RecentReposMsg.NotSwitched(directory, failure);
        }
    }
}
