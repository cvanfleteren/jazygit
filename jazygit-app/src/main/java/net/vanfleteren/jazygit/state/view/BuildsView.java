package net.vanfleteren.jazygit.state.view;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.gh.model.BuildsPage;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;

/**
 * The builds of a local branch on GitHub, which the content panel shows in place of its log (ADR 130).
 *
 * @param branch   the local branch
 * @param onGitHub the branch on GitHub it follows, whose builds are read; none when it follows none
 * @param builds   the builds read so far, of the newest commit first
 * @param commits  how many commits of the branch were read, with or without builds
 * @param after    where the commits still to be read start; none at the tip
 * @param reading  whether more commits are being read
 * @param stopped  why reading more failed, after some were read; none while it did not
 */
public record BuildsView(
        BranchName branch,
        Option<RemoteBranch> onGitHub,
        Loadable<List<Build>> builds,
        int commits,
        Option<String> after,
        boolean reading,
        Option<Failure> stopped) {

    /**
     * How many commits are read, at most (ADR 130).
     */
    public static final int COMMITS = 100;

    /**
     * The builds of {@code branch}, which follows {@code onGitHub}, before any is read.
     */
    public static BuildsView reading(BranchName branch, RemoteBranch onGitHub) {
        return new BuildsView(branch, Option.of(onGitHub), Loadable.loading(), 0, Option.none(), true, Option.none());
    }

    /**
     * The builds of {@code branch}, which follows no branch on GitHub, so has none.
     */
    public static BuildsView offGitHub(BranchName branch) {
        return new BuildsView(
                branch, Option.none(), new Loadable.Loaded<>(List.empty()), 0, Option.none(), false, Option.none());
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
                Option.none());
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
                builds.toOption().map(read -> failure));
    }
}
