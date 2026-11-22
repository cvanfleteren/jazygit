package net.vanfleteren.jazygit.features.forgejo.builds;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.FjKeys;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerge;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeSettings;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerged;
import net.vanfleteren.jazygit.core.forgejo.ForgejoNewPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Env;

/**
 * Forgejo as a test has it: the hosts that are Forgejo, and the states of the commits on it, by sha.
 */
record FakeForgejo(Set<String> hosts, Map<Sha, CommitStatus.State> states) implements Forgejo {

    @Override
    public Option<ForgejoRepository> repository(String remoteUrl) {
        return ForgejoRepository.of(remoteUrl, FjKeys.NONE.aliases());
    }

    /**
     * A host that cannot be reached: whether it is Forgejo cannot be told.
     */
    static final String DOWN = "down.example.net";

    @Override
    public boolean isForgejo(ForgejoRepository repository) {
        return hosts.contains(repository.host());
    }

    @Override
    public Option<Boolean> tells(ForgejoRepository repository) {
        return repository.host().equals(DOWN) ? Option.none() : Option.of(isForgejo(repository));
    }

    @Override
    public Either<ForgejoError, CommitStatus> status(ForgejoRepository repository, Sha sha) {
        return states.get(sha)
                .<Either<ForgejoError, CommitStatus>>map(state -> Either.right(new CommitStatus(state, List.empty())))
                .getOrElse(() -> Either.left(new ForgejoError.Refused(404, "not found")));
    }

    @Override
    public Either<ForgejoError, List<ForgejoPullRequest>> openPullRequests(ForgejoRepository repository) {
        return Either.right(List.empty());
    }

    @Override
    public Either<ForgejoError, ForgejoPullRequestDetails> pullRequest(ForgejoRepository repository, long number) {
        return Either.left(new ForgejoError.Refused(404, "not found"));
    }

    @Override
    public Either<ForgejoError, List<ForgejoReview>> reviews(ForgejoRepository repository, long number) {
        return Either.right(List.empty());
    }

    @Override
    public Either<ForgejoError, ForgejoPullRequest> createPullRequest(
            ForgejoRepository repository, ForgejoNewPullRequest pullRequest) {
        return Either.left(new ForgejoError.Refused(404, "not found"));
    }

    @Override
    public Either<ForgejoError, ForgejoMergeSettings> mergeSettings(ForgejoRepository repository) {
        return Either.left(new ForgejoError.Refused(404, "not found"));
    }

    @Override
    public Either<ForgejoError, ForgejoMerged> merge(ForgejoRepository repository, long number, ForgejoMerge merge) {
        return Either.left(new ForgejoError.Refused(404, "not found"));
    }

    /**
     * An environment with this Forgejo and remotes of {@code urls}.
     */
    Env env(Map<RemoteName, String> urls) {
        return new Env(StubGit.of().withRemotes(new StubRemotes() {
                    @Override
                    public Either<GitError, Map<RemoteName, String>> urls() {
                        return Either.right(urls);
                    }
                }))
                .with(ForgejoServices.FORGEJO, this);
    }

    static Map<RemoteName, String> origin(String url) {
        return HashMap.of(new RemoteName("origin"), url);
    }
}
