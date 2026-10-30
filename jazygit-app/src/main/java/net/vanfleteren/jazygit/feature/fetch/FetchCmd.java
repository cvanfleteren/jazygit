package net.vanfleteren.jazygit.feature.fetch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
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
        public Option<Label> label() {
            return Option.of(Label.of("work.remotes"));
        }

        @Override
        public Msg run(Env env) {
            return env.git().remotes().fold(this::failedWith, FetchMsg.RemotesListed::new);
        }

        @Override
        public Msg failed(Failure failure) {
            return new FetchMsg.ListingFailed(failure);
        }
    }

    /**
     * Fetches from {@code remote}, and then from {@code rest}, one after the other.
     */
    record Fetch(RemoteName remote, List<RemoteName> rest) implements FetchCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.fetch", remote.value()));
        }

        @Override
        public Option<Guard> guard() {
            return Option.of(Guard.REMOTE_REFS);
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .fetch(remote)
                    .fold(this::failedWith, changed -> new FetchMsg.Fetched(remote, rest, changed));
        }

        @Override
        public Msg failed(Failure failure) {
            return new FetchMsg.FetchFailed(remote, rest, failure);
        }
    }
}
