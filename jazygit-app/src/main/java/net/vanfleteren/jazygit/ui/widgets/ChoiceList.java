package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.text;

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
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

/**
 * A selectable list of options, for inside a popup. An option is chosen with its letter, or with the arrow keys and
 * Enter; Escape cancels, which the hint of the popup tells. No two options have the same letter. An option that does not apply is struck through
 * and dimmed, and cannot be chosen. Options can be grouped under headings, which the arrow keys skip. An option
 * can preview what choosing it does, as soon as it is highlighted. A list can be filterable: then {@code /}
 * opens a {@link ListFilter}, and only the options whose text contains it stay shown; while it is open, the
 * letters go into it, and Escape closes it instead of cancelling. The highlighted line and the filter live in the
 * {@link Model}, as its {@link ListCursor}.
 *
 * @param <P> the popup the list is shown in
 */
public final class ChoiceList<P extends Popup> {

    private final List<Choice<P>> choices;
    private final Supplier<Msg> onCancel;
    // The lines of all options, whatever the filter.
    private final List<Line> allLines;
    private final Consumer<Msg> dispatch;
    private final ListElement<?> list;
    private final boolean filterable;
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
        List<Character> shared = choices.map(Choice::key)
                .groupBy(key -> key)
                .filterValues(keys -> keys.size() > 1)
                .keySet()
                .toList()
                .sorted();
        if (!shared.isEmpty()) {
            throw new IllegalArgumentException("The options of " + id + " share the letters " + shared.mkString(", "));
        }
        this.choices = choices;
        this.onCancel = onCancel;
        this.allLines = lines(this.choices, List.range(0, this.choices.size()));
        this.dispatch = dispatch;
        this.filterable = filterable;
        this.list = list().id(id).focusable().highlightSymbol("");
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
        View view = view(rows(model, popup), model.listCursor(), opensAt);
        list.elements(view.lines().map(line -> element(line, view.rows())).toJavaArray(StyledElement[]::new))
                .selected(view.selected())
                .onKeyEvent(event -> handleKey(view, event))
                .onMouseEvent(event -> ListKeys.wheel(
                        event, view.selected(), view.lines().size(), view::isOption, line -> highlight(view, line)));
        return list.length(height());
    }

    /**
     * How many lines the list takes: as many as without a filter, so it does not shrink as the filter grows.
     */
    public int height() {
        return allLines.size();
    }

    /**
     * The width of the widest line for {@code popup}, with the letter of an option, whatever the filter.
     */
    public int width(Model model, P popup) {
        List<Row> rows = rows(model, popup);
        return allLines.map(line -> lineText(line, rows))
                .map(String::length)
                .max()
                .getOrElse(0);
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
                .map(key -> Messages.get(key).length())
                .max()
                .get();
    }

    private List<Row> rows(Model model, P popup) {
        return choices.map(
                choice -> new Row(choice.label().apply(popup), choice.enabled().test(model, popup)));
    }

    /**
     * What the list shows for {@code rows} at {@code cursor}: the options that contain its filter, and the line it
     * highlights; until the user moves, that of {@code opensAt}, or the first option.
     */
    private View view(List<Row> rows, ListCursor cursor, Option<Character> opensAt) {
        List<Line> lines = lines(choices, visible(rows, cursor.filter()));
        int selected = cursor.line()
                .map(line -> Math.clamp(line, 0, lines.size() - 1))
                .orElse(() -> opensAt.flatMap(key -> choices.indexWhereOption(choice -> choice.key() == key))
                        .map(index -> lines.indexOf(new OptionLine(index)))
                        .filter(line -> line >= 0))
                .getOrElse(() -> firstOption(lines));
        return new View(rows, lines, selected, cursor);
    }

    /**
     * The indexes of the options whose text contains {@code filter}.
     */
    private List<Integer> visible(List<Row> rows, Option<String> filter) {
        return List.range(0, choices.size())
                .filter(index -> ListFilter.matches(filter, rows.get(index).label()));
    }

    /**
     * The line of the first option among {@code lines}, or the first line when there is none.
     */
    private static int firstOption(List<Line> lines) {
        return Math.max(0, lines.indexWhere(OptionLine.class::isInstance));
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

    private EventResult handleKey(View view, KeyEvent event) {
        Option<String> typed = view.cursor().filter();
        if (typed.isDefined() && event.code() == KeyCode.ESCAPE) {
            refilter(view, Option.none());
            return EventResult.HANDLED;
        }
        if (filterable && typed.isEmpty() && ListFilter.opens(event)) {
            dispatch.accept(
                    new Msg.ListCursorChanged(view.cursor().at(view.selected()).withFilter(Option.of(""))));
            return EventResult.HANDLED;
        }
        Option<String> edited = typed.flatMap(text -> filter.edit(text, event));
        if (edited.isDefined()) {
            refilter(view, edited);
            return EventResult.HANDLED;
        }
        // The list is not left to move the highlight, which would not skip headings, nor say it moved.
        Option<Integer> moved =
                ListKeys.move(event, view.selected(), view.lines().size(), view::isOption);
        if (moved.isDefined()) {
            moved.filter(line -> line != view.selected()).forEach(line -> highlight(view, line));
            return EventResult.HANDLED;
        }
        if (event.code() == KeyCode.ESCAPE) {
            dispatch.accept(onCancel.get());
            return EventResult.HANDLED;
        }
        // While the filter is open, the letters are its own.
        Option<Choice<P>> chosen = event.isConfirm()
                ? view.option().map(choices::get)
                : typed.isDefined() ? Option.none() : choices.find(choice -> event.isChar(choice.key()));
        chosen.forEach(choice -> choose(view, choice));
        return chosen.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    /**
     * Highlights {@code line}, which says so when its option previews what choosing it does.
     */
    private void highlight(View view, int line) {
        dispatch.accept(new Msg.ListCursorChanged(view.cursor().at(line)));
        preview(view.lineOption(line));
    }

    /**
     * Shows the options that contain {@code filter}, keeping the highlighted one when it does, else highlighting the
     * first; when that is another option, says it moved.
     */
    private void refilter(View view, Option<String> filter) {
        List<Line> lines = lines(choices, visible(view.rows(), filter));
        int line = view.option()
                .map(index -> lines.indexOf(new OptionLine(index)))
                .filter(found -> found >= 0)
                .getOrElse(() -> firstOption(lines));
        dispatch.accept(new Msg.ListCursorChanged(new ListCursor(Option.of(line), filter)));
        Option<Integer> after = lines.asPartialFunction().lift().apply(line).flatMap(ChoiceList::index);
        if (!after.equals(view.option())) {
            preview(after);
        }
    }

    private void preview(Option<Integer> option) {
        option.flatMap(index -> choices.get(index).onHighlight()).forEach(preview -> dispatch.accept(preview.get()));
    }

    private void choose(View view, Choice<P> choice) {
        // A disabled option is not actionable.
        if (view.rows().get(choices.indexOf(choice)).enabled()) {
            dispatch.accept(choice.msg().get());
        }
    }

    private static Option<Integer> index(Line line) {
        return line instanceof OptionLine(int index) ? Option.of(index) : Option.none();
    }

    /**
     * What the list shows for its popup and the model's cursor in it.
     *
     * @param rows     what each option shows, whatever the filter
     * @param lines    the lines shown, of the options that contain the filter
     * @param selected the highlighted line
     */
    private record View(List<Row> rows, List<Line> lines, int selected, ListCursor cursor) {

        boolean isOption(int line) {
            return lines.get(line) instanceof OptionLine;
        }

        /**
         * The index of the option on {@code line}, if that is one.
         */
        Option<Integer> lineOption(int line) {
            return lines.asPartialFunction().lift().apply(line).flatMap(ChoiceList::index);
        }

        /**
         * The index of the highlighted option.
         */
        Option<Integer> option() {
            return lineOption(selected);
        }
    }

    /**
     * A line of the list: an option, or the heading of a group of them, or the blank line before one, or the line
     * saying no option contains the filter.
     */
    private sealed interface Line {}

    /**
     * An option as it is shown: its text, and whether it applies.
     */
    private record Row(String label, boolean enabled) {}

    /**
     * @param index the index of the option among the choices
     */
    private record OptionLine(int index) implements Line {}

    private record Heading(String text) implements Line {}

    private record Blank() implements Line {}

    private record NoMatches() implements Line {}
}
