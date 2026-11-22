package net.vanfleteren.jazygit.kernel.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.FeatureUpdate;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.forge.ReadForgeChoice;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchBadges;
import org.junit.jupiter.api.Test;

/**
 * A feature as the app runs it, when it is on or off for a repository (ADR 171): off from the opening of a
 * repository until its detection says it applies, and nothing of it runs or shows while it is off.
 */
class FeatureGatedTest {

    private static final BranchRef MAIN = new BranchRef.Local(new BranchName("main"));
    private static final BranchBadges.Shown BADGE = new BranchBadges.Shown(List.empty(), now -> "badge");
    private static final PaneKey<String> KEY = PaneKey.of("test.key", String.class);

    private static final Feature FEATURE = Feature.of()
            .every(Duration.ofMinutes(1), () -> new Probe("tick"))
            .reacting((model, event) -> Next.of(model, new Run("reacted")))
            .badging((model, branch) -> BADGE)
            .filling(KEY, model -> Option.of("filled"))
            .spinning(model -> true)
            .applying("f", model -> new Run("detect"));

    @Test
    void aFeatureThatDecidesNothingIsAsItIs() {
        Feature always = Feature.of().spinning(model -> true);

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
    void whileItIsOffItsReactionsBadgesAndSpinnerDoNothing() {
        Feature gated = FEATURE.gated();
        Model on = TestModels.loaded();
        Model off = FeatureUpdate.off(on, "f");
        KernelEvent event = new KernelEvent.RepositoryOpened();

        assertEquals(
                List.of(new Run("reacted")),
                gated.reactions().last().to(on, event).cmds());
        assertEquals(List.empty(), gated.reactions().last().to(off, event).cmds());
        assertSame(BADGE, gated.badges().head().of(on, MAIN));
        assertSame(BranchBadges.Shown.NONE, gated.badges().head().of(off, MAIN));
        assertTrue(gated.spinning().test(on));
        assertFalse(gated.spinning().test(off));
    }

    @Test
    void whileItIsOffItGivesNothingForTheKeysOfThePanes() {
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
