package net.vanfleteren.jazygit.features.branches.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.BranchBadges;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.KernelSlots;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import org.junit.jupiter.api.Test;

/**
 * The branches pane wakes the app for the times it shows through the kernel's clock slot (ADR 211), with those of the
 * badges the features after it put on its branches.
 */
class BranchesPanesFeatureTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    void theTimesOfTheBadgesAFeatureAfterItGivesWakeTheApp() {
        Instant done = NOW.plusSeconds(90);
        BranchBadges.Badge counting =
                (model, ref) -> new BranchBadges.Shown(List.empty(), now -> now.isBefore(done) ? "1m" : "done");
        // A forge, listed after the panes, gives the badge.
        Fills fills = BranchesPanesFeature.FEATURE
                .fills()
                .andThen(Fills.NONE.with(BranchBadges.KEY, model -> Option.of(counting)));

        // The branches of the sample model are decades old, so their age does not change before it.
        assertEquals(
                Option.of(done),
                ShownTime.next(
                        fills.all(KernelSlots.CLOCKING, TestModels.loaded().withNow(NOW)), NOW));
    }
}
