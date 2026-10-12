package net.vanfleteren.jazygit.feature.fetch;

import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Commands that talk to the remotes. They run in the background: the user is not waiting for them.
 */
public sealed interface FetchCmd extends Cmd {

    @Override
    default boolean background() {
        return true;
    }

    /**
     * Lists the remotes to fetch from.
     */
    record ListRemotes() implements FetchCmd {

        @Override
        public Msg run(GitInfoProvider git) {
            return git.remotes().fold(this::failedWith, FetchMsg.RemotesListed::new);
        }

        @Override
        public Msg failed(String message) {
            return new FetchMsg.ListingFailed(message);
        }
    }

    /**
     * Fetches from {@code remote}.
     */
    record Fetch(String remote) implements FetchCmd {

        @Override
        public Msg run(GitInfoProvider git) {
            return git.fetch(remote).fold(this::failedWith, commands -> new FetchMsg.Fetched(remote, commands));
        }

        @Override
        public Msg failed(String message) {
            return new FetchMsg.FetchFailed(remote, message);
        }
    }
}
