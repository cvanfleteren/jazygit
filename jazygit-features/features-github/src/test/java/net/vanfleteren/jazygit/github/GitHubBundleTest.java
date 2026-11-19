package net.vanfleteren.jazygit.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.Bundles;
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
        assertEquals(List.empty(), Bundles.missing("github"));
    }
}
