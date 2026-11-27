package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchesPane;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPane;
import net.vanfleteren.jazygit.kernel.ui.BranchBadges;
import net.vanfleteren.jazygit.kernel.ui.KernelSlots;
import net.vanfleteren.jazygit.kernel.ui.Slot;
import org.junit.jupiter.api.Test;

/**
 * A {@link Slot} is its name (ADR 203), and what the features give for it is cast to the type it is declared with, so
 * no two slots the app declares may share one: the second would be given what was meant for the first. Every
 * constant of the app's classes that holds a slot counts, whichever module declares it.
 */
class SlotNamesTest {

    private static List<Slot<?>> declared() {
        return Constants.of(Slot.class).map(slot -> slot);
    }

    @Test
    void theSlotsOfTheKernelAndOfTheFeaturesAreFound() {
        assertTrue(declared()
                .containsAll(List.of(KernelSlots.SPINNING, BranchesPane.TABS, CommitsPane.TABS, BranchBadges.KEY)));
    }

    @Test
    void noTwoSlotsShareAName() {
        List<String> names = declared().map(Slot::name);
        assertEquals(names.distinct(), names, "two slots share a name");
    }
}
