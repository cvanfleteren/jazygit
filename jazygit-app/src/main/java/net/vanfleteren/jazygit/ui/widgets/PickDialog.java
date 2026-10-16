package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.layout.Constraint;
import dev.tamboui.style.Style;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.ui.Theme;

/**
 * The standard popup to pick one of a list of rows that comes from the open popup, like the branches. A row is
 * picked with the arrow keys and Enter; Escape cancels. A row can have several cells, shown in columns as wide as
 * their widest cell; a cell longer than its cap is cut, and when one of the highlighted row is, the row shows in
 * full in a box under the popup. When there are no rows, it can say so in a dimmed line, which cannot be picked.
 * Whether the popup is open lives in the {@link Model}, as a popup of type {@code P}.
 *
 * @param <P> the popup that makes this dialog show
 * @param <T> what a row stands for, which picking it sends
 */
public final class PickDialog<P extends Popup, T> implements PopupView {

    private static final String HINT = "dialog.hint.pickCancel";
    // The columns between two cells of a row.
    private static final int GAP = 2;
    private static final String CUT = "…";
    private final Spec<P, T> spec;
    private final Consumer<Msg> dispatch;
    private final ListElement<?> list;
    // Shown instead of the list when there are no rows: the same id, so it takes the focus and Escape the same
    // way, but its one line is not highlighted.
    private final Option<ListElement<?>> empty;
    // The rows shown, as of the last render.
    private List<Row<T>> shown = List.empty();
    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public PickDialog(Spec<P, T> spec, Consumer<Msg> dispatch) {
        this.spec = spec;
        this.dispatch = dispatch;
        this.list = list().id(spec.id()).focusable().highlightSymbol("").onKeyEvent(this::handleKey);
        this.empty = spec.emptyKey()
                .map(key -> list().id(spec.id())
                        .focusable()
                        .highlightSymbol("")
                        .highlightStyle(Style.EMPTY)
                        .onKeyEvent(this::handleKey)
                        .elements(text(Messages.get(key)).addClass(Theme.Classes.DIM)));
    }

    @Override
    public List<String> ids() {
        return List.of(spec.id());
    }

    /**
     * The popup, while the model has {@code P} open. It is as tall as its rows need, but no taller than the
     * screen, where the list scrolls.
     */
    @Override
    public Option<Element> render(Model model) {
        return model.popup(spec.popupType()).map(popup -> {
            List<Integer> widths = widths(spec.rows().apply(popup));
            show(spec.rows().apply(popup), widths);
            int width = widths.sum().intValue() + GAP * Math.max(0, widths.size() - 1);
            ListElement<?> rows = empty.filter(none -> shown.isEmpty()).getOrElse(list);
            // Wide enough for the hint, which would wrap otherwise.
            int hint = Messages.get(HINT).length();
            Element frame = DialogFrame.withHint(
                            spec.title().apply(popup),
                            Math.max(DialogFrame.LIST_WIDTH, Math.max(width, hint) + DialogFrame.FRAME),
                            HINT,
                            List.of(rows.constraint(Constraint.fill())))
                    // The rows, the blank line and the hint, plus the border.
                    .length(Math.max(1, shown.size()) + 2 + 2);
            return new WithDetails(
                    frame,
                    shown.lift().apply(list.selected()).flatMap(PickDialog::details),
                    shown.flatMap(PickDialog::details));
        });
    }

    /**
     * How wide each column of {@code rows} is: as wide as its widest cell, once cut.
     */
    static List<Integer> widths(List<? extends Row<?>> rows) {
        int columns = rows.map(row -> row.cells().size()).max().getOrElse(0);
        return List.range(0, columns)
                .map(column -> rows.flatMap(row -> row.cells().lift().apply(column))
                        .map(cell -> cell.shown().length())
                        .max()
                        .getOrElse(0));
    }

    /**
     * The cells of {@code row} in full, one per line, when one of them is cut.
     */
    static Option<String> details(Row<?> row) {
        return Option.when(
                row.cells().exists(Cell::isCut),
                () -> row.cells()
                        .map(Cell::text)
                        .filter(text -> !text.isEmpty())
                        .mkString("\n"));
    }

    private void show(List<Row<T>> rows, List<Integer> widths) {
        if (!rows.equals(shown)) {
            shown = rows;
            list.selected(0);
            list.elements(rows.map(row -> element(row, widths)).toJavaArray(StyledElement[]::new));
        }
    }

    private static StyledElement<?> element(Row<?> row, List<Integer> widths) {
        if (row.cells().size() == 1) {
            return element(row.cells().head());
        }
        // In a row, the length of a cell is its width.
        return row(row.cells()
                        .<Integer, StyledElement<?>>zipWith(
                                widths, (cell, width) -> element(cell).length(width))
                        .toJavaArray(StyledElement[]::new))
                .spacing(GAP);
    }

    private static StyledElement<?> element(Cell cell) {
        StyledElement<?> text = text(cell.shown());
        return cell.cssClass().<StyledElement<?>>map(text::addClass).getOrElse(text);
    }

    private EventResult handleKey(KeyEvent event) {
        Option<Msg> msg = event.code() == KeyCode.ESCAPE
                ? Option.of(spec.onCancel().get())
                : event.isConfirm()
                        ? shown.lift().apply(list.selected()).map(Row::value).map(spec.onPick())
                        : Option.none();
        msg.forEach(this::send);
        return msg.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    private void send(Msg msg) {
        // The next time it opens, it starts at the top again.
        shown = List.empty();
        dispatch.accept(msg);
    }

    /**
     * A row to pick.
     *
     * @param value what picking it sends
     * @param cells what it shows, one per column
     */
    public record Row<T>(T value, List<Cell> cells) {

        /**
         * A row showing just {@code name}, which picking it sends.
         */
        public static Row<String> of(String name) {
            return new Row<>(name, List.of(Cell.of(name)));
        }
    }

    /**
     * A cell of a row.
     *
     * @param cssClass the class it is drawn with, if any
     * @param cap      the characters it shows at most, if any; past those it is cut
     */
    public record Cell(String text, Option<String> cssClass, Option<Integer> cap) {

        public static Cell of(String text) {
            return new Cell(text, Option.none(), Option.none());
        }

        public static Cell styled(String text, String cssClass) {
            return new Cell(text, Option.of(cssClass), Option.none());
        }

        /**
         * This cell, cut past {@code cap} characters.
         */
        public Cell capped(int cap) {
            return new Cell(text, cssClass, Option.of(cap));
        }

        boolean isCut() {
            return cap.exists(max -> text.length() > max);
        }

        /**
         * The text as shown: when it is cut, it ends in an ellipsis within the cap.
         */
        String shown() {
            return isCut() ? text.substring(0, cap.get() - CUT.length()) + CUT : text;
        }
    }

    /**
     * What the dialog shows and sends.
     *
     * @param id        the id of the list, which takes the focus while the dialog is open
     * @param popupType the popup that makes the dialog show
     * @param title     the title, from the popup
     * @param rows      the rows to pick from, from the popup
     * @param onPick    the message for picking a row, from what it stands for
     * @param onCancel  the message for cancelling, by Escape
     * @param emptyKey  the message key of the line shown when there are no rows to pick, if any
     */
    public record Spec<P, T>(
            String id,
            Class<P> popupType,
            Function<P, String> title,
            Function<P, List<Row<T>>> rows,
            Function<T, Msg> onPick,
            Supplier<Msg> onCancel,
            Option<String> emptyKey) {

        /**
         * A dialog that shows no line when there are no rows to pick.
         */
        public Spec(
                String id,
                Class<P> popupType,
                Function<P, String> title,
                Function<P, List<Row<T>>> rows,
                Function<T, Msg> onPick,
                Supplier<Msg> onCancel) {
            this(id, popupType, title, rows, onPick, onCancel, Option.none());
        }
    }
}
