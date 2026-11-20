package net.vanfleteren.jazygit.kernel.state.settings;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * What a section of the settings panel reads as the panel opens, as how gh is set up; the outcome goes to its
 * {@link SectionState} (ADR 163).
 */
@FunctionalInterface
public interface SectionReads {

    /**
     * Reads nothing.
     */
    SectionReads NONE = model -> List.empty();

    /**
     * The commands that read what the section shows, given the model as the panel opens.
     */
    List<Cmd> on(Model model);
}
