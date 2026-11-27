package net.vanfleteren.jazygit.kernel.ui.screen;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.state.Freshness;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * A screen, as the UI draws it (ADR 168): what the left column shows under the status while it is shown, and what the
 * rest of the window says of it. A feature adds one with
 * {@link net.vanfleteren.jazygit.kernel.ui.Feature#screen}; the repository screen is the kernel's. The panes of either
 * are brought by the features, each with {@code Feature.pane} (ADR 211). The content panel mirrors the highlighted item
 * of the pane of the screen in use, and the screen gets its focus back when it is shown again.
 *
 * @param screen      the screen, with its panes and the settings of its layout
 * @param nameKey     the message key of its name in the title of the status
 * @param mark        what follows its name in the title of the status, if anything, as how many of what it lists are
 *                    new
 * @param freshness   how fresh what it shows is, for the fetch line
 * @param freshnessKey the root of the message keys of its freshness: {@code .ago} says when it was read,
 *                    {@code .failed} that the last try failed
 * @param times       the times its panes show, which wake the app to draw them again as they change (ADR 124)
 */
public record ScreenSpec(
        Screen screen,
        String nameKey,
        Function<Model, Option<LoadableList.Seg>> mark,
        Function<Model, Freshness> freshness,
        String freshnessKey,
        Function<Model, List<ShownTime>> times) {

    /**
     * The repository screen, whose fetch line says when the remotes were fetched from last.
     */
    public static final ScreenSpec REPOSITORY = new ScreenSpec(
            Screen.REPOSITORY,
            "screen.repository",
            model -> Option.none(),
            model -> model.jobs().lastFetch(),
            "status.fetch",
            model -> List.empty());
}
