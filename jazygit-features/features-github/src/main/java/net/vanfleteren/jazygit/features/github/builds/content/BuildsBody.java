package net.vanfleteren.jazygit.features.github.builds.content;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.builds.BuildsAction;
import net.vanfleteren.jazygit.features.github.builds.BuildsAt;
import net.vanfleteren.jazygit.features.github.builds.BuildsMsg;
import net.vanfleteren.jazygit.features.github.builds.BuildsShown;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.ui.content.Body;
import net.vanfleteren.jazygit.kernel.ui.content.DrawContext;
import net.vanfleteren.jazygit.kernel.ui.content.Drawn;
import net.vanfleteren.jazygit.kernel.ui.content.KeyContext;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The builds of a branch, of the newest commit first (ADR 131), drawn as wide as the area, as its chart has a column
 * for as many builds as fit.
 */
record BuildsBody(BuildsShown shown, Option<FailedBuild> failed) implements Body {

    /**
     * The builds, without a build whose jobs show on one time line, so nothing to act on as a failed build.
     */
    BuildsBody(BuildsShown shown) {
        this(shown, Option.none());
    }

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
        // The keys other features add to a failed build act on the one whose jobs show on one time line (ADR 161).
        return context.keymap()
                .handle(
                        BuildsAction.SCOPE,
                        event,
                        Option.of(new BuildsAt(context.searching())),
                        new Keymap.Hosted<>(Scopes.FAILED_BUILD, failed));
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
