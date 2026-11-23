package net.vanfleteren.jazygit.kernel.state.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Landing;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.Reactions;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import org.junit.jupiter.api.Test;

/**
 * The model keeps the tab each pane shows (ADR 185): the keys go round its tabs, choosing one keeps it, and another
 * worktree opens on the same tabs.
 */
class PaneTabsTest {

    private static final TabId LOG = new TabId("test.log");
    private static final TabId SECOND = new TabId("test.second");
    private static final TabId THIRD = new TabId("test.third");
    private static final ShownTabs TABS = new ShownTabs(PaneId.COMMITS, List.of(LOG, SECOND, THIRD), SECOND);

    @Test
    void theKeysGoToTheTabBeforeOrAfterTheOneShownGoingRound() {
        assertEquals(new ViewMsg.TabChosen(PaneId.COMMITS, THIRD), TABS.shifted(1));
        assertEquals(new ViewMsg.TabChosen(PaneId.COMMITS, LOG), TABS.shifted(-1));
        assertEquals(
                new ViewMsg.TabChosen(PaneId.COMMITS, LOG),
                new ShownTabs(PaneId.COMMITS, List.of(LOG, SECOND, THIRD), THIRD).shifted(1));
        assertEquals(
                new ViewMsg.TabChosen(PaneId.COMMITS, THIRD),
                new ShownTabs(PaneId.COMMITS, List.of(LOG, SECOND, THIRD), LOG).shifted(-1));
    }

    @Test
    void aPaneShowsNoTabOfItsOwnChoosingUntilOneIsChosen() {
        Model model = TestModels.loaded(SampleData.git());

        assertEquals(Option.none(), model.repoView().tab(PaneId.COMMITS));
        Model chosen =
                new ViewMsg.TabChosen(PaneId.COMMITS, SECOND).apply(model).model();
        assertEquals(Option.of(SECOND), chosen.repoView().tab(PaneId.COMMITS));
        assertEquals(Option.none(), chosen.repoView().tab(PaneId.STASH));
    }

    @Test
    void theFeaturesAreToldOfATabShownThatWasNot() {
        java.util.List<KernelEvent> told = new java.util.ArrayList<>();
        Model model = TestModels.loaded(SampleData.git()).withReactions(new Reactions(List.of((at, event) -> {
            told.add(event);
            return Update.Next.of(at);
        })));

        Model chosen =
                new ViewMsg.TabChosen(PaneId.COMMITS, SECOND).apply(model).model();
        new ViewMsg.TabChosen(PaneId.COMMITS, SECOND).apply(chosen);

        assertEquals(java.util.List.of(new KernelEvent.TabShown(PaneId.COMMITS, SECOND)), told);
    }

    @Test
    void anotherWorktreeOpensOnTheTabsShown() {
        RepoView shown = RepoView.INITIAL.withTab(PaneId.COMMITS, SECOND);

        assertEquals(HashMap.of(PaneId.COMMITS, SECOND), Landing.of(shown).tabs());
        assertEquals(
                Option.of(SECOND),
                Landing.START.keepingTabs(shown).applyTo(RepoView.INITIAL).tab(PaneId.COMMITS));
        assertEquals(Option.none(), Landing.START.applyTo(shown).tab(PaneId.COMMITS));
    }
}
