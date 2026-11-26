package net.vanfleteren.jazygit.features.changes.panes.files;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.kernel.ui.widgets.Pane;

/**
 * What the files pane gives the list it shows, to draw in: whether the pane has the focus and is in use, the filter
 * (ADR 105) and the lock (ADR 149), which the lists of the working tree and of a commit share.
 *
 * @param focused  whether the pane has the focus
 * @param active   whether the pane is in use, so that its highlighted node shows
 * @param shown    the filter the tree shows its files for, if any
 * @param filtered what the title says after what it says already while the files are filtered
 * @param locked   what the title ends in while the pane is locked to a list
 * @param withLine the tree, under the line the filter is typed in while it is open
 */
record ListFrame(
        boolean focused,
        boolean active,
        Option<String> shown,
        String filtered,
        String locked,
        UnaryOperator<Element> withLine) {

    /**
     * The pane, titled {@code title}, around {@code tree}, which highlights its row {@code selectedRow} of
     * {@code count}.
     */
    Panel bordered(String title, Element tree, int selectedRow, Option<Integer> count) {
        return Pane.bordered(title, withLine.apply(tree), focused, selectedRow, count);
    }
}
