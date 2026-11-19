package net.vanfleteren.jazygit.content;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.ui.content.Shown;

/**
 * What the content panel shows, for the tests of a feature's content in another package, as the builds of a branch in
 * {@code github.builds}: {@link ContentView} is the content panel's own.
 */
public final class ContentViews {

    private ContentViews() {}

    /**
     * What the content panel shows in {@code model} next to the pane {@code pane}, from the sources of the app.
     */
    public static Shown of(Model model, PaneId pane) {
        return ContentView.of(model, pane);
    }
}
