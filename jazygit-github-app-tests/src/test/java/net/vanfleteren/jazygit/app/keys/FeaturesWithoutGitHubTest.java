package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashSet;
import net.vanfleteren.jazygit.features.github.GitHubFeature;
import org.junit.jupiter.api.Test;

/**
 * {@code --without github} runs the app as if GitHub were deleted, even with gh on the path.
 */
class FeaturesWithoutGitHubTest {

    @Test
    void gitHubCanBeLeftOut() {
        assertTrue(Features.ids().contains(GitHubFeature.ID));
    }

    @Test
    void leftOutGitHubIsNotRun() {
        assertFalse(Features.without(Features.ALL, HashSet.of(GitHubFeature.ID))
                .exists(feature ->
                        feature.applying().exists(applying -> applying.id().equals(GitHubFeature.ID))));
    }
}
