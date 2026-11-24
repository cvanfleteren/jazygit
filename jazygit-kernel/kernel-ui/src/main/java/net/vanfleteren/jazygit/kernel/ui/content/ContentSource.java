package net.vanfleteren.jazygit.kernel.ui.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * One kind of thing the content panel shows, such as the log of a branch or what a stash stashed, and the areas it
 * shows it in (ADR 154). The content panel asks the sources in order and shows what the first that answers gives; a
 * feature contributes its own through its {@code Feature}.
 */
public interface ContentSource {

    /**
     * What the content panel shows in {@code model} next to the pane {@code pane}, if this source shows it; none when
     * it is not this source's to show, and the next one is asked.
     */
    Option<Shown> shown(Model model, Mirrored pane);

    /**
     * What this source puts above what the content panel shows in {@code model} next to {@code pane}, whichever source
     * shows it (ADR 187); none, as for most sources.
     */
    default Option<Heading> heading(Model model, Mirrored pane) {
        return Option.none();
    }

    /**
     * The areas this source shows what it shows in: each can have the focus, and keeps where it is scrolled to.
     */
    List<ContentArea> areas();
}
