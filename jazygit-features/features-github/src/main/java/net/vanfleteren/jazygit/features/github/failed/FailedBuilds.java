package net.vanfleteren.jazygit.features.github.failed;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;

/**
 * What failed on GitHub, as the builds of a branch and the checks popup show it, as a {@link FailedBuild} that names
 * no forge, for the actions other features add to them (ADR 161).
 */
public final class FailedBuilds {

    private FailedBuilds() {}

    /**
     * What failed in {@code build} of {@code branch}, with its pull request, if it has one.
     */
    public static FailedBuild of(BranchName branch, Build build, Option<PullRequest> pullRequest) {
        Option<String> run = Option.of(String.valueOf(build.runId()));
        List<Failure> failures = build.jobs()
                .filter(job -> Build.Outcome.of(List.of(job)) == Build.Outcome.FAILED)
                .map(job -> new Failure(new FailedBuild.Job(job.name(), Option.none()), run));
        return new FailedBuild(
                Option.none(),
                branch,
                Option.of(build.sha()),
                pullRequest.map(FailedBuilds::request),
                failures.map(Failure::job),
                build.outcome() == Build.Outcome.RUNNING,
                logs(Option.none(), failures));
    }

    /**
     * What failed of {@code checks}, the checks on the tip of {@code branch} in {@code repository}, as
     * {@code owner/name}, with its pull request, if it has one: a check run of a workflow, or a commit status, as of an
     * outside CI, which has no run.
     */
    public static FailedBuild of(
            BranchName branch, String repository, List<Check> checks, Option<PullRequest> pullRequest) {
        List<Failure> failures = checks.filter(check -> check.state() == Checks.FAILING)
                .map(check -> new Failure(
                        new FailedBuild.Job(check.name(), check.url()),
                        check.run().map(run -> String.valueOf(run.runId()))));
        return new FailedBuild(
                Option.of(repository),
                branch,
                Option.none(),
                pullRequest.map(FailedBuilds::request),
                failures.map(Failure::job),
                checks.exists(check -> !check.done()),
                logs(Option.of(repository), failures));
    }

    private static FailedBuild.ChangeRequest request(PullRequest pullRequest) {
        return new FailedBuild.ChangeRequest("pull request", pullRequest.number());
    }

    /**
     * How to read the logs of {@code failures}: with gh, once for each run they are part of; a commit status has its
     * logs outside GitHub, where it links to.
     */
    private static String logs(Option<String> repository, List<Failure> failures) {
        String repo = repository.map(name -> " --repo " + name).getOrElse("");
        List<String> runs =
                failures.flatMap(Failure::run).distinct().map(run -> "gh run view " + run + " --log-failed" + repo);
        List<String> statuses = failures.filter(failure -> failure.run().isEmpty())
                .map(Failure::job)
                .map(job -> job.name() + ": its logs are not on GitHub"
                        + job.url().map(url -> ", but at " + url).getOrElse(""));
        return runs.appendAll(statuses).mkString("\n");
    }

    /**
     * A job that failed, with the workflow run it is part of: what GitHub needs to read its logs, which a
     * {@link FailedBuild} does not carry.
     */
    private record Failure(FailedBuild.Job job, Option<String> run) {}
}
