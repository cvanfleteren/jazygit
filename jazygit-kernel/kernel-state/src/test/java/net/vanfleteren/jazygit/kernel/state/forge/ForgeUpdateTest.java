package net.vanfleteren.jazygit.kernel.state.forge;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.state.forge.ForgeDecisionTest.candidate;
import static net.vanfleteren.jazygit.kernel.state.forge.ForgeDecisionTest.claim;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.FeatureUpdate;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * The kernel switching on the forge of the home once every forge told its claims, and what the user chose before
 * was read, or asking which it is (ADR 177).
 */
class ForgeUpdateTest {

    /**
     * A repository opening with forges {@code github} and {@code forgejo}, both off until the kernel decides.
     */
    private static Model opened() {
        Model off = FeatureUpdate.off(FeatureUpdate.off(loaded(), "github"), "forgejo");
        return ForgeUpdate.opening(ForgeUpdate.opening(off, "github").model(), "forgejo")
                .model();
    }

    private static Model told(Model model, ForgeClaim... claims) {
        return List.of(claims)
                .foldLeft(
                        model,
                        (told, claim) -> Update.update(told, new FeatureMsg.Claimed(claim))
                                .model());
    }

    private static Model nothingRemembered(Model model) {
        return Update.update(model, new ForgeMsg.Remembered(Option.none())).model();
    }

    @Test
    void openingReadsWhatTheUserChoseBefore() {
        assertEquals(
                List.of(new ReadForgeChoice()),
                ForgeUpdate.opening(loaded(), "github").cmds());
    }

    @Test
    void theForgeOfTheHomeTurnsOnOnceEveryForgeToldAndTheChoiceWasRead() {
        Model githubTold = told(nothingRemembered(opened()), claim("github", "origin"));
        assertFalse(githubTold.on("github"));

        Model decided = told(githubTold, claim("forgejo"));
        assertTrue(decided.on("github"));
        assertFalse(decided.on("forgejo"));
        assertEquals(Option.of(new RemoteName("origin")), ForgeHome.of(decided));
    }

    @Test
    void inDoubtTheUserIsAskedAndNoForgeIsOnUntilThen() {
        Model asked = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo", "codeberg"));

        assertEquals(
                Option.of(new ForgeChoicePopup(
                        List.of(candidate("github", "origin"), candidate("forgejo", "codeberg")),
                        Option.of(candidate("github", "origin")))),
                asked.popup(ForgeChoicePopup.class));
        assertFalse(asked.on("github"));
        assertFalse(asked.on("forgejo"));
    }

    @Test
    void theChoiceTurnsItsForgeOnAndIsKept() {
        Model asked = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo", "codeberg"));

        Next picked = Update.update(asked, new ForgeMsg.Picked(1));
        assertTrue(picked.model().on("forgejo"));
        assertFalse(picked.model().on("github"));
        assertEquals(Option.none(), picked.model().popup(ForgeChoicePopup.class));
        assertEquals(Option.of(new RemoteName("codeberg")), ForgeHome.of(picked.model()));
        assertTrue(picked.cmds().contains(new SaveForgeChoice(Option.of(new RemoteName("codeberg")))));
    }

    @Test
    void choosingNoForgeIsKeptTooAndPuttingItOffIsNot() {
        Model asked = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo", "codeberg"));

        Next none = Update.update(asked, new ForgeMsg.Chosen(Option.none()));
        assertFalse(none.model().on("github"));
        assertTrue(none.cmds().contains(new SaveForgeChoice(Option.none())));

        Next later = Update.update(asked, new ForgeMsg.Later());
        assertEquals(Option.none(), later.model().popup(ForgeChoicePopup.class));
        assertEquals(List.empty(), later.cmds());
        assertFalse(later.model().on("github"));
    }

    @Test
    void choosingAgainFromTheSettingsSwitchesTheForge() {
        Model decided = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo"));

        Model asked = Update.update(decided, new ForgeMsg.Asked()).model();
        assertEquals(
                Option.of(new ForgeChoicePopup(
                        List.of(candidate("github", "origin")), Option.of(candidate("github", "origin")))),
                asked.popup(ForgeChoicePopup.class));

        Model none = Update.update(asked, new ForgeMsg.Chosen(Option.none())).model();
        assertFalse(none.on("github"));
        assertEquals(Option.none(), ForgeHome.of(none));
    }

    @Test
    void aRememberedChoiceIsHonoured() {
        Model remembered = Update.update(
                        opened(), new ForgeMsg.Remembered(Option.of(Option.of(new RemoteName("codeberg")))))
                .model();

        Model decided = told(remembered, claim("github", "origin"), claim("forgejo", "codeberg"));
        assertTrue(decided.on("forgejo"));
        assertEquals(Option.none(), decided.popup(ForgeChoicePopup.class));
    }

    @Test
    void itDecidesOnceAnOpeningAndIgnoresAForgeItDoesNotWaitFor() {
        Model decided = told(nothingRemembered(opened()), claim("github", "origin"), claim("forgejo"));

        Model again = told(decided, claim("forgejo", "codeberg"), claim("other", "x"));
        assertEquals(decided, again);
    }
}
