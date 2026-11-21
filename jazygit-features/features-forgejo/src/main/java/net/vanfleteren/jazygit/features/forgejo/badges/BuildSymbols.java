package net.vanfleteren.jazygit.features.forgejo.badges;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * The symbols of how the builds of a commit went, as GitHub's badges draw its checks (ADR 48, 174): one column wide,
 * told apart by their shape as well as their colour.
 */
public final class BuildSymbols {

    static final String PASSED = "✓";
    static final String FAILED = "✗";
    static final String RUNNING = "●";

    private BuildSymbols() {}

    /**
     * The segment of {@code state}, with a leading space; none for a commit without builds.
     */
    public static Option<LoadableList.Seg> of(CommitStatus.State state) {
        return switch (state) {
            case PASSED -> Option.of(LoadableList.Seg.styled(" " + PASSED, Theme.Classes.CHECKS_PASSED));
            case FAILED -> Option.of(LoadableList.Seg.styled(" " + FAILED, Theme.Classes.CHECKS_FAILED));
            case RUNNING -> Option.of(LoadableList.Seg.styled(" " + RUNNING, Theme.Classes.CHECKS_RUNNING));
            case NONE -> Option.none();
        };
    }
}
