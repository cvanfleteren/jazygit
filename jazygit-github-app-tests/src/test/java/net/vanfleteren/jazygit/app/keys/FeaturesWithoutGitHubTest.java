package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.GitHubFeature;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import org.junit.jupiter.api.Test;

/**
 * {@code --without github}, passed on as the {@code jazygit.without} property, runs the app as if GitHub were deleted,
 * even with gh on the path: nothing of it is registered.
 */
class FeaturesWithoutGitHubTest {

    private static boolean isGitHub(Feature feature) {
        return feature.applying().exists(applying -> applying.id().equals(GitHubFeature.ID));
    }

    @Test
    void gitHubCanBeLeftOut() {
        assertTrue(Features.ids().contains(GitHubFeature.ID));
    }

    @Test
    void withoutThePropertyGitHubRuns() {
        assertTrue(Features.running(Option.none()).exists(FeaturesWithoutGitHubTest::isGitHub));
    }

    @Test
    void thePropertyLeavesGitHubOut() {
        assertFalse(Features.running(Option.of(GitHubFeature.ID)).exists(FeaturesWithoutGitHubTest::isGitHub));
    }

    @Test
    void leftOutGitHubBringsNoScreen() {
        List<Feature> running = Features.running(Option.of(GitHubFeature.ID));

        assertTrue(GitHubFeature.FEATURE.screens().nonEmpty());
        assertFalse(running.flatMap(Feature::screens)
                .exists(screen -> GitHubFeature.FEATURE.screens().contains(screen)));
    }
}
