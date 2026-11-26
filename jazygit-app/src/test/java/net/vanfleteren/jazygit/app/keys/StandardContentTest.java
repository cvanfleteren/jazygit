package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileContent;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Divergence;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.commits.BranchCommitsMsg;
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
        return (source instanceof Feature.GatedSource(var feature, ContentSource gated) ? gated : source)
                .getClass()
                .getSimpleName();
    }

    @Test
    void aFileWithoutChangesComesBeforeTheDiffsOfTheFiles() {
        assertEquals(List.of("UnchangedSource", "FileDiffsSource"), answering(unchanged(), Mirrored.FILES));
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

    /**
     * The files pane showing a file without changes, as all files are shown.
     */
    private static Model unchanged() {
        return TestModels.loaded()
                .mapRepoView(view -> view.mapFiles(files -> files.withShown(FilesShown.ALL))
                        .mapFiles(files -> files.withUnchanged(Option.of(new UnchangedNode.File(new Preview<>(
                                new RepoPath("README.md"),
                                new Loadable.Loaded<>(new FileContent.Text(DiffText.of("# Title\n")))))))));
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
