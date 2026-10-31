package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.style.Style;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.ui.TextWidth;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

/**
 * A selectable list of options, for inside a popup. An option is chosen with its letter, or with the arrow keys and
 * Enter; Escape cancels, which the hint of the popup tells. No two options have the same letter. An option that does not apply is struck through
 * and dimmed, and cannot be chosen; one that makes no sense for the popup is left out. Options can be grouped under headings, which the arrow keys skip. An option
 * can preview what choosing it does, as soon as it is highlighted. A list can be filterable: then {@code /}
 * opens a {@link ListFilter}, and only the options whose text contains it stay shown; while it is open, the
 * letters go into it, and Escape closes it instead of cancelling. A list can be in two columns, without headings or a
 * filter: the options fill the first column down, then the second, the arrows to the sides go to the other column,
 * and Up and Down go on from one column to the other (ADR 66). The highlighted line and the filter live in the
 * {@link Model}, as its {@link ListCursor}.
 *
 * @param <P> the popup the list is shown in
 */
public final class ChoiceList<P extends Popup> {

    private final String id;
    private final List<Choice<P>> choices;
    private final Supplier<Msg> onCancel;
    private final Consumer<Msg> dispatch;
    private final ListElement<?> list;
    private final boolean filterable;
    // The width two columns share, for a list in two columns.
    private final Option<Integer> columnsWidth;
    private final FilterLine filter = new FilterLine();

    /**
     * @param id       the id of the list, which takes the focus while the popup is open
     * @param choices  the options, in order, each with a letter of its own
     * @param onCancel the message for cancelling, by Escape
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ChoiceList(String id, List<Choice<P>> choices, Supplier<Msg> onCancel, Consumer<Msg> dispatch) {
        this(id, choices, onCancel, dispatch, false);
    }

    /**
     * @param filterable whether {@code /} opens a filter on the options
     * @throws IllegalArgumentException when two options have the same letter, as only the first could be chosen
     */
    public ChoiceList(
            String id, List<Choice<P>> choices, Supplier<Msg> onCancel, Consumer<Msg> dispatch, boolean filterable) {
        this(id, choices, onCancel, dispatch, filterable, Option.none());
    }

    private ChoiceList(
            String id,
            List<Choice<P>> choices,
            Supplier<Msg> onCancel,
            Consumer<Msg> dispatch,
            boolean filterable,
            Option<Integer> columnsWidth) {
        List<Character> shared = choices.map(Choice::key)
                .groupBy(key -> key)
                .filterValues(keys -> keys.size() > 1)
                .keySet()
                .toList()
                .sorted();
        if (!shared.isEmpty()) {
            throw new IllegalArgumentException("The options of " + id + " share the letters " + shared.mkString(", "));
        }
        if (columnsWidth.isDefined()
                && (filterable || choices.exists(choice -> choice.heading().isDefined()))) {
            throw new IllegalArgumentException(
                    "The options of " + id + " are in two columns, which have no headings nor a filter");
        }
        this.id = id;
        this.choices = choices;
        this.onCancel = onCancel;
        this.dispatch = dispatch;
        this.filterable = filterable;
        this.columnsWidth = columnsWidth;
        this.list = list().id(id).focusable().highlightSymbol("");
    }

    /**
     * A list in two columns that share {@code width}, each half of it; in one column when the widest option does
     * not fit in half.
     *
     * @throws IllegalArgumentException when an option has a heading, or two have the same letter
     */
    public static <P extends Popup> ChoiceList<P> inTwoColumns(
            String id, List<Choice<P>> choices, Supplier<Msg> onCancel, Consumer<Msg> dispatch, int width) {
        return new ChoiceList<>(id, choices, onCancel, dispatch, false, Option.of(width));
    }

    /**
     * The lines that show the options of {@code choices} at {@code visible}: a heading starts a group that has
     * one of them, after a blank line unless it is the first line. Without options, it says nothing matches.
     */
    private static <P> List<Line> lines(List<Choice<P>> choices, List<Integer> visible) {
        if (visible.isEmpty()) {
            return List.of(new NoMatches());
        }
        // The group of an option: the index of the option whose heading starts it, if any.
        Function<Integer, Integer> group = index -> choices.take(index + 1)
                .lastIndexWhere(choice -> choice.heading().isDefined());
        return visible.zipWithIndex().flatMap(indexed -> {
            int index = indexed._1;
            int position = indexed._2;
            int start = group.apply(index);
            boolean starts = position == 0 || group.apply(visible.get(position - 1)) != start;
            Option<String> heading =
                    Option.when(starts && start >= 0, () -> choices.get(start)).flatMap(Choice::heading);
            List<Line> before = heading.map(key -> List.<Line>of(new Heading(Messages.get(key))))
                    .getOrElse(List.empty())
                    .prependAll(position > 0 && heading.isDefined() ? List.of(new Blank()) : List.empty());
            return before.append(new OptionLine(index));
        });
    }

    /**
     * The list for {@code popup}, one line per option and per heading, opening at the first option.
     */
    public StyledElement<?> render(Model model, P popup) {
        return render(model, popup, Option.none());
    }

    /**
     * The list for {@code popup}, one line per option and per heading.
     *
     * @param opensAt the key of the option highlighted until the user moves, if not the first
     */
    public StyledElement<?> render(Model model, P popup, Option<Character> opensAt) {
        List<Row> rows = rows(model, popup);
        FilteredCursor<Line> view = view(rows, model.listCursor(), opensAt);
        List<StyledElement<?>> elements = view.lines().map(line -> element(line, rows));
        return down(rows)
                .<StyledElement<?>>map(first -> columns(view, rows, elements, first))
                .getOrElse(() -> list.elements(elements.toJavaArray(StyledElement[]::new))
                        .selected(view.selected())
                        .onKeyEvent(event -> handleKey(view, rows, Option.none(), event))
                        .onMouseEvent(event -> wheel(view, event))
                        .length(height(model, popup)));
    }

    /**
     * The options in two columns, the first {@code first} of them on the left, each column half the width. The
     * column without the highlighted option hides its highlight. Both are drawn anew each time, as neither scrolls.
     */
    private StyledElement<?> columns(
            FilteredCursor<Line> view, List<Row> rows, List<StyledElement<?>> elements, int first) {
        int width = columnsWidth.get();
        boolean onLeft = view.selected() < first;
        ListElement<?> left = list().id(id)
                .focusable()
                .highlightSymbol("")
                .elements(elements.take(first).toJavaArray(StyledElement[]::new))
                .selected(onLeft ? view.selected() : 0)
                .onKeyEvent(event -> handleKey(view, rows, Option.of(first), event))
                .onMouseEvent(event -> wheel(view, event));
        ListElement<?> right = list().highlightSymbol("")
                .elements(elements.drop(first).toJavaArray(StyledElement[]::new))
                .selected(onLeft ? 0 : view.selected() - first)
                .onMouseEvent(event -> wheel(view, event));
        return row(
                        (onLeft ? left : left.highlightStyle(Style.EMPTY)).length(width / 2),
                        (onLeft ? right.highlightStyle(Style.EMPTY) : right).length(width - width / 2))
                .length(first);
    }

    private EventResult wheel(FilteredCursor<Line> view, MouseEvent event) {
        return ListKeys.wheel(
                event, view.selected(), view.size(), view::highlightable, line -> moved(view, view.at(line)));
    }

    /**
     * How many lines the list takes for {@code popup}: as many as without a filter, so it does not shrink as the
     * filter grows; in two columns, those of the first.
     */
    public int height(Model model, P popup) {
        List<Row> rows = rows(model, popup);
        return down(rows).getOrElse(() -> unfiltered(rows).size());
    }

    /**
     * The width of the widest line for {@code popup}, with the letter of an option, whatever the filter.
     */
    public int width(Model model, P popup) {
        return width(rows(model, popup));
    }

    private int width(List<Row> rows) {
        return unfiltered(rows)
                .map(line -> lineText(line, rows))
                .map(TextWidth::of)
                .max()
                .getOrElse(0);
    }

    /**
     * For a list in two columns, how many of the options shown fill the first: half of them, the one more of an odd
     * number. None when the list is one column: when it is not in two, has no options, or its widest option does
     * not fit in half the width with a space before the next column.
     */
    private Option<Integer> down(List<Row> rows) {
        int shown = visible(rows, Option.none()).size();
        return columnsWidth
                .filter(width -> shown > 0 && width(rows) < width / 2)
                .map(width -> (shown + 1) / 2);
    }

    /**
     * The line to type the filter in, while {@code cursor} has it open.
     */
    public Option<StyledElement<?>> filterField(ListCursor cursor) {
        return cursor.filter().map(filter::field);
    }

    /**
     * The message key of the hint about the keys: it tells how to filter, or how to stop.
     */
    public String hintKey(ListCursor cursor) {
        return !filterable
                ? "dialog.hint.selectCancel"
                : cursor.filter().isDefined() ? "dialog.hint.selectFiltering" : "dialog.hint.selectFilter";
    }

    /**
     * The width of the widest hint {@link #hintKey} gives, so the hint fits whether the filter is open or not.
     */
    public int hintWidth() {
        return (filterable
                        ? List.of("dialog.hint.selectFilter", "dialog.hint.selectFiltering")
                        : List.of("dialog.hint.selectCancel"))
                .map(key -> TextWidth.of(Messages.get(key)))
                .max()
                .get();
    }

    private List<Row> rows(Model model, P popup) {
        return choices.map(choice -> new Row(
                choice.label().apply(popup),
                choice.enabled().test(model, popup),
                choice.shown().test(model, popup)));
    }

    /**
     * The lines of the options shown for {@code rows}, whatever the filter.
     */
    private List<Line> unfiltered(List<Row> rows) {
        return lines(choices, visible(rows, Option.none()));
    }

    /**
     * What the list shows for {@code rows} at {@code cursor}: the options that contain its filter, and the line it
     * highlights; until the user moves, that of {@code opensAt}, or the first option.
     */
    private FilteredCursor<Line> view(List<Row> rows, ListCursor cursor, Option<Character> opensAt) {
        return FilteredCursor.of(
                filter -> lines(choices, visible(rows, filter)),
                // A heading, a blank line, and the line saying nothing matches are not options.
                OptionLine.class::isInstance,
                lines -> opensAt.flatMap(key -> choices.indexWhereOption(choice -> choice.key() == key))
                        .map(index -> lines.indexOf(new OptionLine(index))),
                cursor);
    }

    /**
     * The indexes of the options shown whose text contains {@code filter}.
     */
    private List<Integer> visible(List<Row> rows, Option<String> filter) {
        return List.range(0, choices.size())
                .filter(index -> rows.get(index).shown()
                        && ListFilter.matches(filter, rows.get(index).label()));
    }

    private StyledElement<?> element(Line line, List<Row> rows) {
        return switch (line) {
            case OptionLine(int index) when rows.get(index).enabled() -> text(lineText(line, rows));
            case OptionLine(int index) -> text(lineText(line, rows)).addClass(Theme.Classes.DISABLED);
            case Heading(String heading) -> text(heading).addClass(Theme.Classes.BOLD);
            case Blank() -> text("");
            case NoMatches() -> ListFilter.noMatches();
        };
    }

    private String lineText(Line line, List<Row> rows) {
        return switch (line) {
            case OptionLine(int index) ->
                Messages.get(
                        "dialog.choice.row",
                        String.valueOf(choices.get(index).key()),
                        rows.get(index).label());
            case Heading(String heading) -> heading;
            case Blank(), NoMatches() -> "";
        };
    }

    /**
     * @param down in two columns, how many options fill the first
     */
    private EventResult handleKey(FilteredCursor<Line> view, List<Row> rows, Option<Integer> down, KeyEvent event) {
        Option<Integer> aside = down.flatMap(first -> aside(event, view.selected(), view.size(), first));
        if (aside.isDefined()) {
            aside.filter(line -> line != view.selected()).forEach(line -> moved(view, view.at(line)));
            return EventResult.HANDLED;
        }
        return ListKeys.classify(event, view, filterable, filter)
                .map(key -> switch (key) {
                    // The list is not left to move the highlight, which would not skip headings, nor say it moved.
                    case ListKey.CursorKey cursorKey -> {
                        view.after(cursorKey).forEach(cursor -> moved(view, cursor));
                        yield EventResult.HANDLED;
                    }
                    case ListKey.Cancel() -> {
                        dispatch.accept(onCancel.get());
                        yield EventResult.HANDLED;
                    }
                    case ListKey.Confirm() ->
                        choose(
                                rows,
                                view.highlighted().flatMap(ChoiceList::index).map(choices::get));
                })
                // While the filter is open, the letters are its own.
                .getOrElse(() -> view.cursor().filter().isDefined()
                        ? EventResult.UNHANDLED
                        : choose(rows, choices.find(choice -> event.isChar(choice.key()))));
    }

    /**
     * Moves {@code view} to {@code cursor}; when that highlights another option, says so when it previews what
     * choosing it does.
     */
    private void moved(FilteredCursor<Line> view, ListCursor cursor) {
        dispatch.accept(new Msg.ListCursorChanged(cursor));
        Option<Line> after = view.at(cursor).highlighted();
        if (!after.equals(view.highlighted())) {
            preview(after.flatMap(ChoiceList::index));
        }
    }

    private void preview(Option<Integer> option) {
        option.flatMap(index -> choices.get(index).onHighlight()).forEach(preview -> dispatch.accept(preview.get()));
    }

    /**
     * Chooses {@code chosen}, if any. A disabled or left out option is not actionable, but its key is still taken.
     */
    private EventResult choose(List<Row> rows, Option<Choice<P>> chosen) {
        chosen.forEach(choice -> {
            Row row = rows.get(choices.indexOf(choice));
            if (row.shown() && row.enabled()) {
                dispatch.accept(choice.msg().get());
            }
        });
        return chosen.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    /**
     * The line the highlight moves to from {@code from} on an arrow to the side, among {@code size} options in two
     * columns of which the first {@code first} are on the left: the same row of the other column, or its last option
     * when the right one has no such row; {@code from} when there is no column on that side. None for another key.
     */
    private static Option<Integer> aside(KeyEvent event, int from, int size, int first) {
        if (event.matches(Actions.MOVE_LEFT)) {
            return Option.of(from >= first ? from - first : from);
        }
        if (event.matches(Actions.MOVE_RIGHT)) {
            return Option.of(from < first && size > first ? Math.min(from + first, size - 1) : from);
        }
        return Option.none();
    }

    private static Option<Integer> index(Line line) {
        return line instanceof OptionLine(int index) ? Option.of(index) : Option.none();
    }

    /**
     * A line of the list: an option, or the heading of a group of them, or the blank line before one, or the line
     * saying no option contains the filter.
     */
    private sealed interface Line {}

    /**
     * An option as it is shown: its text, whether it applies, and whether it is in the list at all.
     */
    private record Row(String label, boolean enabled, boolean shown) {}

    /**
     * @param index the index of the option among the choices
     */
    private record OptionLine(int index) implements Line {}

    private record Heading(String text) implements Line {}

    private record Blank() implements Line {}

    private record NoMatches() implements Line {}
}
