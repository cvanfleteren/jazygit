package net.vanfleteren.jazygit.state.load;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.view.BranchCommitsView;
import net.vanfleteren.jazygit.state.view.DivergenceView;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.RepoView;
import org.junit.jupiter.api.Test;

/**
 * Where a load keeps what it reads in the model.
 */
class TargetTest {

    private static final Sha SHA = new Sha("abc123");

    private static final DiffText CHANGES = DiffText.of("diff");

    private static final Failure FAILURE = TestModels.refused("gone");

    private static Model withStashDetail(Model model, Sha sha, Loadable<DiffText> detail) {
        return model.mapRepoView(repoView -> repoView.withStashDetail(Option.of(new Preview<>(sha, detail))));
    }

    private static DiffOptions options(Model model) {
        return model.preferences().diffOptions();
    }

    @Test
    void whatTheRepositoryHoldsStaysWhenReadingItAgainFails() {
        Model loaded = TestModels.loaded();
        Target<?> status = new LoadStatus().target();

        assertEquals(
                loaded.repo().status(), status.failed(loaded, FAILURE).repo().status());
        assertTrue(status.failedIn(status.failed(TestModels.loading("repo"), FAILURE)));
    }

    @Test
    void readingWhatIsAlreadyHeldKeepsTheSameModel() {
        Model loaded = TestModels.loaded();
        LoadStatus status = new LoadStatus();

        assertSame(
                loaded,
                status.target().reload(loaded, loaded.repo().status().toOption().get()));
    }

    @Test
    void whatAViewShowsIsReplacedByAFailure() {
        Model shown = withStashDetail(TestModels.loaded(), SHA, new Loadable.Loaded<>(CHANGES));
        Target<DiffText> detail = new LoadStashDetail(SHA, options(shown)).target();

        Model failed = detail.failed(shown, FAILURE);

        assertEquals(Option.of(new Loadable.Failed<>(FAILURE)), detail.in(failed));
        assertTrue(detail.failedIn(failed));
    }

    @Test
    void whatIsReadForAnotherItemThanTheOneShownIsDropped() {
        Model shown = withStashDetail(TestModels.loaded(), new Sha("other"), Loadable.loading());
        Target<DiffText> detail = new LoadStashDetail(SHA, options(shown)).target();

        assertFalse(detail.wanted(shown));
        assertSame(shown, detail.reload(shown, CHANGES));
        assertSame(shown, detail.failed(shown, FAILURE));
    }

    @Test
    void whatIsReadForACommitAndThatReadingItFailedGoToEveryPreviewOfIt() {
        Model shown = everyPreviewOf(SHA, Loadable.loading());
        Target<DiffText> ofBranch = new LoadCommitDetail(SHA, options(shown), Cmd.Slot.BRANCH_COMMITS_DETAIL).target();

        Model read = ofBranch.reload(shown, CHANGES);
        Model failed = ofBranch.failed(shown, FAILURE);

        assertEquals(List.fill(3, new Loadable.Loaded<>(CHANGES)), previews(read));
        assertEquals(List.fill(3, new Loadable.Failed<>(FAILURE)), previews(failed));
    }

    @Test
    void eachPreviewOfACommitIsReadAgainOnlyWhenItsOwnFailed() {
        Model shown = everyPreviewOf(SHA, new Loadable.Loaded<>(CHANGES));
        Target<DiffText> ofCommits = new LoadCommitDetail(SHA, options(shown)).target();
        Target<DiffText> ofDivergence = new LoadCommitDetail(SHA, options(shown), Cmd.Slot.DIVERGENCE_DETAIL).target();
        Target<DiffText> ofBranch = new LoadCommitDetail(SHA, options(shown), Cmd.Slot.BRANCH_COMMITS_DETAIL).target();
        Option<Preview<Sha, DiffText>> failed = Option.of(new Preview<>(SHA, new Loadable.Failed<>(FAILURE)));

        Model commitsFailed = shown.mapRepoView(repoView -> repoView.withCommitDetail(failed));
        Model divergenceFailed = shown.mapRepoView(
                repoView -> repoView.withDivergence(repoView.divergence().map(view -> view.withDetail(failed))));
        Model branchFailed = shown.mapRepoView(
                repoView -> repoView.withBranchCommits(repoView.branchCommits().map(view -> view.withDetail(failed))));

        assertEquals(
                List.of(true, false, false),
                List.of(ofCommits, ofDivergence, ofBranch).map(target -> target.failedIn(commitsFailed)));
        assertEquals(
                List.of(false, true, false),
                List.of(ofCommits, ofDivergence, ofBranch).map(target -> target.failedIn(divergenceFailed)));
        assertEquals(
                List.of(false, false, true),
                List.of(ofCommits, ofDivergence, ofBranch).map(target -> target.failedIn(branchFailed)));
    }

    /**
     * A model with {@code detail} as the preview of {@code sha} in the commits pane, the divergence and the commits
     * of a branch.
     */
    private static Model everyPreviewOf(Sha sha, Loadable<DiffText> detail) {
        Option<Preview<Sha, DiffText>> preview = Option.of(new Preview<>(sha, detail));
        return TestModels.loaded()
                .mapRepoView(repoView -> repoView.withCommitDetail(preview)
                        .withDivergence(Option.of(DivergenceView.loading(
                                        new BranchName("feature"),
                                        new BranchRef.Local(new BranchName("main")),
                                        DivergenceView.Against.UPSTREAM)
                                .withDetail(preview)))
                        .withBranchCommits(Option.of(
                                new BranchCommitsView(new BranchRef.Local(new BranchName("feature")), preview))));
    }

    /**
     * What the previews of the commits pane, the divergence and the commits of a branch hold, in that order.
     */
    private static List<Loadable<DiffText>> previews(Model model) {
        RepoView repoView = model.repoView();
        return List.of(
                        repoView.commitDetail(),
                        repoView.divergence().flatMap(DivergenceView::detail),
                        repoView.branchCommits().flatMap(BranchCommitsView::detail))
                .map(preview -> preview.get().value());
    }
}
