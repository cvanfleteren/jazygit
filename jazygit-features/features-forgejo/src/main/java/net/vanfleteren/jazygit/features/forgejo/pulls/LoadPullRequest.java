package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import org.slf4j.LoggerFactory;

/**
 * Reads pull request {@code number} of the repository of {@code remote} on Forgejo to show it, as read {@code read}
 * (ADR 176): the pull request, its reviews and the builds of the commit it merges, in the background. Reviews or
 * builds that cannot be read leave the pull request shown without them, and the log says why.
 */
public record LoadPullRequest(RemoteName remote, long number, long read) implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        Either<Failure, ShownPullRequest> shown = ForgejoRemotes.of(env, forgejo)
                .flatMap(remotes -> remotes.get(remote))
                .<Either<Failure, ForgejoRepository>>map(Either::right)
                .getOrElse(() -> Either.left(new ForgejoFailure.NotOnForgejo(remote)))
                .flatMap(repository -> forgejo.pullRequest(repository, number)
                        .<Failure>mapLeft(ForgejoFailure.Asked::new)
                        .map(pr -> new ShownPullRequest(
                                pr,
                                forgejo.reviews(repository, number)
                                        .peekLeft(error -> logged("reviews", error.why()))
                                        .toOption()
                                        .flatMap(ForgejoReview::standing),
                                forgejo.status(repository, pr.branches().headSha())
                                        .peekLeft(error -> logged("builds", error.why()))
                                        .getOrElse(CommitStatus.NONE))));
        return new PullRequestMsg.Read(read, shown);
    }

    private void logged(String what, String why) {
        LoggerFactory.getLogger(LoadPullRequest.class)
                .debug("The {} of pull request {} on {} could not be read: {}", what, number, remote.value(), why);
    }

    @Override
    public Msg failed(Failure failure) {
        return new PullRequestMsg.Read(read, Either.left(failure));
    }
}
