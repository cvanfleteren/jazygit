package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSources;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import org.junit.jupiter.api.Test;

class LauncherTest {

    @Test
    void featuresThatCanBeLeftOutStartTheApp() {
        assertEquals(Option.none(), Launcher.without(LinkedHashSet.of("forge", "agent"), LinkedHashSet.of("forge")));
    }

    @Test
    void leavingNothingOutStartsTheApp() {
        assertEquals(Option.none(), Launcher.without(LinkedHashSet.empty(), LinkedHashSet.empty()));
    }

    @Test
    void anUnknownFeatureSaysWhichCanBeLeftOut() {
        assertEquals(
                Option.of("No feature to run without is called gitlab; expected one of: forge, agent"),
                Launcher.without(LinkedHashSet.of("forge", "agent"), LinkedHashSet.of("forge", "gitlab")));
    }

    /**
     * As in a build without GitHub, where {@code --without github} names nothing there is.
     */
    @Test
    void aBuildWithNothingToLeaveOutSaysSo() {
        assertEquals(
                Option.of(
                        "No feature to run without is called github: this build has no features that can be left out"),
                Launcher.without(LinkedHashSet.empty(), LinkedHashSet.of("github")));
    }

    @Test
    void theAppStartsWithTheKeymapOfItsFeatures() {
        assertEquals(Either.right(Keymap.standard()), Launcher.keymap(Keymap.standard()));
    }

    /**
     * Without the service that gives the keymap, the app would start with no key that works; it says why instead.
     */
    @Test
    void theAppDoesNotStartWithoutAKeymap() {
        Either<String, Keymap> found = Launcher.keymap(Keymap.empty());

        assertTrue(found.isLeft());
        assertTrue(found.getLeft().contains(Keymap.Source.class.getName()), found.getLeft());
    }

    @Test
    void theAppStartsWithTheContentOfItsFeatures() {
        assertEquals(Either.right(ContentSources.standard()), Launcher.contents(ContentSources.standard()));
    }

    /**
     * Without the service that gives what the content panel shows, it would show only that it is loading; the app
     * says why instead (ADR 154).
     */
    @Test
    void theAppDoesNotStartWithoutTheContentSources() {
        Either<String, ContentSources> found = Launcher.contents(new ContentSources(List.empty()));

        assertTrue(found.isLeft());
        assertTrue(found.getLeft().contains(ContentSources.Provider.class.getName()), found.getLeft());
    }
}
