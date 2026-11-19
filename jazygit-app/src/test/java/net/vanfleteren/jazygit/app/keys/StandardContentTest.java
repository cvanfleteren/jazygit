package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.branches.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Divergence;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.github.builds.BuildsMsg;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.DivergenceView;
import net.vanfleteren.jazygit.state.view.FilesShown;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;
import net.vanfleteren.jazygit.ui.content.ContentSource;
import net.vanfleteren.jazygit.ui.content.ContentSources;
import net.vanfleteren.jazygit.ui.content.Mirrored;
import org.junit.jupiter.api.Test;

/**
 * Which of the app's sources answer for a pane, in the order they are asked: the first of them is what the content
 * panel shows (ADR 154). Nothing else checks that two sources do not both answer, so a source that answers for more
 * than it should, or a change to the order, shows here rather than as content that no longer shows.
 */
class StandardContentTest {

    private static final ContentSources SOURCES = new StandardContent().sources();

    private static final BranchName MAIN = new BranchName("main");

    /**
     * The names of the sources that answer for {@code pane} in {@code model}, in the order they are asked.
     */
    private static List<String> answering(Model model, Mirrored pane) {
        return SOURCES.sources()
                .filter(source -> source.shown(model, pane).isDefined())
                .map(StandardContentTest::name);
    }

    private static String name(ContentSource source) {
        return source.getClass().getSimpleName();
    }

    @Test
    void theAppAsksTheSourcesOfTheFeaturesBeforeTheContentPanelsOwn() {
        assertEquals(
                List.of(
                        "PullRequestSource",
                        "CommitFilesSource",
                        "UnchangedSource",
                        "FileDiffsSource",
                        "DivergenceSource",
                        "BranchCommitsSource",
                        "BuildsSource",
                        "BranchLogSource",
                        "CommitSource",
                        "StashSource"),
                SOURCES.sources().map(StandardContentTest::name));
    }

    @Test
    void eachPaneHasOneSourceOfItsOwn() {
        Model model = TestModels.loaded();

        assertEquals(List.of("FileDiffsSource"), answering(model, Mirrored.FILES));
        assertEquals(List.of("BranchLogSource"), answering(model, Mirrored.BRANCHES));
        assertEquals(List.of("CommitSource"), answering(model, Mirrored.COMMITS));
        assertEquals(List.of("StashSource"), answering(model, Mirrored.STASH));
        assertEquals(List.of("PullRequestSource"), answering(model, Mirrored.PULL_REQUESTS));
    }

    @Test
    void aFileWithoutChangesComesBeforeTheDiffsOfTheFiles() {
        assertEquals(List.of("UnchangedSource", "FileDiffsSource"), answering(unchanged(), Mirrored.FILES));
    }

    @Test
    void theBuildsOfABranchComeBeforeItsLog() {
        assertEquals(List.of("BuildsSource", "BranchLogSource"), answering(buildsOpen(), Mirrored.BRANCHES));
    }

    @Test
    void aDivergenceComesBeforeTheLog() {
        assertEquals(List.of("DivergenceSource", "BranchLogSource"), answering(diverged(), Mirrored.BRANCHES));
    }

    @Test
    void theCommitsOfABranchComeBeforeItsLog() {
        assertEquals(
                List.of("BranchCommitsSource", "BranchLogSource"), answering(branchCommitsOpen(), Mirrored.BRANCHES));
    }

    @Test
    void noSourceOfAPaneAnswersForAnother() {
        List<Model> models = List.of(TestModels.loaded(), unchanged(), buildsOpen(), diverged(), branchCommitsOpen());
        List<Mirrored> panes = List.of(Mirrored.values());

        models.forEach(model -> panes.forEach(pane -> assertEquals(
                List.empty(),
                answering(model, pane).filter(source -> !ownSources(pane).contains(source)),
                pane.name())));
    }

    /**
     * The sources that may answer for {@code pane}.
     */
    private static List<String> ownSources(Mirrored pane) {
        return switch (pane) {
            case FILES -> List.of("CommitFilesSource", "UnchangedSource", "FileDiffsSource");
            case BRANCHES -> List.of("DivergenceSource", "BranchCommitsSource", "BuildsSource", "BranchLogSource");
            case COMMITS -> List.of("CommitSource");
            case STASH -> List.of("StashSource");
            case PULL_REQUESTS -> List.of("PullRequestSource");
        };
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
