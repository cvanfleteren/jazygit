package net.vanfleteren.jazygit.features.forgejo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.Bundles;
import org.junit.jupiter.api.Test;

/**
 * The bundle of features-forgejo: every key starts with {@code forgejo}, has a text, and is there for the code that
 * names it (ADR 136).
 */
class ForgejoBundleTest {

    @Test
    void everyKeyStartsWithTheRootOfTheModule() {
        assertEquals(List.empty(), Bundles.unprefixed("forgejo"));
    }

    @Test
    void everyKeyHasAText() {
        assertEquals(List.empty(), Bundles.blank("forgejo"));
    }

    @Test
    void everyKeyTheCodeNamesIsInTheBundle() {
        assertEquals(List.empty(), Bundles.missing("forgejo"));
    }
}
