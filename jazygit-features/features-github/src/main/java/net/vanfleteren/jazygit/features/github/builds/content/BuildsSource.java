package net.vanfleteren.jazygit.features.github.builds.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.core.git.model.BranchRef;
import net.vanfleteren.jazygit.core.git.model.RemoteBranch;
import net.vanfleteren.jazygit.features.github.builds.BuildsShown;
import net.vanfleteren.jazygit.features.github.builds.BuildsView;
import net.vanfleteren.jazygit.features.github.builds.WorkflowRuns;
import net.vanfleteren.jazygit.features.github.failed.FailedBuilds;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * The builds of the local branch highlighted in the branches pane, in place of its log, while they are shown for it
 * (ADR 131). The GitHub feature contributes it, so it is asked before the content panel's own sources (ADR 154).
 */
public record BuildsSource() implements ContentSource {

    /**
     * The builds of the branch highlighted in the branches pane, in place of its log (ADR 131), which take the focus
     * from the branches once they show after {@code B} opened them.
     */
    static final ContentArea AREA = ContentArea.of(BuildsView.AREA, BranchesPanel.PANE)
            .opening(model -> model.part(BuildsView.PART).map(BuildsView::branch));

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        if (pane != Mirrored.BRANCHES) {
            return Option.none();
        }
        return model.part(BuildsView.PART)
                .filter(view -> model.repoView().branches().tab() == BranchesTab.LOCAL
                        // A divergence or the commits of a branch opened over the builds show in their place, as the
                        // content panel's own sources asked for them before the builds.
                        && model.repoView().branches().divergence().isEmpty()
                        && model.repoView().branches().branchCommits().isEmpty()
                        && model.repoView()
                                .preview()
                                .branchLog()
                                .exists(log -> log.key().equals(new BranchRef.Local(view.branch()))))
                .map(view -> builds(view, model));
    }

    @Override
    public List<ContentArea> areas() {
        return List.of(AREA);
    }

    /**
     * The builds {@code view} shows of its branch: the chart and list of them once some are read, or else why there
     * are none.
     */
    private static Shown builds(BuildsView view, Model model) {
        String title = title(view);
        return Shown.whenLoaded(
                title,
                view.builds(),
                _ -> view.onGitHub()
                        .map(remote -> view.shownOf()
                                .<Shown>map(drawn -> new Shown.Area(
                                        AREA,
                                        title,
                                        new Builds(view.branch()),
                                        new BuildsBody(
                                                drawn,
                                                failedBuild(
                                                        view.branch(),
                                                        drawn,
                                                        model.gitHub().pullRequest(remote)))))
                                .getOrElse(() -> noBuilds(view, title, remote)))
                        .getOrElse(() -> Shown.dim(
                                title,
                                Messages.get(
                                        "github.builds.offGitHub", view.branch().value()))));
    }

    /**
     * What failed in the build whose jobs {@code drawn} shows on one time line, for the keys other features add to a
     * failed build (ADR 161): only one of {@code branch}, as with every branch shown it can be another's run, and
     * {@code branch}'s pull request is not that run's.
     */
    static Option<FailedBuild> failedBuild(BranchName branch, BuildsShown drawn, Option<PullRequest> pullRequest) {
        return drawn.onTimeLine()
                .filter(build -> !drawn.allBranches() || drawn.mine().contains(build.sha()))
                .map(build -> FailedBuilds.of(branch, build, pullRequest));
    }

    /**
     * The title of the builds {@code view} shows: of which branch, whether more are read, and whether more can be.
     */
    private static String title(BuildsView view) {
        String ofBranch = ofBranch(view);
        Option<WorkflowRuns> runs = view.shownTab().flatMap(tab -> view.runsOf(tab.workflow()));
        // On every branch, the title says when more runs of the tab are being read, as m asked: the first ones say so
        // in the body.
        String every = view.allBranches()
                ? runs.filter(ofTab -> ofTab.reading() && ofTab.count() > 0)
                        .map(ofTab -> Messages.get(
                                "github.builds.panel.everyBranchReading", ofBranch, String.valueOf(ofTab.count())))
                        .getOrElse(() -> Messages.get("github.builds.panel.everyBranch", ofBranch))
                : ofBranch;
        // Older builds can be read with m, or read again after failing: of the branch, or on every branch, of the tab
        // (ADR 132).
        boolean more = view.allBranches() ? runs.exists(WorkflowRuns::hasMore) : view.hasMore();
        if (!more) {
            return every;
        }
        boolean failed = view.allBranches() ? runs.exists(WorkflowRuns::hasFailed) : view.hasFailed();
        return Messages.get(failed ? "github.builds.panel.retry" : "github.builds.panel.more", every);
    }

    /**
     * What the title of the builds {@code view} shows says of its branch: whether more commits are read, or reading
     * them stopped, and how many were read.
     */
    private static String ofBranch(BuildsView view) {
        String branch = view.branch().value();
        String commits = String.valueOf(view.commits());
        if (view.reading()) {
            return Messages.get("github.builds.panel.reading", branch, commits);
        }
        if (view.stopped().isDefined()) {
            return Messages.get("github.builds.panel.stopped", branch, commits);
        }
        return Messages.get("github.builds.panel.title", branch, commits);
    }

    /**
     * What the content panel says, titled {@code title}, in place of the builds of {@code view}, whose branch follows
     * {@code remote} on GitHub, while it has none to show.
     */
    private static Shown noBuilds(BuildsView view, String title, RemoteBranch remote) {
        return switch (NoBuilds.of(view)) {
            case READING -> Shown.loading(title);
            case NOT_ON_GITHUB -> Shown.dim(title, Messages.get("github.builds.notOnGitHub", remote.shortName()));
            case NONE ->
                Shown.dim(
                        title, Messages.get("github.builds.none", remote.shortName(), String.valueOf(view.commits())));
        };
    }

    /**
     * The builds of the local branch {@code branch}, as an area keeps where it is scrolled to for them: while more are
     * read, it stays (ADR 131).
     */
    record Builds(BranchName branch) implements ScrolledContent.Other {}

    /**
     * Why the content panel shows no builds of a branch that follows one on GitHub (ADR 131). Once any is read, a tab
     * shows it.
     */
    private enum NoBuilds {
        /** None is read yet, while the first commits are. */
        READING,
        /** The branch it follows is not on GitHub. */
        NOT_ON_GITHUB,
        /** None of the commits read has builds. */
        NONE;

        /**
         * Why {@code view} shows no builds.
         */
        static NoBuilds of(BuildsView view) {
            if (view.reading()) {
                return READING;
            }
            return view.commits() == 0 ? NOT_ON_GITHUB : NONE;
        }
    }
}
