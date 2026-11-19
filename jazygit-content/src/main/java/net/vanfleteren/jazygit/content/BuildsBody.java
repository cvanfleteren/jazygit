package net.vanfleteren.jazygit.content;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.github.builds.BuildsViewMsg;
import net.vanfleteren.jazygit.state.view.BuildsShown;
import net.vanfleteren.jazygit.ui.content.Body;
import net.vanfleteren.jazygit.ui.content.DrawContext;
import net.vanfleteren.jazygit.ui.content.Drawn;

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
     * A click on a tab, on the first line, shows its workflow (ADR 131); one on a build in the chart or the list its
     * jobs on one time line (ADR 133).
     */
    @Override
    public Option<Msg> clicked(int width, int line, int column) {
        Option<Msg> tab =
                line == 0 ? BuildsText.tabAt(shown, width, column).map(BuildsViewMsg.TabClicked::new) : Option.none();
        return tab.orElse(() -> BuildsText.buildAt(shown, width, line, column).map(BuildsViewMsg.BuildClicked::new));
    }
}
