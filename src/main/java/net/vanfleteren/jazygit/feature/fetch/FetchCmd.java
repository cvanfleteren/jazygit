package net.vanfleteren.jazygit.feature.fetch;

import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Commands that talk to the remotes.
 */
public sealed interface FetchCmd extends Cmd {

    /**
     * Fetches from all remotes, in the background.
     */
    record FetchAll() implements FetchCmd {

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.fetchAll().fold(this::failedWith, FetchMsg.Done::new);
        }

        @Override
        public Msg failed(String message) {
            return new FetchMsg.Failed(message);
        }
    }
}
