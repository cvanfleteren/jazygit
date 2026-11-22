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

        @Override
        public Either<ForgejoError, ForgejoMergeSettings> mergeSettings(ForgejoRepository repository) {
            return Either.left(new ForgejoError.Unreachable("no Forgejo"));
        }

        @Override
        public Either<ForgejoError, ForgejoMerged> merge(
                ForgejoRepository repository, long number, ForgejoMerge merge) {
            return Either.left(new ForgejoError.Unreachable("no Forgejo"));
        }

        @Override
        public Either<ForgejoError, ForgejoBranchRules> branchRules(ForgejoRepository repository, String branch) {
            return Either.left(new ForgejoError.Unreachable("no Forgejo"));
        }

        @Override
        public Either<ForgejoError, Option<ForgejoScheduledMerge>> scheduledMerge(
                ForgejoRepository repository, long number) {
            return Either.left(new ForgejoError.Unreachable("no Forgejo"));
        }

        @Override
        public Option<ForgejoError> cancelScheduledMerge(ForgejoRepository repository, long number) {
            return Option.of(new ForgejoError.Unreachable("no Forgejo"));
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
     * Whether fj's token goes to the host of {@code repository} unencrypted: it has one for it, and it is asked over
     * plain HTTP, as its remote says (ADR 174).
     */
    default boolean sendsTokenInTheClear(ForgejoRepository repository) {
        return false;
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

    /**
     * How {@code repository} lets its pull requests be merged (ADR 180).
     */
    Either<ForgejoError, ForgejoMergeSettings> mergeSettings(ForgejoRepository repository);

    /**
     * Merges pull request {@code number} of {@code repository} as {@code merge} says (ADR 180), at once, or schedules
     * it for when its checks succeed (ADR 181): how that went, or why Forgejo did not do it.
     */
    Either<ForgejoError, ForgejoMerged> merge(ForgejoRepository repository, long number, ForgejoMerge merge);

    /**
     * What {@code branch} of {@code repository} requires before a pull request is merged into it (ADR 181).
     */
    Either<ForgejoError, ForgejoBranchRules> branchRules(ForgejoRepository repository, String branch);

    /**
     * The merge of pull request {@code number} of {@code repository} scheduled for when its checks succeed, as its
     * timeline tells it (ADR 181); none when none is.
     */
    Either<ForgejoError, Option<ForgejoScheduledMerge>> scheduledMerge(ForgejoRepository repository, long number);

    /**
     * Cancels the merge of pull request {@code number} of {@code repository} scheduled for when its checks succeed
     * (ADR 181): none once cancelled, else why not, as the 404 of one that is no longer scheduled.
     */
    Option<ForgejoError> cancelScheduledMerge(ForgejoRepository repository, long number);
}
