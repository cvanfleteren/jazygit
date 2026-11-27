package net.vanfleteren.jazygit.kernel.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * What the features show of a branch, joined as the branches pane shows it (ADR 167); the pane's drawing of them is
 * tested in {@code features-branches}.
 */
class BranchBadgesTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    /**
     * A feature that shows {@code mark} and the name of each local branch, and nothing of a remote one.
     */
    private static BranchBadges.Badge marking(String mark) {
        return (model, ref) -> ref instanceof BranchRef.Local(BranchName name)
                ? BranchBadges.Shown.of(List.of(LoadableList.Seg.of(" " + mark + name.value())))
                : BranchBadges.Shown.NONE;
    }

    @Test
    void theBadgesOfTheFeaturesAreJoinedInTheirOrderAndTheirTimesApart() {
        BranchBadges.Badge timed = (model, ref) -> new BranchBadges.Shown(List.empty(), now -> "soon");
        BranchBadges badges = new BranchBadges(List.of(marking("a:"), timed, marking("b:")));

        BranchBadges.Shown shown = badges.of(TestModels.loaded(), new BranchRef.Local(new BranchName("main")));

        assertEquals(List.of(LoadableList.Seg.of(" a:main"), LoadableList.Seg.of(" b:main")), shown.segs());
        assertEquals("soon", shown.timed().apply(NOW));
        assertEquals(
                "",
                BranchBadges.NONE
                        .of(TestModels.loaded(), new BranchRef.Local(new BranchName("main")))
                        .timed()
                        .apply(NOW));
    }

    @Test
    void theBadgesOfEveryFeatureThatFillsTheKeyAreJoinedInTheirOrder() {
        Fills fills = Fills.NONE
                .with(BranchBadges.KEY, model -> Option.some(marking("a:")))
                .andThen(Fills.NONE.with(BranchBadges.KEY, model -> Option.none()))
                .andThen(Fills.NONE.with(BranchBadges.KEY, model -> Option.some(marking("b:"))));

        BranchBadges.Shown shown =
                BranchBadges.filled(fills).of(TestModels.loaded(), new BranchRef.Local(new BranchName("main")));

        assertEquals(List.of(LoadableList.Seg.of(" a:main"), LoadableList.Seg.of(" b:main")), shown.segs());
    }
}
