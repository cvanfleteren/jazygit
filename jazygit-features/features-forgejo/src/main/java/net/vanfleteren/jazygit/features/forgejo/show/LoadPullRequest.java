package net.vanfleteren.jazygit.features.forgejo.show;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFailure;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.features.forgejo.pulls.ForgejoRemotes;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestMsg;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import org.slf4j.LoggerFactory;

/**
 * Reads pull request {@code number} of {@code home}, the remote on Forgejo the pull requests are opened against, to
 * show it, as read {@code read} (ADR 176, 177): the pull request, its reviews, the builds of the commit it merges, how
 * its repository lets it be merged (ADR 180), what its base branch requires and whether its merge is scheduled (ADR
 * 181), in the background. What else cannot be read leaves the pull request shown without it, and the log says why.
 */
public record LoadPullRequest(RemoteName home, long number, long read) implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        Either<Failure, ShownPullRequest> shown = ForgejoRemotes.of(env, forgejo, home)
                .map(ForgejoRemotes::home)
                .<Either<Failure, ForgejoRepository>>map(Either::right)
                .getOrElse(() -> Either.left(new ForgejoFailure.NoHome()))
                .flatMap(repository -> forgejo.pullRequest(repository, number)
                        .<Failure>mapLeft(ForgejoFailure.Asked::new)
                        .map(pr -> shown(forgejo, repository, pr)));
        return new PullRequestMsg.Read(read, shown);
    }

    private ShownPullRequest shown(Forgejo forgejo, ForgejoRepository repository, ForgejoPullRequestDetails pr) {
        Option<List<ForgejoReview>> reviews = forgejo.reviews(repository, number)
                .peekLeft(error -> logged("reviews", error.why()))
                .toOption();
        return new ShownPullRequest(
                pr,
                reviews.flatMap(ForgejoReview::standing),
                forgejo.status(repository, pr.branches().headSha())
                        .peekLeft(error -> logged("builds", error.why()))
                        .getOrElse(CommitStatus.NONE),
                forgejo.mergeSettings(repository)
                        .peekLeft(error -> logged("merge settings", error.why()))
                        .toOption(),
                forgejo.branchRules(repository, pr.branches().base())
                        .peekLeft(error -> logged("rules of the base branch", error.why()))
                        .toOption(),
                reviews.map(ForgejoReview::approvals).getOrElse(0),
                forgejo.scheduledMerge(repository, number)
                        .peekLeft(error -> logged("scheduled merge", error.why()))
                        .getOrElse(Option.none()));
    }

    private void logged(String what, String why) {
        LoggerFactory.getLogger(LoadPullRequest.class)
                .debug("The {} of pull request {} could not be read: {}", what, number, why);
    }

    @Override
    public Msg failed(Failure failure) {
        return new PullRequestMsg.Read(read, Either.left(failure));
    }
}
