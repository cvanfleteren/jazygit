package net.vanfleteren.jazygit.state.view;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.gh.model.BuildsPage;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;

/**
 * The builds of a local branch on GitHub, which the content panel shows in place of its log (ADR 131).
 *
 * @param branch    the local branch
 * @param onGitHub  the branch on GitHub it follows, whose builds are read; none when it follows none
 * @param builds    the builds read so far, of the newest commit first
 * @param commits   how many commits of the branch were read, with or without builds
 * @param after     where the commits still to be read start; none at the tip
 * @param reading   whether more commits are being read
 * @param stopped   why reading more failed, after some were read; none while it did not
 * @param workflows the files of the workflows read, at the commit of a build, which say which job needs which; none
 *                  for one being read, or that could not be
 */
public record BuildsView(
        BranchName branch,
        Option<RemoteBranch> onGitHub,
        Loadable<List<Build>> builds,
        int commits,
        Option<String> after,
        boolean reading,
        Option<Failure> stopped,
        Map<BuildsView.FileAt, Option<Workflow>> workflows) {

    /**
     * How many commits are read, at most (ADR 131).
     */
    public static final int COMMITS = 100;

    /**
     * The builds of {@code branch}, which follows {@code onGitHub}, before any is read.
     */
    public static BuildsView reading(BranchName branch, RemoteBranch onGitHub) {
        return new BuildsView(
                branch,
                Option.of(onGitHub),
                Loadable.loading(),
                0,
                Option.none(),
                true,
                Option.none(),
                HashMap.empty());
    }

    /**
     * The builds of {@code branch}, which follows no branch on GitHub, so has none.
     */
    public static BuildsView offGitHub(BranchName branch) {
        return new BuildsView(
                branch,
                Option.none(),
                new Loadable.Loaded<>(List.empty()),
                0,
                Option.none(),
                false,
                Option.none(),
                HashMap.empty());
    }

    /**
     * The last of {@code builds}, the newest first, that is done and ran a job: the one whose jobs show on one time
     * line.
     */
    public static Option<Build> lastDoneOf(List<Build> builds) {
        return builds.find(build -> build.duration().isDefined() && !build.ran().isEmpty());
    }

    /**
     * Whether this waits for the commits of {@code branch} from {@code from} on.
     */
    public boolean awaits(RemoteBranch branch, Option<String> from) {
        return reading && onGitHub.contains(branch) && after.equals(from);
    }

    /**
     * This, with the builds of {@code page} after those read; it reads on while fewer than {@link #COMMITS} commits
     * were read and older ones are left. None read means the branch is not on GitHub.
     */
    public BuildsView read(Option<BuildsPage> page) {
        int read = commits + page.map(BuildsPage::commits).getOrElse(0);
        Option<String> next = page.flatMap(BuildsPage::next);
        return new BuildsView(
                branch,
                onGitHub,
                new Loadable.Loaded<>(builds.getOrElse(List.empty())
                        .appendAll(page.map(BuildsPage::builds).getOrElse(List.empty()))),
                read,
                next,
                read < COMMITS && next.isDefined(),
                Option.none(),
                workflows);
    }

    /**
     * This, done reading as it failed for {@code failure}: what was read stays, stopped for it; with nothing read yet,
     * the builds failed.
     */
    public BuildsView failed(Failure failure) {
        return new BuildsView(
                branch,
                onGitHub,
                builds.failed(failure),
                commits,
                after,
                false,
                builds.toOption().map(read -> failure),
                workflows);
    }

    /**
     * Of each workflow and event, the last build that is done: those whose jobs show on one time line.
     */
    public List<Build> lastDone() {
        List<Build> read = builds.getOrElse(List.empty());
        return read.map(build -> io.vavr.Tuple.of(build.workflow(), build.event()))
                .distinct()
                .flatMap(workflow -> lastDoneOf(read.filter(build ->
                        build.workflow().equals(workflow._1()) && build.event().equals(workflow._2()))));
    }

    /**
     * The files of the workflows of the last builds that are done, that are not read yet, nor being read.
     */
    public List<FileAt> unread() {
        return lastDone()
                .flatMap(build -> build.file().map(path -> new FileAt(build.sha(), path)))
                .distinct()
                .filter(file -> !workflows.containsKey(file));
    }

    /**
     * This, with {@code read} of the files of workflows; a file being read is in it as none.
     */
    public BuildsView withWorkflows(Map<FileAt, Option<Workflow>> read) {
        return new BuildsView(
                branch, onGitHub, builds, commits, after, reading, stopped, workflows.merge(read, (was, now) -> now));
    }

    /**
     * Of each of the last builds that are done, by the id of its run, which of its jobs each job needs, as the file of
     * its workflow says at its commit; a build is left out while that is not read, or when it could not be.
     */
    public Map<Long, Map<String, Set<String>>> needs() {
        return HashMap.ofEntries(lastDone()
                .flatMap(build -> build.file()
                        .flatMap(path -> workflow(build.sha(), path))
                        .map(workflow -> io.vavr.Tuple.of(
                                build.runId(),
                                workflow.needs(
                                        build.ran().map(Build.Job::name), called -> workflow(build.sha(), called))))));
    }

    private Option<Workflow> workflow(String sha, String path) {
        return workflows.get(new FileAt(sha, path)).flatMap(read -> read);
    }

    /**
     * A file at a commit.
     *
     * @param sha  the commit
     * @param path its path
     */
    public record FileAt(String sha, String path) {}
}
