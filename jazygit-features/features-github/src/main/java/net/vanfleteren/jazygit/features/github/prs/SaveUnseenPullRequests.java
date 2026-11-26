package net.vanfleteren.jazygit.features.github.prs;

import net.vanfleteren.jazygit.features.github.prs.unseen.UnseenPullRequests;
import net.vanfleteren.jazygit.features.github.state.GitHubStores;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import org.slf4j.LoggerFactory;

/**
 * Keeps {@code unseen} as the unseen pull requests of {@code repository}, as {@code owner/name}, for the next
 * session (ADR 29). That they could not be kept is only written to the debug log: they are only a convenience.
 */
public record SaveUnseenPullRequests(String repository, UnseenPullRequests unseen) implements Cmd {

    @Override
    public Msg run(Env env) {
        env.service(GitHubStores.UNSEEN)
                .save(repository, unseen)
                .onFailure(failed -> LoggerFactory.getLogger(SaveUnseenPullRequests.class)
                        .debug("The unseen pull requests could not be kept", failed));
        return new PrOverviewMsg.UnseenKept();
    }

    @Override
    public Msg failed(Failure failure) {
        return new PrOverviewMsg.UnseenKept();
    }
}
