package net.vanfleteren.jazygit.state.view;

import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.Version;

/**
 * What one area of the content panel shows besides what it is given.
 *
 * @param expanded the lines expanded around the hunks of the diff the area shows (ADR 77)
 * @param version  the version of its one file the area shows in place of its diff, while another item was not
 *                 highlighted (ADR 102)
 */
@With
public record AreaView(Option<Expansion> expanded, Option<Version> version) {

    public static final AreaView INITIAL = new AreaView(Option.none(), Option.none());

    /**
     * Whether it keeps nothing.
     */
    public boolean isEmpty() {
        return expanded.isEmpty() && version.isEmpty();
    }
}
