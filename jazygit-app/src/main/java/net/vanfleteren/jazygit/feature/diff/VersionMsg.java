package net.vanfleteren.jazygit.feature.diff;

import net.vanfleteren.jazygit.state.DiffCursorAt;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface VersionMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return VersionUpdate.update(model, this);
    }

    /**
     * The user asked to show the next of the diff, the new version and the original of the one file of the area the
     * cursor {@code at} is in (ADR 102).
     */
    record Cycled(DiffCursorAt at) implements VersionMsg {}

    /**
     * The user asked to show the version of the one file of the area the cursor {@code at} is in rendered, or as source
     * again (ADR 103).
     */
    record RenderToggled(DiffCursorAt at) implements VersionMsg {}
}
