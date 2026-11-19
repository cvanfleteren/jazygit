package net.vanfleteren.jazygit.ui.content;

import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.settings.DiffStyle;

/**
 * A diff an area shows, {@code diff}, drawn as {@code look} says.
 */
public record ShownChanges(ShownDiff diff, DiffLook look) {

    /**
     * These changes with their diff changed by {@code change}.
     */
    public ShownChanges withDiff(UnaryOperator<ShownDiff> change) {
        return new ShownChanges(change.apply(diff), look);
    }

    /**
     * Whether these changes are drawn for the width of their area, so drawn anew when that changes: with the old and
     * the new file side by side, each half of the area (ADR 139), or as a version of their file rendered (ADR 103), or
     * blamed, whose blame is shortened in a narrow area (ADR 147).
     */
    public boolean drawnToWidth() {
        return look.style() == DiffStyle.SPLIT
                || diff.version()
                        .exists(shown ->
                                shown.rendering().isDefined() || shown.blaming().isDefined());
    }
}
