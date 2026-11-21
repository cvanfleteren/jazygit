package net.vanfleteren.jazygit.kernel.state.preferences;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;

/**
 * What the left of the window shows: the repository, which is the kernel's own, or a screen a feature brings (ADR
 * 168). It is an open value, as a {@link PaneId} is: declared next to its owner, with what the pure code needs of it.
 * What draws it is the owner's too, in the UI.
 *
 * @param id     the id of the screen
 * @param panes  the ids of its panes under the status, top to bottom; it starts with the first
 * @param width  the setting its left column is kept as wide as under
 * @param heights the setting its panes but the last are kept as tall as under, as many as it has panes but one
 * @param feature the id of the feature that brings it, when it is on or off for a repository: the screen is not shown
 *                while it is off (ADR 171)
 */
public record Screen(
        String id,
        List<PaneId> panes,
        SettingKey<Integer> width,
        SettingKey<List<Integer>> heights,
        Option<String> feature) {

    /**
     * The files, branches, commits and stashes of the repository, which the app shows first.
     */
    public static final Screen REPOSITORY = new Screen(
            "repository",
            List.of(PaneId.FILES, PaneId.BRANCHES, PaneId.COMMITS, PaneId.STASH),
            Settings.REPOSITORY_WIDTH,
            Settings.REPOSITORY_HEIGHTS);

    /**
     * A screen of a feature that is always on, or the kernel's.
     */
    public Screen(String id, List<PaneId> panes, SettingKey<Integer> width, SettingKey<List<Integer>> heights) {
        this(id, panes, width, heights, Option.none());
    }

    /**
     * Whether it is shown in {@code model}: its feature is on, if it has one.
     */
    public boolean shown(Model model) {
        return feature.forAll(model::on);
    }

    /**
     * The pane the screen starts with, before any of its panes had the focus.
     */
    public PaneId first() {
        return panes.head();
    }
}
