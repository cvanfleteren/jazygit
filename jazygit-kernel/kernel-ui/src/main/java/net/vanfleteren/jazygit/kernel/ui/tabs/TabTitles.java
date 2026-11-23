package net.vanfleteren.jazygit.kernel.ui.tabs;

import dev.tamboui.layout.Rect;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.IntConsumer;

/**
 * The title of a pane with tabs (ADR 185): the names of its tabs, the one shown in bold and underlined, the others
 * dimmed, and which is clicked. The title takes the colour of the border, so they differ in text style only;
 * underlining shows the difference in themes with colours that bold and dim barely change.
 */
public final class TabTitles {

    private static final String SEPARATOR = " │ ";

    private TabTitles() {}

    /**
     * The title of the tabs named {@code names}, of which the one at {@code shown} is shown.
     */
    public static Line line(List<String> names, int shown) {
        List<Span> spans = names.zipWithIndex()
                .map(name -> name._2 == shown
                        ? Span.raw(name._1).bold().underlined()
                        : Span.raw(name._1).dim());
        return Line.from(spans.intersperse(Span.raw(SEPARATOR)).toJavaList());
    }

    /**
     * The index of the tab whose name is {@code column} cells from the start of the title, if any; the separators
     * between them are none.
     */
    public static Option<Integer> at(List<String> names, int column) {
        int separator = Span.raw(SEPARATOR).width();
        List<Integer> widths = names.map(name -> Span.raw(name).width());
        // Where the name of each tab starts: after the names and separators before it.
        List<Integer> starts = widths.init().scanLeft(0, (start, width) -> start + width + separator);
        return widths.zip(starts)
                .zipWithIndex()
                .find(tab -> column >= tab._1._2 && column < tab._1._2 + tab._1._1)
                .map(tab -> tab._2);
    }

    /**
     * Gives {@code chosen} the index of the tab whose name is clicked in the title of a pane drawn in {@code area}.
     * The title starts after the corner of the border.
     */
    public static EventResult clicked(Rect area, MouseEvent event, List<String> names, IntConsumer chosen) {
        return Option.when(
                        event.kind() == MouseEventKind.PRESS && event.isLeftButton() && event.y() == area.top(),
                        event.x() - area.left() - 1)
                .flatMap(column -> at(names, column))
                .map(index -> {
                    chosen.accept(index);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }
}
