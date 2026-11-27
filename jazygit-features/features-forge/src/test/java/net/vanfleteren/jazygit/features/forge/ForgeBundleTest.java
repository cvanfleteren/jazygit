package net.vanfleteren.jazygit.features.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.Bundles;
import org.junit.jupiter.api.Test;

/**
 * The bundle of features-forge: every key starts with {@code forge}, has a text, and is there for the code that names
 * it (ADR 136).
 */
class ForgeBundleTest {

    @Test
    void everyKeyStartsWithTheRootOfTheModule() {
        assertEquals(List.empty(), Bundles.unprefixed("forge"));
    }

    @Test
    void everyKeyHasAText() {
        assertEquals(List.empty(), Bundles.blank("forge"));
    }

    @Test
    void everyKeyTheCodeNamesIsInTheBundle() {
        assertEquals(List.empty(), Bundles.missing("forge"));
    }
}
