package net.vanfleteren.jazygit.kernel.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.kernel.state.Carried;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.FeatureSwitches;
import net.vanfleteren.jazygit.kernel.state.FeatureUpdate;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeHome;
import net.vanfleteren.jazygit.kernel.state.forge.ReadForgeChoice;
import org.junit.jupiter.api.Test;

/**
 * A feature as the app runs it, when it is on or off for a repository (ADR 171): off from the opening of a
 * repository until its detection says it applies, and nothing of it runs or shows while it is off.
 */
class FeatureGatedTest {

    private static final Slot<String> KEY = Slot.of("test.key");

    private static final Feature FEATURE = Feature.of()
            .every(Duration.ofMinutes(1), () -> new Probe("tick"))
            .reacting((model, event) -> Next.of(model, new Run("reacted")))
            .filling(KEY, model -> Option.of("filled"))
            .filling(KernelSlots.SPINNING, model -> Option.of(true))
            .applying("f", model -> new Run("detect"));

    @Test
    void aFeatureThatDecidesNothingIsAsItIs() {
        Feature always = Feature.of().filling(KernelSlots.SPINNING, model -> Option.of(true));

        assertSame(always, always.gated());
    }

    @Test
    void openingARepositorySwitchesItOffAndDetectsWhetherItApplies() {
        Next opened = FEATURE.gated().reactions().head().to(TestModels.loaded(), new KernelEvent.RepositoryOpened());

        assertFalse(opened.model().on("f"));
        // What the user chose as the forge before is read too, from which the kernel decides (ADR 177).
        assertEquals(List.of(new ReadForgeChoice(), new Run("detect")), opened.cmds());
    }

    @Test
    void anotherWorktreeOfTheRepositoryKeepsItAsTheForgeDecidedThereAndDoesNotDetectAgain() {
        Model decided = TestModels.loaded()
                .withPart(ForgeHome.PART, ForgeHome.NONE.deciding(Option.none()))
                .withPart(FeatureSwitches.PART, FeatureSwitches.ALL_ON);

        Next opened = FEATURE.gated()
                .reactions()
                .head()
                .to(Carried.from(decided).into(TestModels.loaded()), new KernelEvent.RepositoryOpened());

        assertTrue(opened.model().on("f"));
        assertEquals(List.empty(), opened.cmds());
    }

    @Test
    void whileItIsOffItsReactionsAndSpinnerDoNothing() {
        Feature gated = FEATURE.gated();
        Model on = TestModels.loaded();
        Model off = FeatureUpdate.off(on, "f");
        KernelEvent event = new KernelEvent.RepositoryOpened();

        assertEquals(
                List.of(new Run("reacted")),
                gated.reactions().last().to(on, event).cmds());
        assertEquals(List.empty(), gated.reactions().last().to(off, event).cmds());
        assertEquals(List.of(true), gated.fills().all(KernelSlots.SPINNING, on));
        assertEquals(List.empty(), gated.fills().all(KernelSlots.SPINNING, off));
    }

    @Test
    void whileItIsOffItGivesNothingForTheSlots() {
        Fills fills = FEATURE.gated().fills();
        Model on = TestModels.loaded();

        assertEquals(Option.of("filled"), fills.of(KEY, on));
        assertEquals(Option.none(), fills.of(KEY, FeatureUpdate.off(on, "f")));
    }

    @Test
    void whatItsTimersSendIsItsOwn() {
        assertEquals(
                new FeatureMsg.Owned("f", new Probe("tick")),
                FEATURE.gated().timers().head().msg().get());
    }

    private record Probe(String name) implements Msg {

        @Override
        public Next apply(Model model) {
            return Next.of(model);
        }
    }

    private record Run(String name) implements Cmd {

        @Override
        public Msg run(Env env) {
            return new Probe(name);
        }

        @Override
        public Msg failed(Failure failure) {
            return new Probe(name);
        }
    }
}
