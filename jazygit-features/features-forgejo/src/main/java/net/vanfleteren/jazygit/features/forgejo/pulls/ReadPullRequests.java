package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the open pull requests of the home on Forgejo (ADR 176, 177), in the background: by the branch of a remote
 * they merge, a fork's that is a remote, as one's own, included; one whose branch is in no remote's repository, as
 * someone else's fork, is left out.
 */
public record ReadPullRequests(RemoteName home) implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        return ForgejoRemotes.of(env, forgejo, home)
                .map(remotes -> forgejo.openPullRequests(remotes.home())
                        .fold(
                                error -> new PullRequestMsg.Listed(
                                        Option.none(), Option.of(new ForgejoFailure.Asked(error))),
                                open -> new PullRequestMsg.Listed(
                                        Option.of(HashMap.ofEntries(open.flatMap(
                                                pr -> remotes.head(pr).map(head -> Tuple.of(head, pr))))),
                                        Option.none())))
                .getOrElse(() -> new PullRequestMsg.Listed(Option.none(), Option.none()));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PullRequestMsg.Listed(Option.none(), Option.of(failure));
    }
}
