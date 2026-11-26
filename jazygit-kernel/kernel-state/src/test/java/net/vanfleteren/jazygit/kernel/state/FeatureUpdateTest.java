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
            "own",
            List.of(new PaneId("own")),
            Screen.REPOSITORY.width(),
            Screen.REPOSITORY.heights(),
            Option.of(new FeatureId("f")));

    @Test
    void aFeatureIsOnUntilItIsSwitchedOff() {
        assertTrue(loaded().on(new FeatureId("f")));
        assertFalse(FeatureUpdate.off(loaded(), new FeatureId("f")).on(new FeatureId("f")));
        assertTrue(FeatureUpdate.off(loaded(), new FeatureId("f")).on(new FeatureId("g")));
    }

    @Test
    void whatAFeatureThatIsOffSendsDoesNothing() {
        Msg owned = new FeatureMsg.Owned(new FeatureId("f"), new FeatureMsg.Applies(new FeatureId("g"), false));
        Model off = FeatureUpdate.off(loaded(), new FeatureId("f"));

        assertEquals(off, Update.update(off, owned).model());
        assertFalse(Update.update(loaded(), owned).model().on(new FeatureId("g")));
    }

    @Test
    void oneKeyThatFeaturesBindAlikeDoesWhatTheFirstOfThemThatIsOnSends() {
        Msg first = new FeatureMsg.FirstOn(List.of(
                new FeatureMsg.Owned(new FeatureId("f"), new FeatureMsg.Applies(new FeatureId("x"), false)),
                new FeatureMsg.Owned(new FeatureId("g"), new FeatureMsg.Applies(new FeatureId("y"), false))));
        Model fOff = FeatureUpdate.off(loaded(), new FeatureId("f"));
        Model bothOff = FeatureUpdate.off(fOff, new FeatureId("g"));

        Model both = Update.update(loaded(), first).model();
        assertFalse(both.on(new FeatureId("x")));
        assertTrue(both.on(new FeatureId("y")));
        Model onlyG = Update.update(fOff, first).model();
        assertTrue(onlyG.on(new FeatureId("x")));
        assertFalse(onlyG.on(new FeatureId("y")));
        assertEquals(bothOff, Update.update(bothOff, first).model());
    }

    @Test
    void switchedTheFeaturesAreToldAndOffItsScreenGoesBackToTheRepository() {
        Model reactions = loaded().withReactions(new Reactions(List.of(
                (model, event) -> event instanceof KernelEvent.Switched(FeatureId feature, boolean on) && on
                        ? Next.of(model.withScreen(OWN))
                        : Next.of(model))));
        Model off = FeatureUpdate.off(reactions, new FeatureId("f"));

        Model on = Update.update(off, new FeatureMsg.Applies(new FeatureId("f"), true))
                .model();
        assertTrue(on.on(new FeatureId("f")));
        assertEquals(OWN, on.screen());

        Model offAgain = Update.update(on, new FeatureMsg.Applies(new FeatureId("f"), false))
                .model();
        assertFalse(offAgain.on(new FeatureId("f")));
        assertEquals(Screen.REPOSITORY, offAgain.screen());
        // Told it applies as it already does, nothing changes.
        assertEquals(
                on,
                Update.update(on, new FeatureMsg.Applies(new FeatureId("f"), true))
                        .model());
    }

    @Test
    void aTimerOfAFeatureIsKeptWhileAwayAsAnyTimerIs() {
        Model away = Update.focused(loaded(), false).model();

        Model ticked = Update.update(away, new FeatureMsg.Owned(new FeatureId("f"), new Poll(true)))
                .model();

        assertEquals(Screen.REPOSITORY, ticked.screen());
        assertTrue(ticked.jobs().missed().contains(new Poll(true)));
    }

    @Test
    void aTimerOfAFeatureTurnedOffInTheSettingsDoesNothing() {
        Model model = loaded();

        assertEquals(
                model,
                Update.update(model, new FeatureMsg.Owned(new FeatureId("f"), new Poll(false)))
                        .model());
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
