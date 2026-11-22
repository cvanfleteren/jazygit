package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.FjKeys;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoNewPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.history.StubHistory;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Tracking;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeCandidate;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeHome;

/**
 * Pull requests on Forgejo as the tests have them (ADR 176).
 */
final class PullRequestFixtures {

    static final Instant TIME = Instant.parse("2026-11-21T10:00:00Z");
    static final RemoteName ORIGIN = new RemoteName("origin");
    static final String HOST = "code.example.net";
    static final String URL = "https://" + HOST + "/cvf/nix.git";
    static final ForgejoRepository NIX = new ForgejoRepository("https://" + HOST, "cvf", "nix");
    static final Sha HEAD = new Sha("2".repeat(40));
    static final RemoteBranch FEATURE = new RemoteBranch(ORIGIN, new BranchName("feature"));

    static final ForgejoPullRequest LISTED = new ForgejoPullRequest(
            12,
            "Show the pull request",
            "feature",
            Option.of("cvf/nix"),
            "main",
            false,
            "https://" + HOST + "/cvf/nix/pulls/12");

    private PullRequestFixtures() {}

    static Branch following(String name, Option<String> upstream) {
        return new Branch(
                new BranchName(name),
                false,
                new Sha("f".repeat(40)),
                TIME,
                Option.none(),
                upstream.isDefined(),
                false,
                upstream.<Tracking>map(branch -> new Tracking.Tracks(new Upstream(
                                new BranchRef.Remote(new RemoteBranch(ORIGIN, new BranchName(branch))), 0, 0)))
                        .getOrElse(Tracking.None::new),
                Option.none());
    }

    /**
     * A model with local branch {@code feature} following {@code origin/feature}, and {@code alone} following none, on
     * Forgejo with {@code origin} the home (ADR 177).
     */
    static Model model() {
        return TestModels.localBranchesRead(
                        TestModels.loaded(),
                        List.of(following("feature", Option.of("feature")), following("alone", Option.none())))
                .model()
                .withPart(
                        ForgeHome.PART,
                        ForgeHome.NONE.deciding(Option.of(new ForgeCandidate("forgejo", "Forgejo", ORIGIN, URL))));
    }

    /**
     * {@link #model()} with the open pull requests read: {@link #LISTED} merges {@code origin/feature}.
     */
    static Model listed() {
        return PullRequestUpdate.update(
                        model(),
                        new PullRequestMsg.Listed(ORIGIN, Option.of(HashMap.of(FEATURE, LISTED)), Option.none()))
                .model();
    }

    static ForgejoPullRequestDetails details(String title) {
        return new ForgejoPullRequestDetails(
                12,
                title,
                "cvf",
                new ForgejoPullRequestDetails.Status(ForgejoPullRequestDetails.State.OPEN, false, true),
                new ForgejoPullRequestDetails.Branches("feature", "main", HEAD),
                new ForgejoPullRequestDetails.Activity(TIME.minusSeconds(86_400 * 2), TIME.minusSeconds(3_600)),
                new ForgejoPullRequestDetails.Size(120, 7, 5),
                new ForgejoPullRequestDetails.Conversation(4, List.of("feature"), List.of("anna")),
                "https://" + HOST + "/cvf/nix/pulls/12");
    }

    /**
     * Forgejo as a test has it: one host, {@link #HOST}, with these open pull requests, details, reviews and states.
     */
    record FakeForgejo(
            Either<ForgejoError, List<ForgejoPullRequest>> open,
            Either<ForgejoError, ForgejoPullRequestDetails> details,
            Either<ForgejoError, List<ForgejoReview>> reviews,
            Either<ForgejoError, CommitStatus> status,
            Function<ForgejoNewPullRequest, Either<ForgejoError, ForgejoPullRequest>> create)
            implements Forgejo {

        static FakeForgejo of(ForgejoPullRequestDetails details) {
            return new FakeForgejo(
                    Either.right(List.of(LISTED)),
                    Either.right(details),
                    Either.right(List.of(new ForgejoReview("anna", ForgejoReview.Verdict.APPROVED, false, false))),
                    Either.right(new CommitStatus(
                            CommitStatus.State.FAILED,
                            List.of(new CommitStatus.Status("check / lint (push)", CommitStatus.State.FAILED, "")))),
                    created -> Either.left(new ForgejoError.Refused(500, "not asked for")));
        }

        FakeForgejo withOpen(Either<ForgejoError, List<ForgejoPullRequest>> listed) {
            return new FakeForgejo(listed, details, reviews, status, create);
        }

        /**
         * This Forgejo answering the creation of a pull request as {@code created} does.
         */
        FakeForgejo creating(Function<ForgejoNewPullRequest, Either<ForgejoError, ForgejoPullRequest>> created) {
            return new FakeForgejo(open, details, reviews, status, created);
        }

        @Override
        public Either<ForgejoError, ForgejoPullRequest> createPullRequest(
                ForgejoRepository repository, ForgejoNewPullRequest pullRequest) {
            return repository.equals(NIX)
                    ? create.apply(pullRequest)
                    : Either.left(new ForgejoError.Refused(404, "{\"message\":\"not this repository\"}"));
        }

        /**
         * An environment with this Forgejo, remotes of {@code urls}, and {@code ahead} commits of every branch that
         * its base does not have.
         */
        Env ahead(Map<RemoteName, String> urls, int ahead) {
            Env env = env(urls);
            return new Env(env.git().withHistory(new StubHistory() {
                        @Override
                        public Either<GitError, Integer> countNotOn(BranchRef branch, BranchRef base, int max) {
                            return Either.right(Math.min(ahead, max));
                        }

                        @Override
                        public Either<GitError, Option<String>> firstSubject(BranchRef branch, BranchRef base) {
                            return Either.right(Option.of("Create it"));
                        }
                    }))
                    .with(ForgejoServices.FORGEJO, this);
        }

        @Override
        public Option<ForgejoRepository> repository(String remoteUrl) {
            return ForgejoRepository.of(remoteUrl, FjKeys.NONE.aliases());
        }

        @Override
        public boolean isForgejo(ForgejoRepository repository) {
            return repository.host().equals(HOST);
        }

        @Override
        public Either<ForgejoError, CommitStatus> status(ForgejoRepository repository, Sha sha) {
            return status;
        }

        @Override
        public Either<ForgejoError, List<ForgejoPullRequest>> openPullRequests(ForgejoRepository repository) {
            return open;
        }

        @Override
        public Either<ForgejoError, ForgejoPullRequestDetails> pullRequest(ForgejoRepository repository, long number) {
            return details;
        }

        @Override
        public Either<ForgejoError, List<ForgejoReview>> reviews(ForgejoRepository repository, long number) {
            return reviews;
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

        Env env() {
            return env(HashMap.of(ORIGIN, URL));
        }
    }
}
