package net.vanfleteren.jazygit.features.chrome;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.Bundles;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
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

    // The theme dialog and the settings name a theme by a key made of its id, which the test above cannot see.
    @Test
    void everyThemeHasAName() {
        assertEquals(
                List.empty(),
                List.of(ThemeName.values())
                        .map(theme -> "chrome.dialog.theme." + theme.id())
                        .filter(key -> !Bundles.keys("chrome").contains(key)));
    }
}
