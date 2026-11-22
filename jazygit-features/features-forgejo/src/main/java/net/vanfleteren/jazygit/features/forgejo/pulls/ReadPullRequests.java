package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the open pull requests of each remote on Forgejo (ADR 176), in the background: those whose branch is in that
 * repository, by the branch they merge; one from a fork is left out, as its branch is not the remote's.
 */
public record ReadPullRequests() implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        return ForgejoRemotes.of(env, forgejo)
                .map(remotes -> read(forgejo, remotes))
                .getOrElse(() -> new PullRequestMsg.Listed(Option.none(), Option.none()));
    }

    private static PullRequestMsg.Listed read(Forgejo forgejo, Map<RemoteName, ForgejoRepository> remotes) {
        List<Either<ForgejoError, Map<RemoteBranch, ForgejoPullRequest>>> read = remotes.toList()
                .map(remote -> forgejo.openPullRequests(remote._2())
                        .map(open -> HashMap.ofEntries(open.filter(pr -> pr.from(remote._2()))
                                .map(pr -> Tuple.of(new RemoteBranch(remote._1(), new BranchName(pr.head())), pr)))));
        List<Map<RemoteBranch, ForgejoPullRequest>> listed = read.flatMap(Either::toOption);
        Option<Failure> failure =
                read.find(Either::isLeft).map(failed -> (Failure) new ForgejoFailure.Asked(failed.getLeft()));
        return new PullRequestMsg.Listed(
                Option.when(
                        !listed.isEmpty() || remotes.isEmpty(),
                        () -> listed.foldLeft(
                                (Map<RemoteBranch, ForgejoPullRequest>)
                                        HashMap.<RemoteBranch, ForgejoPullRequest>empty(),
                                Map::merge)),
                failure);
    }

    @Override
    public Msg failed(Failure failure) {
        return new PullRequestMsg.Listed(Option.none(), Option.of(failure));
    }
}
