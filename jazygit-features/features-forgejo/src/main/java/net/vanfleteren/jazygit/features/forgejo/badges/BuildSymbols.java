package net.vanfleteren.jazygit.features.forgejo.badges;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.kernel.ui.widgets.CheckSymbols;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.Symbol;

/**
 * The symbols of how the builds of a commit went, those of the checks of every forge (ADR 48, 174, 175).
 */
public final class BuildSymbols {

    private BuildSymbols() {}

    /**
     * The segment of {@code state}, with a leading space; none for a commit without builds.
     */
    public static Option<LoadableList.Seg> of(CommitStatus.State state) {
        return switch (state) {
            case PASSED -> Option.of(seg(CheckSymbols.PASSED));
            case FAILED -> Option.of(seg(CheckSymbols.FAILED));
            case RUNNING -> Option.of(seg(CheckSymbols.RUNNING));
            case NONE -> Option.none();
        };
    }

    private static LoadableList.Seg seg(Symbol symbol) {
        return LoadableList.Seg.styled(" " + symbol.text(), symbol.cssClass());
    }
}
