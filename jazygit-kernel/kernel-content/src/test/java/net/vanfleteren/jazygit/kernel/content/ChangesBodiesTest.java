package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Stash;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.diff.Filtering;
import net.vanfleteren.jazygit.kernel.ui.content.Body;
import net.vanfleteren.jazygit.kernel.ui.content.ShownChanges;
import net.vanfleteren.jazygit.kernel.ui.content.ShownDiff;
import org.junit.jupiter.api.Test;

/**
 * What each body that shows changes says of them: the diff, the commit and the stash alike, as nothing but these
 * tests makes them answer the same (ADR 152).
 */
class ChangesBodiesTest {

    private static final List<Function<ShownChanges, Body>> BODIES = List.of(
            DiffBody::new,
            changes -> new CommitBody(
                    Fixtures.commit(new Sha("cccc"), "Ada", "a@e.com", Instant.EPOCH, "cccc", ""), changes),
            changes -> new StashBody(
                    new Stash("stash@{0}", new Sha("5555"), "my work", Option.none(), Instant.EPOCH), changes));

    private static ShownChanges changes(DiffStyle style) {
        return new ShownChanges(
                new ShownDiff(DiffText.of("+a\n"), Option.none()), new DiffLook(style, true, true, true));
    }

    @Test
    void eachShowsItsChanges() {
        BODIES.forEach(body -> assertEquals(
                Option.of(changes(DiffStyle.RICH)),
                body.apply(changes(DiffStyle.RICH)).changesShown()));
    }

    @Test
    void eachIsDrawnToTheWidthOfItsAreaWhenItsDiffIsSplit() {
        BODIES.forEach(body -> {
            assertTrue(body.apply(changes(DiffStyle.SPLIT)).drawnToWidth());
            assertFalse(body.apply(changes(DiffStyle.RICH)).drawnToWidth());
        });
    }

    @Test
    void eachHasItsDiffChangedAndStaysTheKindOfBodyItIs() {
        Filtering hiding = Filtering.NONE.withOff(true);
        BODIES.forEach(body -> {
            Body before = body.apply(changes(DiffStyle.RICH));
            Body after = before.withDiff(diff -> diff.filtered(hiding));

            assertEquals(before.getClass(), after.getClass());
            assertEquals(
                    Option.of(hiding),
                    after.changesShown().map(shown -> shown.diff().filtering()));
        });
    }
}
