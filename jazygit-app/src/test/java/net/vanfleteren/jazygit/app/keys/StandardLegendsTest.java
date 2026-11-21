package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.features.github.badges.BadgeLegend;
import net.vanfleteren.jazygit.kernel.ui.keys.Legend;
import net.vanfleteren.jazygit.kernel.ui.keys.Legends;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * What the features add to the legends of the panels, as the app gives them (ADR 168).
 */
class StandardLegendsTest {

    @Test
    void theHelpFindsTheLegendsOfTheAppAsAService() {
        assertSame(new StandardLegends().legends(), Legends.standard());
    }

    @Test
    void gitHubSaysWhatItsSymbolsInTheBranchesPaneMean() {
        assertEquals(BadgeLegend.BRANCHES, Legends.standard().of(Scopes.BRANCHES.id()));
        assertEquals(
                List.of(
                        "github.help.branches.legend.pullRequest",
                        "github.help.branches.legend.checks",
                        "github.help.branches.legend.review",
                        "github.help.branches.legend.merge",
                        "github.help.branches.legend.stack"),
                Legends.standard().of(Scopes.BRANCHES.id()).map(Legend::descriptionKey));
        assertEquals(List.empty(), Legends.standard().of(Scopes.FILES.id()));
    }
}
