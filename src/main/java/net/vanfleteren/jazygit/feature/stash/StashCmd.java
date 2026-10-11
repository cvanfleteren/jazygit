package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;

import java.util.Optional;

/**
 * Commands that create stashes.
 */
public sealed interface StashCmd extends Cmd {

    /**
     * Stashes the changes under {@code name}.
     */
    record Stash(String name) implements StashCmd {

        @Override
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.stash");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new StashMsg.Done(git.stash(name));
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
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.stashDrop");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new StashMsg.Dropped(git.drop(ref));
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
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.stashPop");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new StashMsg.Popped(git.pop(ref));
        }

        @Override
        public Msg failed(String message) {
            return new StashMsg.PopFailed(message);
        }
    }
}
