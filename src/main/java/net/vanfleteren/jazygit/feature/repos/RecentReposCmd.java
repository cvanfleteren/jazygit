package net.vanfleteren.jazygit.feature.repos;

import io.vavr.collection.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;

/**
 * The IO of switching to a recent repository. That the recent repositories could not be kept is not worth
 * telling: they are only a convenience.
 */
public sealed interface RecentReposCmd extends Cmd {

    /**
     * Reads the recent repositories but the one shown. The ones whose directory is gone are not kept anymore.
     */
    record ListRecent() implements RecentReposCmd {

        @Override
        public Msg run(Env env) {
            List<Path> recent = env.recent().load();
            List<Path> gone = recent.filter(directory -> !Files.isDirectory(directory));
            if (gone.nonEmpty()) {
                env.recent().forget(gone);
            }
            return new RecentReposMsg.Listed(
                    recent.removeAll(gone).remove(env.git().workTree()).map(Path::toString));
        }

        @Override
        public Msg failed(Failure failure) {
            return new RecentReposMsg.Listed(List.empty());
        }
    }

    /**
     * Opens the repository at {@code directory} instead of the one shown, which makes it the last one opened. One
     * that cannot be opened is not kept anymore.
     */
    record SwitchTo(String directory) implements RecentReposCmd {

        @Override
        public Msg run(Env env) {
            Path chosen = Path.of(directory);
            return env.repositories()
                    .switchTo(chosen)
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
