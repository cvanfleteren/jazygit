package net.vanfleteren.jazygit.feature.layout;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preferences.Dragged;
import net.vanfleteren.jazygit.state.Preferences.DraggedHeights;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * How wide the left column of each screen is, and how tall its panes are, which is kept for the next session. A width
 * or a height that is dragged shows while the user drags, and is kept when they let go.
 */
public final class LayoutUpdate {

    /**
     * How many percent of the width a step makes the left column wider or narrower.
     */
    public static final int STEP = 5;

    private LayoutUpdate() {}

    public static Next update(Model model, LayoutMsg msg) {
        return switch (msg) {
            // The keys and the borders work on the panes, not behind a popup.
            case LayoutMsg.Stepped stepped when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.Resized resized when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.PaneResized resized when model.popup().isDefined() -> Next.of(model);
            // A step ends a drag, of which a release that the terminal did not report would keep the width dragged to.
            case LayoutMsg.Stepped(int by) ->
                keep(
                        model.mapPreferences(preferences -> preferences.withDragged(Option.none())),
                        model.screen(),
                        model.preferences().leftWidth(model.screen()) + by);
            case LayoutMsg.Resized(Screen screen, int width) ->
                Next.of(model.mapPreferences(preferences ->
                        preferences.withDragged(Option.of(new Dragged(screen, Settings.clampWidth(width))))));
            case LayoutMsg.Settled(Screen screen) ->
                model.preferences()
                        .dragged()
                        .filter(drag -> drag.screen() == screen)
                        .map(drag -> keep(
                                model.mapPreferences(preferences -> preferences.withDragged(Option.none())),
                                screen,
                                drag.width()))
                        .getOrElse(() -> Next.of(model));
            case LayoutMsg.PaneResized(Screen screen, int pane, int height) ->
                Next.of(model.mapPreferences(preferences -> preferences.withDraggedHeights(Option.of(new DraggedHeights(
                        screen, Settings.resizedHeights(preferences.leftHeights(screen), pane, height))))));
            case LayoutMsg.PaneSettled(Screen screen) ->
                model.preferences()
                        .draggedHeights()
                        .filter(drag -> drag.screen() == screen)
                        .map(drag -> keep(
                                model.mapPreferences(preferences -> preferences.withDraggedHeights(Option.none())),
                                model.preferences().keepingHeights(screen, drag.heights())))
                        .getOrElse(() -> Next.of(model));
        };
    }

    private static Next keep(Model model, Screen screen, int width) {
        return keep(model, model.preferences().keepingWidth(screen, Settings.clampWidth(width)));
    }

    /**
     * Keeps the {@code chosen} settings, saving them if they changed.
     */
    private static Next keep(Model model, Settings chosen) {
        Settings kept = model.preferences().settings();
        Model shown = model.mapPreferences(preferences -> preferences.withSettings(chosen));
        return chosen.equals(kept) ? Next.of(shown) : new Next(shown, List.of(new Cmd.SaveSettings(kept, chosen)));
    }
}
