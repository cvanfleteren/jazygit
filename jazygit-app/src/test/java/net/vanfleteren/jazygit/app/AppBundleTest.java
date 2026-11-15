package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.Bundles;
import org.junit.jupiter.api.Test;

/**
 * The bundle of jazygit-app: every key starts with {@code app}, has a text, and is there for the code that names it
 * (ADR 136).
 */
class AppBundleTest {

    @Test
    void everyKeyStartsWithTheRootOfTheModule() {
        assertEquals(List.empty(), Bundles.unprefixed("app"));
    }

    @Test
    void everyKeyHasAText() {
        assertEquals(List.empty(), Bundles.blank("app"));
    }

    @Test
    void everyKeyTheCodeNamesIsInTheBundle() {
        assertEquals(List.empty(), Bundles.missing("app"));
    }

    /**
     * A key is in one bundle only: the views look a key up in the bundle its root names, and a key of the views in
     * their own, so a key in two would show one text where the other was meant.
     */
    @Test
    void noKeyIsInTwoBundles() {
        List<String> roots = List.of("ui", "state", "changes", "branches", "github", "chrome", "app");
        List<String> all = roots.flatMap(Bundles::keys);
        assertEquals(
                List.empty(),
                all.groupBy(key -> key)
                        .filterValues(keys -> keys.size() > 1)
                        .keySet()
                        .toList());
    }

    /**
     * A key of the views starts with no root but that of a module without such a key, so the views, not a module,
     * hold it: only {@code branches.*}, {@code settings.*} and such, which name no module's key.
     */
    @Test
    void noKeyOfTheViewsStartsWithUi() {
        assertEquals(
                List.empty(),
                Bundles.keys("ui").filter(key -> key.startsWith("ui.")).toList());
    }
}
