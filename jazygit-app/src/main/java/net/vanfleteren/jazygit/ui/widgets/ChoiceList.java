package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.Iterator;
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
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

/**
 * A selectable list of options that ends in a cancel option, for inside a popup. An option is chosen with its
 * letter, or with the arrow keys and Enter; Escape cancels. An option that does not apply is struck through
 * and dimmed, and cannot be chosen. Options can be grouped under headings, which the arrow keys skip. An option
 * can preview what choosing it does, as soon as it is highlighted. A list can be filterable: then {@code /}
 * opens a {@link ListFilter}, and only the options whose text contains it stay shown; while it is open, the
 * letters go into it, and Escape closes it instead of cancelling.
 *
 * @param <P> the popup the list is shown in
 */
public final class ChoiceList<P extends Popup> {

    private static final char CANCEL_KEY = 'c';
    private final List<Choice<P>> choices;
    // The lines of all options, whatever the filter.
    private final List<Line> allLines;
    private final Consumer<Msg> dispatch;
    private final ListElement<?> list;
    private final boolean filterable;
    private final ListFilter filter = new ListFilter();
    // The lines shown, of the options that contain the filter.
    private List<Line> lines;
    // What the rows show, as of the last render.
    private List<Row> shown = List.empty();

    /**
     * @param id       the id of the list, which takes the focus while the popup is open
     * @param choices  the options, in order; a cancel option is added after them
     * @param onCancel the message for cancelling, by the cancel option or by Escape
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ChoiceList(String id, List<Choice<P>> choices, Supplier<Msg> onCancel, Consumer<Msg> dispatch) {
        this(id, choices, onCancel, dispatch, false);
    }

    /**
     * @param filterable whether {@code /} opens a filter on the options
     */
    public ChoiceList(
            String id, List<Choice<P>> choices, Supplier<Msg> onCancel, Consumer<Msg> dispatch, boolean filterable) {
        this.choices = choices.append(new Choice<P>(CANCEL_KEY, "dialog.choice.cancel", onCancel));
        this.allLines = lines(this.choices, List.range(0, this.choices.size()));
        this.lines = allLines;
        this.dispatch = dispatch;
        this.filterable = filterable;
        this.list = list().id(id).focusable().highlightSymbol("").onKeyEvent(this::handleKey);
        list.selected(firstOption());
    }

    /**
     * The lines that show the options of {@code choices} at {@code visible}: a heading starts a group that has
     * one of them, after a blank line unless it is the first line; with groups, cancelling comes after a blank
     * line too, so it is in none of them. Without options, it says nothing matches.
     */
    private static <P> List<Line> lines(List<Choice<P>> choices, List<Integer> visible) {
        if (visible.isEmpty()) {
            return List.of(new NoMatches());
        }
        boolean grouped = choices.exists(choice -> choice.heading().isDefined());
        int cancel = choices.size() - 1;
        // The group of an option: the index of the option whose heading starts it, if any; cancelling has its own.
        Function<Integer, Integer> group = index -> index == cancel
                ? cancel
                : choices.take(index + 1)
                        .lastIndexWhere(choice -> choice.heading().isDefined());
        return visible.zipWithIndex().flatMap(indexed -> {
            int index = indexed._1;
            int position = indexed._2;
            int start = group.apply(index);
            boolean starts = position == 0 || group.apply(visible.get(position - 1)) != start;
            Option<String> heading = Option.when(starts && index != cancel && start >= 0, () -> choices.get(start))
                    .flatMap(Choice::heading);
            List<Line> before = heading.map(key -> List.<Line>of(new Heading(Messages.get(key))))
                    .getOrElse(List.empty())
                    .prependAll(
                            position > 0 && (heading.isDefined() || grouped && index == cancel)
                                    ? List.of(new Blank())
                                    : List.empty());
            return before.append(new OptionLine(index));
        });
    }

    /**
     * The list for {@code popup}, one line per option and per heading.
     */
    public StyledElement<?> render(Model model, P popup) {
        show(choices.map(
                choice -> new Row(choice.label().apply(popup), choice.enabled().test(model, popup))));
        return list.length(height());
    }

    /**
     * How many lines the list takes: as many as without a filter, so it does not shrink as the filter grows.
     */
    public int height() {
        return allLines.size();
    }

    /**
     * The width of the widest line, as of the last render, with the letter of an option, whatever the filter.
     */
    public int width() {
        return allLines.map(this::lineText).map(String::length).max().getOrElse(0);
    }

    /**
     * The line to type the filter in, while it is open.
     */
    public Option<StyledElement<?>> filterField() {
        return Option.when(filter.isOpen(), filter::field);
    }

    /**
     * The message key of the hint about the keys: it tells how to filter, or how to stop.
     */
    public String hintKey() {
        return !filterable
                ? "dialog.hint.selectCancel"
                : filter.isOpen() ? "dialog.hint.selectFiltering" : "dialog.hint.selectFilter";
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

    private void show(List<Row> rows) {
        if (!rows.equals(shown)) {
            shown = rows;
            refilter(false);
        }
    }

    /**
     * Shows the options that contain the filter, keeping the highlighted one when it does, else highlighting the
     * first; when the highlight moves to another option and {@code preview} says so, that says it moved.
     */
    private void refilter(boolean preview) {
        Option<Integer> before = selected();
        lines = lines(choices, visible());
        list.elements(lines.map(this::element).toJavaArray(StyledElement[]::new));
        list.selected(before.map(index -> lines.indexOf(new OptionLine(index)))
                .filter(line -> line >= 0)
                .getOrElse(this::firstOption));
        if (preview && !selected().equals(before)) {
            previewSelected();
        }
    }

    /**
     * The indexes of the options whose text, as of the last render, contains the filter.
     */
    private List<Integer> visible() {
        return List.range(0, choices.size())
                .filter(index -> shown.lift().apply(index).forAll(row -> filter.matches(row.label())));
    }

    private StyledElement<?> element(Line line) {
        return switch (line) {
            case OptionLine(int index) ->
                shown.get(index).enabled()
                        ? text(lineText(line))
                        : text(lineText(line)).addClass(Theme.Classes.DISABLED);
            case Heading(String heading) -> text(heading).addClass(Theme.Classes.BOLD);
            case Blank() -> text("");
            case NoMatches() -> ListFilter.noMatches();
        };
    }

    private String lineText(Line line) {
        return switch (line) {
            case OptionLine(int index) ->
                shown.lift()
                        .apply(index)
                        .map(row -> Messages.get(
                                "dialog.choice.row",
                                String.valueOf(choices.get(index).key()),
                                row.label()))
                        .getOrElse("");
            case Heading(String heading) -> heading;
            case Blank(), NoMatches() -> "";
        };
    }

    private EventResult handleKey(KeyEvent event) {
        if (filter.isOpen() && event.code() == KeyCode.ESCAPE) {
            filter.close();
            refilter(true);
            return EventResult.HANDLED;
        }
        if (filterable && !filter.isOpen() && ListFilter.opens(event)) {
            filter.open();
            return EventResult.HANDLED;
        }
        if (filter.edit(event)) {
            refilter(true);
            return EventResult.HANDLED;
        }
        // The list is not left to move the highlight, which would not skip headings, nor say it moved.
        if (event.matches(Actions.MOVE_UP) || event.matches(Actions.MOVE_DOWN)) {
            move(event.matches(Actions.MOVE_UP) ? -1 : 1);
            return EventResult.HANDLED;
        }
        if (event.matches(Actions.HOME) || event.matches(Actions.PAGE_UP)) {
            highlight(firstOption());
            return EventResult.HANDLED;
        }
        if (event.matches(Actions.END) || event.matches(Actions.PAGE_DOWN)) {
            highlight(Math.max(firstOption(), lines.lastIndexWhere(OptionLine.class::isInstance)));
            return EventResult.HANDLED;
        }
        // While the filter is open, the letters are its own.
        Option<Choice<P>> chosen = event.code() == KeyCode.ESCAPE
                ? Option.of(choices.last())
                : event.isConfirm()
                        ? selected().map(choices::get)
                        : filter.isOpen() ? Option.none() : choices.find(choice -> event.isChar(choice.key()));
        chosen.forEach(this::choose);
        return chosen.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    /**
     * The index of the highlighted option.
     */
    private Option<Integer> selected() {
        return lines.lift()
                .apply(Math.clamp(list.selected(), 0, lines.size() - 1))
                .flatMap(line -> line instanceof OptionLine(int index) ? Option.of(index) : Option.none());
    }

    /**
     * Highlights the next option in {@code direction}, skipping headings and blank lines; stays put at either end.
     */
    private void move(int direction) {
        int from = list.selected();
        Iterator<Integer> next = direction < 0
                ? Iterator.iterate(from - 1, i -> i - 1).takeWhile(i -> i >= 0)
                : Iterator.iterate(from + 1, i -> i + 1).takeWhile(i -> i < lines.size());
        next.find(i -> lines.get(i) instanceof OptionLine).forEach(this::highlight);
    }

    /**
     * Highlights the option on {@code line}, which says so when it previews what choosing it does.
     */
    private void highlight(int line) {
        if (line != list.selected()) {
            list.selected(line);
            previewSelected();
        }
    }

    private void previewSelected() {
        selected()
                .flatMap(index -> choices.get(index).onHighlight())
                .forEach(preview -> dispatch.accept(preview.get()));
    }

    /**
     * Highlights the option of {@code key}, or the first one when there is none, without previewing it: for when
     * the list is shown anew, which also closes the filter.
     */
    public void highlightKey(Option<Character> key) {
        if (filter.isOpen()) {
            filter.close();
            refilter(false);
        }
        list.selected(key.flatMap(pressed -> choices.find(choice -> choice.key() == pressed))
                .map(choice -> lines.indexOf(new OptionLine(choices.indexOf(choice))))
                .getOrElse(this::firstOption));
    }

    /**
     * The line of the first option shown, or the first line when there is none.
     */
    private int firstOption() {
        return Math.max(0, lines.indexWhere(OptionLine.class::isInstance));
    }

    private void choose(Choice<P> choice) {
        // A disabled option is not actionable.
        boolean disabled = shown.lift().apply(choices.indexOf(choice)).exists(row -> !row.enabled());
        if (!disabled) {
            list.selected(firstOption());
            dispatch.accept(choice.msg().get());
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
