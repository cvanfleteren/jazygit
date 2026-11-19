package net.vanfleteren.jazygit.features.github.failed;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.git.model.BranchName;
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
        String run = String.valueOf(build.runId());
        List<FailedBuild.Job> jobs = build.jobs()
                .filter(job -> Build.Outcome.of(List.of(job)) == Build.Outcome.FAILED)
                .map(job -> new FailedBuild.Job(job.name(), Option.of(run), Option.none()));
        return new FailedBuild(
                Option.none(),
                branch,
                Option.of(build.sha()),
                pullRequest.map(pr -> pr.number().value()),
                jobs,
                build.outcome() == Build.Outcome.RUNNING,
                logs(Option.none(), jobs));
    }

    /**
     * What failed of {@code checks}, the checks on the tip of {@code branch} in {@code repository}, as
     * {@code owner/name}, with its pull request, if it has one: a check run of a workflow, or a commit status, as of an
     * outside CI, which has no run.
     */
    public static FailedBuild of(
            BranchName branch, String repository, List<Check> checks, Option<PullRequest> pullRequest) {
        List<FailedBuild.Job> jobs = checks.filter(check -> check.state() == Checks.FAILING)
                .map(check -> new FailedBuild.Job(
                        check.name(), check.run().map(run -> String.valueOf(run.runId())), check.url()));
        return new FailedBuild(
                Option.of(repository),
                branch,
                Option.none(),
                pullRequest.map(pr -> pr.number().value()),
                jobs,
                checks.exists(check -> !check.done()),
                logs(Option.of(repository), jobs));
    }

    /**
     * How to read the logs of {@code jobs}: with gh, once for each run they are part of; a commit status has its logs
     * outside GitHub, where it links to.
     */
    static String logs(Option<String> repository, List<FailedBuild.Job> jobs) {
        String repo = repository.map(name -> " --repo " + name).getOrElse("");
        List<String> runs =
                jobs.flatMap(FailedBuild.Job::run).distinct().map(run -> "gh run view " + run + " --log-failed" + repo);
        List<String> statuses = jobs.filter(job -> job.run().isEmpty())
                .map(job -> job.name() + ": its logs are not on GitHub"
                        + job.url().map(url -> ", but at " + url).getOrElse(""));
        return runs.appendAll(statuses).mkString("\n");
    }
}
