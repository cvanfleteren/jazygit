package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import java.nio.file.Path;
import net.vanfleteren.jazygit.Bundles;
import org.junit.jupiter.api.Test;

/**
 * The bundle of jazygit-app: every key starts with {@code app}, has a text, and is there for the code that names it
 * (ADR 136).
 */
class AppBundleTest {

    // A host, the paths of the GitHub settings in the settings file, and the titles of ReviewDialog, which finish as
    // ...titleLine, ...titleEditFile and so on.
    private static final Set<String> NOT_KEYS = HashSet.of(
            "github.com",
            "github.executable",
            "github.browsers",
            "github.dialog.review.title",
            "github.dialog.review.titleEdit");

    private static final List<String> ROOTS =
            // GitHub's and Forgejo's, unless they were deleted (ADR 171).
            List.of("ui", "state", "content", "changes", "branches", "github", "forgejo", "chrome", "app")
                    .filter(Bundles::exists);

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
     * Every key the code of any module names is found where {@code Messages} looks for it: the test of each module
     * checks only the keys that start with its root, so this is the check of the keys of the views, which start with
     * none, wherever they are named.
     */
    @Test
    void everyKeyTheCodeOfAnyModuleNamesIsFound() {
        assertEquals(List.empty(), Bundles.unknown(Path.of(".."), ROOTS, NOT_KEYS));
    }

    /**
     * Every module's bundle is kept by the native image, which keeps only the bundles its metadata lists: one left out
     * shows each of its keys as {@code !key!} there, though the JVM finds them.
     */
    @Test
    void everyBundleIsKeptByTheNativeImage() {
        assertEquals(List.empty(), Bundles.unregistered(Path.of("..")));
    }

    /**
     * A key is in one bundle only: the views look a key up in the bundle its root names, and a key of the views in
     * their own, so a key in two would show one text where the other was meant.
     */
    @Test
    void noKeyIsInTwoBundles() {
        List<String> all = ROOTS.flatMap(Bundles::keys);
        assertEquals(
                List.empty(),
                all.groupBy(key -> key)
                        .filterValues(keys -> keys.size() > 1)
                        .keySet()
                        .toList());
    }

    /**
     * A help text's details are in the bundle of the text they detail: the help looks up {@code <key>.details} next to
     * {@code <key>}, so details left behind when the text moved to another bundle are never shown.
     */
    @Test
    void everyHelpDetailsIsInTheBundleOfTheTextItDetails() {
        String details = ".details";
        assertEquals(
                List.empty(),
                ROOTS.flatMap(root -> {
                            Set<String> keys = Bundles.keys(root);
                            return keys.filter(key -> key.contains(".help.") && key.endsWith(details))
                                    .filter(key -> !keys.contains(key.substring(0, key.length() - details.length())));
                        })
                        .sorted());
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
