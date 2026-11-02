package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
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
        Target<?> status = new Cmd.LoadStatus().target();

        assertEquals(
                loaded.repo().status(), status.failed(loaded, FAILURE).repo().status());
        assertTrue(status.failedIn(status.failed(TestModels.loading("repo"), FAILURE)));
    }

    @Test
    void readingWhatIsAlreadyHeldKeepsTheSameModel() {
        Model loaded = TestModels.loaded();
        Cmd.LoadStatus status = new Cmd.LoadStatus();

        assertSame(
                loaded,
                status.target().reload(loaded, loaded.repo().status().toOption().get()));
    }

    @Test
    void whatAViewShowsIsReplacedByAFailure() {
        Model shown = withStashDetail(TestModels.loaded(), SHA, new Loadable.Loaded<>(CHANGES));
        Target<DiffText> detail = new Cmd.LoadStashDetail(SHA, options(shown)).target();

        Model failed = detail.failed(shown, FAILURE);

        assertEquals(Option.of(new Loadable.Failed<>(FAILURE)), detail.in(failed));
        assertTrue(detail.failedIn(failed));
    }

    @Test
    void whatIsReadForAnotherItemThanTheOneShownIsDropped() {
        Model shown = withStashDetail(TestModels.loaded(), new Sha("other"), Loadable.loading());
        Target<DiffText> detail = new Cmd.LoadStashDetail(SHA, options(shown)).target();

        assertFalse(detail.wanted(shown));
        assertSame(shown, detail.reload(shown, CHANGES));
        assertSame(shown, detail.failed(shown, FAILURE));
    }

    @Test
    void theChangesOfACommitGoToEveryPreviewOfItButEachPreviewFailsOnItsOwn() {
        Model shown = TestModels.loaded()
                .mapRepoView(repoView -> repoView.withCommitDetail(Option.of(new Preview<>(SHA, Loadable.loading())))
                        .withBranchCommits(Option.of(new BranchCommitsView(
                                new BranchRef.Local(new BranchName("feature")),
                                Option.of(new Preview<>(SHA, Loadable.loading()))))));
        Cmd.LoadCommitDetail ofBranch = new Cmd.LoadCommitDetail(SHA, options(shown), Cmd.Slot.BRANCH_COMMITS_DETAIL);
        Cmd.LoadCommitDetail ofCommits = new Cmd.LoadCommitDetail(SHA, options(shown));

        Model read = ofBranch.target().reload(shown, CHANGES);

        assertEquals(
                Option.of(new Loadable.Loaded<>(CHANGES)), ofBranch.target().in(read));
        assertEquals(
                Option.of(new Loadable.Loaded<>(CHANGES)), ofCommits.target().in(read));

        Model branchFailed = read.mapRepoView(repoView -> repoView.withBranchCommits(repoView.branchCommits()
                .map(view -> view.withDetail(Option.of(new Preview<>(SHA, new Loadable.Failed<>(FAILURE)))))));

        assertTrue(ofBranch.target().failedIn(branchFailed));
        assertFalse(ofCommits.target().failedIn(branchFailed));
    }
}
