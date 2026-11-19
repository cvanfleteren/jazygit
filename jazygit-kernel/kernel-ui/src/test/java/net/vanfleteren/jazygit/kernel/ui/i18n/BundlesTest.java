package net.vanfleteren.jazygit.kernel.ui.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.vavr.collection.HashSet;
import net.vanfleteren.jazygit.Bundles;
import org.junit.jupiter.api.Test;

/**
 * The bundle checks of the tests find a module's bundle in its group (ADR 159), and a name has its keys in one module
 * only: with a bundle in two groups, the check of either could read the other's.
 */
class BundlesTest {

    @Test
    void aModulesBundleIsFoundInItsGroup() {
        assertEquals(HashSet.of("probekernel.greeting"), Bundles.keys("probekernel"));
        assertEquals(HashSet.of("probefeature.greeting"), Bundles.keys("probefeature"));
    }

    @Test
    void aNameWithABundleInTwoGroupsIsRefused() {
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> Bundles.keys("probeboth"));
        assertEquals("probeboth has a bundle in more than one group: kernel., features.", refused.getMessage());
    }
}
