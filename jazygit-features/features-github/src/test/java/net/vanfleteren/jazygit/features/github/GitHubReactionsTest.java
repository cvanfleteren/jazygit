package net.vanfleteren.jazygit.features.github;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Events;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.github.prs.LoadMyPullRequests;
import net.vanfleteren.jazygit.kernel.state.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.kernel.state.github.prs.PrOverview;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import org.junit.jupiter.api.Test;

/**
 * What GitHub's pull requests do when something happens in the kernel: they are the feature's own, so a model of
 * another feature, or none, reads nothing from GitHub (ADR 158).
 */
class GitHubReactionsTest {

    private static final LoadRepositoryPullRequests PULL_REQUESTS = new LoadRepositoryPullRequests();

    private static Model withTheFeature() {
        return PullRequestFixtures.loadedWithTheFeature();
    }

    private static List<Cmd> readsAfter(Model model, KernelEvent event) {
        return Events.happened(Next.of(model), event).cmds();
    }

    @Test
    void anOperationThatChangedWhatGitHubHasMakesThePullRequestsBeReadAgain() {
        assertTrue(readsAfter(withTheFeature(), new KernelEvent.OperationDone(true))
                .contains(PULL_REQUESTS));
    }

    @Test
    void anOperationThatChangedNothingThereReadsNothingFromGitHub() {
        assertEquals(List.of(), readsAfter(withTheFeature(), new KernelEvent.OperationDone(false)));
    }

    @Test
    void showingThePullRequestsScreenReadsWhatItShowsAndShowingTheRepositoryDoesNot() {
        // GitHub's feature declares the answer of its screen (ADR 168).
        assertTrue(readsAfter(withTheFeature(), new KernelEvent.ScreenShown(PrOverview.SCREEN))
                .containsAll(List.of(new LoadMyPullRequests(), PULL_REQUESTS)));
        assertEquals(List.of(), readsAfter(withTheFeature(), new KernelEvent.ScreenShown(Screen.REPOSITORY)));
    }

    @Test
    void aFetchMakesThePullRequestsBeReadAgain() {
        assertTrue(readsAfter(withTheFeature(), new KernelEvent.Fetched()).contains(PULL_REQUESTS));
    }

    @Test
    void theOpeningOfARepositoryMakesThePullRequestsBeReadFromTheStart() {
        assertTrue(
                readsAfter(withTheFeature(), new KernelEvent.RepositoryOpened()).contains(PULL_REQUESTS));
    }

    @Test
    void withoutTheFeatureNoneOfThemReadsFromGitHub() {
        assertEquals(List.of(), readsAfter(loaded(), new KernelEvent.OperationDone(true)));
        assertEquals(List.of(), readsAfter(loaded(), new KernelEvent.Fetched()));
        assertEquals(List.of(), readsAfter(loaded(), new KernelEvent.RepositoryOpened()));
    }

    @Test
    void theHeadLogBeingReadAgainIsNoneOfItsConcern() {
        assertEquals(List.of(), readsAfter(withTheFeature(), new KernelEvent.LogRead()));
    }
}
