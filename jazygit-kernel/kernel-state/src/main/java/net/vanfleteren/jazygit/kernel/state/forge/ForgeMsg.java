package net.vanfleteren.jazygit.kernel.state.forge;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * What happens to the choice of the forge of a repository (ADR 177).
 */
public sealed interface ForgeMsg extends Msg {

    @Override
    default Next apply(Model model) {
        return ForgeUpdate.update(model, this);
    }

    /**
     * What the user chose before was read: {@code chosen}.
     */
    record Remembered(ForgeChoice chosen) implements ForgeMsg {}

    /**
     * The user chose {@code home}, or no forge.
     */
    record Chosen(Option<ForgeCandidate> home) implements ForgeMsg {}

    /**
     * The user chose the {@code index}th remote the open choice lists.
     */
    record Picked(int index) implements ForgeMsg {}

    /**
     * The user put the choice off: no forge until the repository is opened again.
     */
    record Later() implements ForgeMsg {}

    /**
     * The user asked to choose again, from the settings.
     */
    record Asked() implements ForgeMsg {}
}
