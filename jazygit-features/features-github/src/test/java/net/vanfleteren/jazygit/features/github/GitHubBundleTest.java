package net.vanfleteren.jazygit.features.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.Bundles;
import net.vanfleteren.jazygit.core.gh.model.Reaction;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import org.junit.jupiter.api.Test;

/**
 * The bundle of features-github: every key starts with {@code github}, has a text, and is there for the code that names it
 * (ADR 136).
 */
class GitHubBundleTest {

    @Test
    void everyKeyStartsWithTheRootOfTheModule() {
        assertEquals(List.empty(), Bundles.unprefixed("github"));
    }

    @Test
    void everyKeyHasAText() {
        assertEquals(List.empty(), Bundles.blank("github"));
    }

    @Test
    void everyKeyTheCodeNamesIsInTheBundle() {
        // The paths of the settings in the settings file, and GitHub's host, which start with the root but are no keys.
        assertEquals(
                List.empty(),
                Bundles.missing("github", HashSet.of("github.executable", "github.browsers", "github.com")));
    }

    // These keys are made of the name of a value, which the test above cannot see: each value needs one.
    @Test
    void everyReasonAReviewIsRefusedHasAText() {
        assertEquals(
                List.empty(),
                unknown(List.of(GitHubError.CannotReview.Why.values())
                        .map(why -> new GitHubError.CannotReview(why).phrase())));
    }

    @Test
    void everyReasonAStackIsNotRebasedHasAText() {
        assertEquals(
                List.empty(),
                unknown(List.of(GitHubError.StackRefusal.values())
                        .map(why -> why.phrase(new GitHubError.StackSubject.Worktree()))));
    }

    @Test
    void everyReactionHasAName() {
        assertEquals(
                List.empty(),
                List.of(Reaction.values())
                        .map(reaction -> "github.dialog.reaction." + reaction.name())
                        .filter(key -> !Bundles.keys("github").contains(key)));
    }

    /**
     * The keys of {@code phrases} that are not in the bundle.
     */
    private static List<String> unknown(List<Phrase> phrases) {
        return phrases.map(phrase -> phrase instanceof Phrase.Keyed(String key, var _) ? key : phrase.toString())
                .filter(key -> !Bundles.keys("github").contains(key));
    }
}
