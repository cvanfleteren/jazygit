package net.vanfleteren.jazygit.chrome;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.Bundles;
import org.junit.jupiter.api.Test;

/**
 * The bundle of features-chrome: every key starts with {@code chrome}, has a text, and is there for the code that names it
 * (ADR 136).
 */
class ChromeBundleTest {

    @Test
    void everyKeyStartsWithTheRootOfTheModule() {
        assertEquals(List.empty(), Bundles.unprefixed("chrome"));
    }

    @Test
    void everyKeyHasAText() {
        assertEquals(List.empty(), Bundles.blank("chrome"));
    }

    @Test
    void everyKeyTheCodeNamesIsInTheBundle() {
        assertEquals(List.empty(), Bundles.missing("chrome"));
    }
}
