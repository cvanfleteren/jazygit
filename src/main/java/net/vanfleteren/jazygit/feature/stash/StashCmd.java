package net.vanfleteren.jazygit.feature.stash;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Commands that create stashes.
 */
public sealed interface StashCmd extends Cmd {

    /**
     * Stashes the changes under {@code name}.
     */
    record Stash(String name) implements StashCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.stash");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.stash(name).fold(this::failedWith, StashMsg.Done::new);
        }

        @Override
        public Msg failed(String message) {
            return new StashMsg.Failed(message);
        }
    }

    /**
     * Deletes the stash {@code ref}.
     */
    record Drop(String ref) implements StashCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.stashDrop");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.drop(ref).fold(this::failedWith, StashMsg.Dropped::new);
        }

        @Override
        public Msg failed(String message) {
            return new StashMsg.DropFailed(message);
        }
    }

    /**
     * Applies the stash {@code ref} and drops it.
     */
    record Pop(String ref) implements StashCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.stashPop");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.pop(ref).fold(this::failedWith, StashMsg.Popped::new);
        }

        @Override
        public Msg failed(String message) {
            return new StashMsg.PopFailed(message);
        }
    }
}
