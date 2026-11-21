package net.vanfleteren.jazygit.kernel.state;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
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
}
