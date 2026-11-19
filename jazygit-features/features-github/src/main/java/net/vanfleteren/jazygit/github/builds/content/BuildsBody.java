package net.vanfleteren.jazygit.github.builds.content;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.github.builds.BuildsAction;
import net.vanfleteren.jazygit.github.builds.BuildsAt;
import net.vanfleteren.jazygit.github.builds.BuildsMsg;
import net.vanfleteren.jazygit.github.builds.BuildsShown;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.content.Body;
import net.vanfleteren.jazygit.ui.content.DrawContext;
import net.vanfleteren.jazygit.ui.content.Drawn;
import net.vanfleteren.jazygit.ui.content.KeyContext;

/**
 * The builds of a branch, of the newest commit first (ADR 131), drawn as wide as the area, as its chart has a column
 * for as many builds as fit.
 */
record BuildsBody(BuildsShown shown) implements Body {

    @Override
    public Drawn draw(DrawContext context) {
        return Drawn.plain(BuildsText.lines(shown, context.zone(), context.styles(), context.width()));
    }

    @Override
    public boolean drawnToWidth() {
        return true;
    }

    /**
     * The builds have keys of their own, to show another workflow, without a cursor (ADR 131).
     */
    @Override
    public Option<Msg> key(KeyEvent event, KeyContext context) {
        return context.keymap().handle(BuildsAction.SCOPE, event, Option.of(new BuildsAt(context.searching())));
    }

    /**
     * A click on a tab, on the first line, shows its workflow (ADR 131); one on a build in the chart or the list its
     * jobs on one time line (ADR 133).
     */
    @Override
    public Option<Msg> clicked(int width, int line, int column) {
        Option<Msg> tab =
                line == 0 ? BuildsText.tabAt(shown, width, column).map(BuildsMsg.TabClicked::new) : Option.none();
        return tab.orElse(() -> BuildsText.buildAt(shown, width, line, column).map(BuildsMsg.BuildClicked::new));
    }
}
