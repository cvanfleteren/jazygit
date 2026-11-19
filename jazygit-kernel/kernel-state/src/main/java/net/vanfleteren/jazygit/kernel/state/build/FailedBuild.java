package net.vanfleteren.jazygit.kernel.state.build;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.BranchName;

/**
 * What failed on one commit of a branch, as a view of a forge shows it, for an action on it, as handing it to an agent
 * (ADR 161). It names no forge: each gives the same value.
 *
 * @param repository  the repository on the forge, as {@code owner/name}, if the view knows it
 * @param branch      the local branch
 * @param commit      the commit that was built, if the view knows it; it may not be the one checked out, as of an
 *                    older build
 * @param pullRequest the number of the branch's pull request, if it has one
 * @param jobs        the jobs that failed; none when nothing failed
 * @param running     whether some of its jobs still run
 * @param logs        how to read the logs of the failed jobs, as the forge side writes it, so that what reads this
 *                    knows no forge's program
 */
public record FailedBuild(
        Option<String> repository,
        BranchName branch,
        Option<String> commit,
        Option<Integer> pullRequest,
        List<Job> jobs,
        boolean running,
        String logs) {

    /**
     * Whether a job failed: what an action on a failed build needs.
     */
    public boolean failed() {
        return jobs.nonEmpty();
    }

    /**
     * A job that failed, or a commit status, as of an outside CI.
     *
     * @param name its name, or the context of the status
     * @param run  the run it is part of, as the forge names it; none for a commit status
     * @param url  where the forge shows it, or where the status links to; none when that is not known
     */
    public record Job(String name, Option<String> run, Option<String> url) {}
}
