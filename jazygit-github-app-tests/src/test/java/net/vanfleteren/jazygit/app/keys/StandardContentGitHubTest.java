package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileContent;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Divergence;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.branches.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.features.github.builds.BuildsMsg;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.DivergenceView;
import net.vanfleteren.jazygit.kernel.state.view.FilesShown;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.state.view.UnchangedNode;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSources;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import org.junit.jupiter.api.Test;

/**
 * The tests of StandardContentTest with GitHub in the app (ADR 171).
 */
class StandardContentGitHubTest {

    private static final ContentSources SOURCES = new StandardContent().sources();

    private static final BranchName MAIN = new BranchName("main");

    /**
     * The names of the sources that answer for {@code pane} in {@code model}, in the order they are asked.
     */
    private static List<String> answering(Model model, Mirrored pane) {
        return SOURCES.sources()
                .filter(source -> source.shown(model, pane).isDefined())
                .map(StandardContentGitHubTest::name);
    }

    private static String name(ContentSource source) {
        return (source instanceof Feature.GatedSource(var feature, ContentSource gated) ? gated : source)
                .getClass()
                .getSimpleName();
    }

    @Test
    void theAppAsksTheSourcesOfTheFeaturesBeforeTheContentPanelsOwn() {
        assertEquals(
                List.of(
                        "BuildsSource",
                        "PullRequestSource",
                        "CommitFilesSource",
                        "UnchangedSource",
                        "FileDiffsSource",
                        "DivergenceSource",
                        "BranchCommitsSource",
                        "BranchLogSource",
                        "CommitSource",
                        "StashSource"),
                SOURCES.sources().map(StandardContentGitHubTest::name));
    }

    @Test
    void eachPaneHasOneSourceOfItsOwn() {
        Model model = TestModels.loaded();

        assertEquals(List.of("FileDiffsSource"), answering(model, Mirrored.FILES));
        assertEquals(List.of("BranchLogSource"), answering(model, Mirrored.BRANCHES));
        assertEquals(List.of("CommitSource"), answering(model, Mirrored.COMMITS));
        assertEquals(List.of("StashSource"), answering(model, Mirrored.STASH));
        assertEquals(List.of("PullRequestSource"), answering(model, Mirrored.of(PrOverview.SCREEN)));
    }

    @Test
    void theBuildsOfABranchComeBeforeItsLog() {
        assertEquals(List.of("BuildsSource", "BranchLogSource"), answering(buildsOpen(), Mirrored.BRANCHES));
    }

    /**
     * The builds are the GitHub feature's, asked before the content panel's own sources, yet a divergence or the
     * commits of a branch opened over them still show in their place, as before the builds left the content panel.
     */
    @Test
    void whatIsOpenedOverTheBuildsComesBeforeThem() {
        Model builds = buildsOpen();
        Model diverged = builds.mapRepoView(repoView -> repoView.mapBranches(branches ->
                branches.withDivergence(diverged().repoView().branches().divergence())));
        Model commits = builds.mapRepoView(repoView -> repoView.mapBranches(branches -> branches.withBranchCommits(
                branchCommitsOpen().repoView().branches().branchCommits())));

        assertEquals("DivergenceSource", answering(diverged, Mirrored.BRANCHES).head());
        assertEquals(
                "BranchCommitsSource", answering(commits, Mirrored.BRANCHES).head());
    }

    @Test
    void noSourceOfAPaneAnswersForAnother() {
        List<Model> models = List.of(TestModels.loaded(), unchanged(), buildsOpen(), diverged(), branchCommitsOpen());
        List<Mirrored> panes = Mirrored.REPOSITORY.append(Mirrored.of(PrOverview.SCREEN));

        models.forEach(model -> panes.forEach(pane -> assertEquals(
                List.empty(),
                answering(model, pane).filter(source -> !ownSources(pane).contains(source)),
                String.valueOf(pane))));
    }

    /**
     * The sources that may answer for {@code pane}.
     */
    private static List<String> ownSources(Mirrored pane) {
        return io.vavr.collection.HashMap.of(
                        Mirrored.FILES,
                        List.of("CommitFilesSource", "UnchangedSource", "FileDiffsSource"),
                        Mirrored.BRANCHES,
                        List.of("DivergenceSource", "BranchCommitsSource", "BuildsSource", "BranchLogSource"),
                        Mirrored.COMMITS,
                        List.of("CommitSource"),
                        Mirrored.STASH,
                        List.of("StashSource"),
                        Mirrored.of(PrOverview.SCREEN),
                        List.of("PullRequestSource"))
                .getOrElse(pane, List.empty());
    }

    /**
     * The files pane showing a file without changes, as all files are shown.
     */
    private static Model unchanged() {
        return TestModels.loaded()
                .mapRepoView(view -> view.mapFiles(files -> files.withShown(FilesShown.ALL))
                        .mapFiles(files -> files.withUnchanged(Option.of(new UnchangedNode.File(new Preview<>(
                                "README.md",
                                new Loadable.Loaded<>(new FileContent.Text(DiffText.of("# Title\n")))))))));
    }

    /**
     * The branch {@code main}, which follows {@code origin/main}, highlighted with its log read, and its builds
     * opened with {@code B} (ADR 131).
     */
    private static Model buildsOpen() {
        RemoteBranch originMain = new RemoteBranch("origin", MAIN);
        Branch main = Fixtures.branch(
                MAIN,
                false,
                new Sha("abc"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(originMain), 0, 0)));
        Model read =
                TestModels.localBranchesRead(TestModels.loaded(), List.of(main)).model();
        BranchRef ref = new BranchRef.Local(MAIN);
        Model logRead = Update.update(
                        Update.update(read, new SelectionMsg.BranchSelected(ref))
                                .model(),
                        TestModels.branchLogLoaded(ref, List.empty()))
                .model();
        return Update.update(logRead, new BuildsMsg.Toggled(MAIN)).model();
    }

    /**
     * The branch {@code main} diverged from its upstream, by a commit that is highlighted.
     */
    private static Model diverged() {
        Model diverged = TestModels.loaded()
                .mapRepoView(repoView ->
                        repoView.mapBranches(branches -> branches.withDivergence(Option.of(new DivergenceView(
                                MAIN,
                                new BranchRef.Remote(new RemoteBranch("origin", MAIN)),
                                DivergenceView.Against.UPSTREAM,
                                new Loadable.Loaded<>(new Divergence(
                                        new Divergence.Side(List.of(commit("cccc")), 1),
                                        new Divergence.Side(List.of(), 0))),
                                Option.none())))));
        return Update.update(diverged, new SelectionMsg.DivergenceCommitSelected(new Sha("cccc")))
                .model();
    }

    /**
     * The commits of the branch {@code feature} open in the branches pane.
     */
    private static Model branchCommitsOpen() {
        BranchRef feature = new BranchRef.Local(new BranchName("feature"));
        Model logRead = Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.BranchSelected(feature))
                                .model(),
                        TestModels.branchLogLoaded(feature, List.of(commit("aaaa"))))
                .model();
        return Update.update(logRead, new BranchCommitsMsg.Opened(feature)).model();
    }

    private static Commit commit(String sha) {
        return Fixtures.commit(new Sha(sha), "Ada", "a@e.com", Instant.EPOCH, sha, "", List.empty());
    }
}
