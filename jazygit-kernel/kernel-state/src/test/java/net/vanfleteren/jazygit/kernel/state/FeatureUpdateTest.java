package net.vanfleteren.jazygit.kernel.state;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import org.junit.jupiter.api.Test;

/**
 * A feature on or off for a repository (ADR 171).
 */
class FeatureUpdateTest {

    private static final Screen OWN = new Screen(
            "own", List.of(new PaneId("own")), Screen.REPOSITORY.width(), Screen.REPOSITORY.heights(), Option.of("f"));

    @Test
    void aFeatureIsOnUntilItIsSwitchedOff() {
        assertTrue(loaded().on("f"));
        assertFalse(FeatureUpdate.off(loaded(), "f").on("f"));
        assertTrue(FeatureUpdate.off(loaded(), "f").on("g"));
    }

    @Test
    void whatAFeatureThatIsOffSendsDoesNothing() {
        Msg owned = new FeatureMsg.Owned("f", new FeatureMsg.Applies("g", false));
        Model off = FeatureUpdate.off(loaded(), "f");

        assertEquals(off, Update.update(off, owned).model());
        assertFalse(Update.update(loaded(), owned).model().on("g"));
    }

    @Test
    void switchedTheFeaturesAreToldAndOffItsScreenGoesBackToTheRepository() {
        Model reactions = loaded().withReactions(new Reactions(List.of(
                (model, event) -> event instanceof KernelEvent.Switched(String feature, boolean on) && on
                        ? Next.of(model.withScreen(OWN))
                        : Next.of(model))));
        Model off = FeatureUpdate.off(reactions, "f");

        Model on = Update.update(off, new FeatureMsg.Applies("f", true)).model();
        assertTrue(on.on("f"));
        assertEquals(OWN, on.screen());

        Model offAgain = Update.update(on, new FeatureMsg.Applies("f", false)).model();
        assertFalse(offAgain.on("f"));
        assertEquals(Screen.REPOSITORY, offAgain.screen());
        // Told it applies as it already does, nothing changes.
        assertEquals(on, Update.update(on, new FeatureMsg.Applies("f", true)).model());
    }

    @Test
    void aTimerOfAFeatureIsKeptWhileAwayAsAnyTimerIs() {
        Model away = Update.focused(loaded(), false).model();

        Model ticked =
                Update.update(away, new FeatureMsg.Owned("f", new Poll(true))).model();

        assertEquals(Screen.REPOSITORY, ticked.screen());
        assertTrue(ticked.jobs().missed().contains(new Poll(true)));
    }

    @Test
    void aTimerOfAFeatureTurnedOffInTheSettingsDoesNothing() {
        Model model = loaded();

        assertEquals(
                model,
                Update.update(model, new FeatureMsg.Owned("f", new Poll(false))).model());
    }

    /**
     * What a feature's timer sends: it shows the feature's screen, unless it is turned off in the settings.
     */
    private record Poll(boolean enabled) implements Msg.Periodic {

        @Override
        public Duration whileAway() {
            return Duration.ofMinutes(5);
        }

        @Override
        public boolean on(Settings settings) {
            return enabled;
        }

        @Override
        public Next apply(Model model) {
            return Next.of(model.withScreen(OWN));
        }
    }
}
