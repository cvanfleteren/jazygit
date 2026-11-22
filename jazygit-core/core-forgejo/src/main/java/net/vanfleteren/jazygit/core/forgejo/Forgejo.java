package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * What jazygit asks Forgejo (ADR 174).
 */
public interface Forgejo {

    /**
     * No Forgejo: no remote is on it, as without the feature's services.
     */
    Forgejo NONE = new Forgejo() {
        @Override
        public Option<ForgejoRepository> repository(String remoteUrl) {
            return Option.none();
        }

        @Override
        public boolean isForgejo(ForgejoRepository repository) {
            return false;
        }

        @Override
        public Either<ForgejoError, CommitStatus> status(ForgejoRepository repository, Sha sha) {
            return Either.left(new ForgejoError.Unreachable("no Forgejo"));
        }

        @Override
        public Either<ForgejoError, List<ForgejoPullRequest>> openPullRequests(ForgejoRepository repository) {
            return Either.left(new ForgejoError.Unreachable("no Forgejo"));
        }

        @Override
        public Either<ForgejoError, ForgejoPullRequestDetails> pullRequest(ForgejoRepository repository, long number) {
            return Either.left(new ForgejoError.Unreachable("no Forgejo"));
        }

        @Override
        public Either<ForgejoError, List<ForgejoReview>> reviews(ForgejoRepository repository, long number) {
            return Either.left(new ForgejoError.Unreachable("no Forgejo"));
        }

        @Override
        public Either<ForgejoError, ForgejoPullRequest> createPullRequest(
                ForgejoRepository repository, ForgejoNewPullRequest pullRequest) {
            return Either.left(new ForgejoError.Unreachable("no Forgejo"));
        }
    };

    /**
     * The repository the git remote {@code remoteUrl} names, on a host that may be Forgejo; none for a remote that
     * cannot be.
     */
    Option<ForgejoRepository> repository(String remoteUrl);

    /**
     * Whether the host of {@code repository} is Forgejo: it answers {@code /api/forgejo/v1/version}. Each host is
     * asked once.
     */
    boolean isForgejo(ForgejoRepository repository);

    /**
     * Whether the host of {@code repository} is Forgejo, as {@link #isForgejo} tells it; none when that cannot be
     * told now, as of a host that could not be reached (ADR 177).
     */
    default Option<Boolean> tells(ForgejoRepository repository) {
        return Option.of(isForgejo(repository));
    }

    /**
     * How the builds of {@code sha} in {@code repository} went.
     */
    Either<ForgejoError, CommitStatus> status(ForgejoRepository repository, Sha sha);

    /**
     * The open pull requests of {@code repository}, those from forks too (ADR 176).
     */
    Either<ForgejoError, List<ForgejoPullRequest>> openPullRequests(ForgejoRepository repository);

    /**
     * Pull request {@code number} of {@code repository}, as it is shown.
     */
    Either<ForgejoError, ForgejoPullRequestDetails> pullRequest(ForgejoRepository repository, long number);

    /**
     * The reviews of pull request {@code number} of {@code repository}, in the order they were given.
     */
    Either<ForgejoError, List<ForgejoReview>> reviews(ForgejoRepository repository, long number);

    /**
     * Creates {@code pullRequest} in {@code repository} (ADR 178): the pull request as Forgejo answers it, or why it
     * did not create it, as the 409 that names the one open already.
     */
    Either<ForgejoError, ForgejoPullRequest> createPullRequest(
            ForgejoRepository repository, ForgejoNewPullRequest pullRequest);
}
