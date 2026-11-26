package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.HashMap;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.FjKeys;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoBranchRules;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerge;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeSettings;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerged;
import net.vanfleteren.jazygit.core.forgejo.ForgejoNewPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview;
import net.vanfleteren.jazygit.core.forgejo.ForgejoScheduledMerge;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.history.StubHistory;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.RemoteUrl;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Tracking;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.FeatureId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeCandidate;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeHome;

/**
 * Pull requests on Forgejo as the tests have them (ADR 176).
 */
public final class PullRequestFixtures {

    public static final Instant TIME = Instant.parse("2026-11-21T10:00:00Z");
    public static final RemoteName ORIGIN = new RemoteName("origin");
    public static final String HOST = "code.example.net";
    public static final String URL = "https://" + HOST + "/cvf/nix.git";
    public static final ForgejoRepository NIX = new ForgejoRepository("https://" + HOST, "cvf", "nix");
    public static final Sha HEAD = new Sha("2".repeat(40));
    public static final RemoteBranch FEATURE = new RemoteBranch(ORIGIN, new BranchName("feature"));

    public static final ForgejoPullRequest LISTED = new ForgejoPullRequest(
            new PrNumber(12),
            "Show the pull request",
            new ForgeBranch("feature"),
            Option.of("cvf/nix"),
            new ForgeBranch("main"),
            false,
            "https://" + HOST + "/cvf/nix/pulls/12");

    /**
     * A repository that allows squashing and rebasing, and deletes the head branch once merged (ADR 180).
     */
    public static final ForgejoMergeSettings SETTINGS =
            new ForgejoMergeSettings(LinkedHashSet.of(ForgejoMergeStyle.SQUASH, ForgejoMergeStyle.REBASE), true);

    private PullRequestFixtures() {}

    public static Branch following(String name, Option<String> upstream) {
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
    public static Model model() {
        return TestModels.localBranchesRead(
                        TestModels.loaded(),
                        List.of(following("feature", Option.of("feature")), following("alone", Option.none())))
                .model()
                .withPart(
                        ForgeHome.PART,
                        ForgeHome.NONE.deciding(Option.of(
                                new ForgeCandidate(new FeatureId("forgejo"), "Forgejo", ORIGIN, new RemoteUrl(URL)))));
    }

    /**
     * {@link #model()} with the open pull requests read: {@link #LISTED} merges {@code origin/feature}.
     */
    public static Model listed() {
        return PullRequestUpdate.update(
                        model(),
                        new PullRequestMsg.Listed(ORIGIN, Option.of(HashMap.of(FEATURE, LISTED)), Option.none()))
                .model();
    }

    public static ForgejoPullRequestDetails details(String title) {
        return new ForgejoPullRequestDetails(
                new PrNumber(12),
                title,
                "cvf",
                new ForgejoPullRequestDetails.Status(ForgejoPullRequestDetails.State.OPEN, false, true),
                new ForgejoPullRequestDetails.Branches(new ForgeBranch("feature"), new ForgeBranch("main"), HEAD),
                new ForgejoPullRequestDetails.Activity(TIME.minusSeconds(86_400 * 2), TIME.minusSeconds(3_600)),
                new ForgejoPullRequestDetails.Size(120, 7, 5),
                new ForgejoPullRequestDetails.Conversation(4, List.of("feature"), List.of("anna")),
                "https://" + HOST + "/cvf/nix/pulls/12");
    }

    /**
     * Forgejo as a test has it: one host, {@link #HOST}, with these open pull requests, details, reviews and states.
     */
    public record FakeForgejo(
            Either<ForgejoError, List<ForgejoPullRequest>> open,
            Either<ForgejoError, ForgejoPullRequestDetails> details,
            Either<ForgejoError, List<ForgejoReview>> reviews,
            Either<ForgejoError, CommitStatus> status,
            Function<ForgejoNewPullRequest, Either<ForgejoError, ForgejoPullRequest>> create,
            Merging merging)
            implements Forgejo {

        public static FakeForgejo of(ForgejoPullRequestDetails details) {
            return new FakeForgejo(
                    Either.right(List.of(LISTED)),
                    Either.right(details),
                    Either.right(List.of(new ForgejoReview("anna", ForgejoReview.Verdict.APPROVED, false, false))),
                    Either.right(new CommitStatus(
                            CommitStatus.State.FAILED,
                            List.of(new CommitStatus.Status("check / lint (push)", CommitStatus.State.FAILED, "")))),
                    created -> Either.left(new ForgejoError.Refused(500, "not asked for")),
                    new Merging(
                            Either.right(SETTINGS),
                            merged -> Either.left(new ForgejoError.Refused(500, "not asked for")),
                            Either.right(ForgejoBranchRules.NONE),
                            Either.right(Option.none()),
                            Option.none()));
        }

        /**
         * This Forgejo with no pull requests to read, nor anything to create or merge, failing as {@code error}.
         */
        public static FakeForgejo failing(
                Either<ForgejoError, ForgejoPullRequestDetails> details,
                Either<ForgejoError, List<ForgejoReview>> reviews,
                ForgejoError error) {
            return new FakeForgejo(
                    Either.right(List.of(LISTED)),
                    details,
                    reviews,
                    Either.left(error),
                    created -> Either.left(error),
                    new Merging(
                            Either.left(error),
                            merged -> Either.left(error),
                            Either.left(error),
                            Either.left(error),
                            Option.of(error)));
        }

        public FakeForgejo withOpen(Either<ForgejoError, List<ForgejoPullRequest>> listed) {
            return new FakeForgejo(listed, details, reviews, status, create, merging);
        }

        /**
         * This Forgejo with {@code checks} as the builds of every commit.
         */
        public FakeForgejo withStatus(Either<ForgejoError, CommitStatus> checks) {
            return new FakeForgejo(open, details, reviews, checks, create, merging);
        }

        /**
         * This Forgejo answering the creation of a pull request as {@code created} does.
         */
        public FakeForgejo creating(Function<ForgejoNewPullRequest, Either<ForgejoError, ForgejoPullRequest>> created) {
            return new FakeForgejo(open, details, reviews, status, created, merging);
        }

        /**
         * This Forgejo answering a merge of pull request 12 as {@code merged} does (ADR 180).
         */
        public FakeForgejo merging(Function<ForgejoMerge, Either<ForgejoError, ForgejoMerged>> merged) {
            return new FakeForgejo(
                    open,
                    details,
                    reviews,
                    status,
                    create,
                    new Merging(merging.settings(), merged, merging.rules(), merging.scheduled(), merging.cancel()));
        }

        /**
         * This Forgejo with what the base branch requires, the merge scheduled, and how cancelling it is answered, as
         * these say (ADR 181).
         */
        public FakeForgejo scheduling(
                Either<ForgejoError, ForgejoBranchRules> rules,
                Either<ForgejoError, Option<ForgejoScheduledMerge>> scheduled,
                Option<ForgejoError> cancel) {
            return new FakeForgejo(
                    open,
                    details,
                    reviews,
                    status,
                    create,
                    new Merging(merging.settings(), merging.merge(), rules, scheduled, cancel));
        }

        @Override
        public Either<ForgejoError, ForgejoMergeSettings> mergeSettings(ForgejoRepository repository) {
            return merging.settings();
        }

        @Override
        public Either<ForgejoError, ForgejoMerged> merge(
                ForgejoRepository repository, PrNumber number, ForgejoMerge asked) {
            return repository.equals(NIX) && number.value() == 12
                    ? merging.merge().apply(asked)
                    : Either.left(new ForgejoError.Refused(404, "{\"message\":\"not this pull request\"}"));
        }

        @Override
        public Either<ForgejoError, ForgejoBranchRules> branchRules(ForgejoRepository repository, String branch) {
            return merging.rules();
        }

        @Override
        public Either<ForgejoError, Option<ForgejoScheduledMerge>> scheduledMerge(
                ForgejoRepository repository, PrNumber number) {
            return merging.scheduled();
        }

        @Override
        public Option<ForgejoError> cancelScheduledMerge(ForgejoRepository repository, PrNumber number) {
            return merging.cancel();
        }

        @Override
        public Either<ForgejoError, List<ForgejoCheck>> checks(ForgejoRepository repository, Sha sha) {
            return Either.left(new ForgejoError.Refused(404, "not found"));
        }

        @Override
        public Either<ForgejoError, String> jobLog(ForgejoRepository repository, ForgejoCheck.ActionsJob job) {
            return Either.left(new ForgejoError.Refused(404, "not found"));
        }

        @Override
        public Option<ForgejoError> rerunJob(ForgejoRepository repository, ForgejoCheck.ActionsJob job) {
            return Option.of(new ForgejoError.Refused(404, "not found"));
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
        public Env ahead(Map<RemoteName, RemoteUrl> urls, int ahead) {
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

        // fj has a token for the host, which goes to it unencrypted over plain HTTP.
        @Override
        public boolean sendsTokenInTheClear(ForgejoRepository repository) {
            return !repository.secure();
        }

        @Override
        public Option<ForgejoRepository> repository(RemoteUrl remoteUrl) {
            return ForgejoRepository.of(remoteUrl.value(), FjKeys.NONE.aliases());
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
        public Either<ForgejoError, ForgejoPullRequestDetails> pullRequest(
                ForgejoRepository repository, PrNumber number) {
            return details;
        }

        @Override
        public Either<ForgejoError, List<ForgejoReview>> reviews(ForgejoRepository repository, PrNumber number) {
            return reviews;
        }

        /**
         * An environment with this Forgejo and remotes of {@code urls}.
         */
        public Env env(Map<RemoteName, RemoteUrl> urls) {
            return new Env(StubGit.of().withRemotes(new StubRemotes() {
                        @Override
                        public Either<GitError, Map<RemoteName, RemoteUrl>> urls() {
                            return Either.right(urls);
                        }
                    }))
                    .with(ForgejoServices.FORGEJO, this);
        }

        public Env env() {
            return env(HashMap.of(ORIGIN, new RemoteUrl(URL)));
        }

        /**
         * How this Forgejo answers what merging asks (ADR 180, 181): how the repository lets pull requests be merged,
         * a merge, what the base branch requires, the merge scheduled, and cancelling it.
         */
        record Merging(
                Either<ForgejoError, ForgejoMergeSettings> settings,
                Function<ForgejoMerge, Either<ForgejoError, ForgejoMerged>> merge,
                Either<ForgejoError, ForgejoBranchRules> rules,
                Either<ForgejoError, Option<ForgejoScheduledMerge>> scheduled,
                Option<ForgejoError> cancel) {}
    }
}
