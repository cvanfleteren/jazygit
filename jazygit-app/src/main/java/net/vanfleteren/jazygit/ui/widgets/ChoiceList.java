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
 * can preview what choosing it does, as soon as it is highlighted.
 *
 * @param <P> the popup the list is shown in
 */
public final class ChoiceList<P extends Popup> {

    private static final char CANCEL_KEY = 'c';
    private final List<Choice<P>> choices;
    private final List<Line> lines;
    private final Consumer<Msg> dispatch;
    private final ListElement<?> list;
    // What the rows show, as of the last render.
    private List<Row> shown = List.empty();

    /**
     * @param id       the id of the list, which takes the focus while the popup is open
     * @param choices  the options, in order; a cancel option is added after them
     * @param onCancel the message for cancelling, by the cancel option or by Escape
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ChoiceList(String id, List<Choice<P>> choices, Supplier<Msg> onCancel, Consumer<Msg> dispatch) {
        this.choices = choices.append(new Choice<P>(CANCEL_KEY, "dialog.choice.cancel", onCancel));
        this.lines = lines(this.choices);
        this.dispatch = dispatch;
        this.list = list().id(id).focusable().highlightSymbol("").onKeyEvent(this::handleKey);
        list.selected(firstOption());
    }

    /**
     * The lines that show {@code choices}: a heading starts a group, after a blank line unless it is the first
     * line; with groups, cancelling comes after a blank line too, so it is in none of them.
     */
    private static <P> List<Line> lines(List<Choice<P>> choices) {
        boolean grouped = choices.exists(choice -> choice.heading().isDefined());
        return choices.zipWithIndex().flatMap(indexed -> {
            Choice<P> choice = indexed._1;
            int index = indexed._2;
            boolean last = index == choices.size() - 1;
            List<Line> before = choice.heading()
                    .map(key -> List.<Line>of(new Heading(Messages.get(key))))
                    .getOrElse(List.empty())
                    .prependAll(
                            index > 0 && (choice.heading().isDefined() || grouped && last)
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
     * How many lines the list takes.
     */
    public int height() {
        return lines.size();
    }

    /**
     * The width of the widest line, as of the last render, with the letter of an option.
     */
    public int width() {
        return lines.map(this::lineText).map(String::length).max().getOrElse(0);
    }

    private void show(List<Row> rows) {
        if (!rows.equals(shown)) {
            shown = rows;
            list.elements(lines.map(this::element).toJavaArray(StyledElement[]::new));
        }
    }

    private StyledElement<?> element(Line line) {
        return switch (line) {
            case OptionLine(int index) ->
                shown.get(index).enabled()
                        ? text(lineText(line))
                        : text(lineText(line)).addClass(Theme.Classes.DISABLED);
            case Heading(String heading) -> text(heading).addClass(Theme.Classes.BOLD);
            case Blank() -> text("");
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
            case Blank() -> "";
        };
    }

    private EventResult handleKey(KeyEvent event) {
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
            highlight(lines.lastIndexWhere(OptionLine.class::isInstance));
            return EventResult.HANDLED;
        }
        Option<Choice<P>> chosen = event.code() == KeyCode.ESCAPE
                ? Option.of(choices.last())
                : event.isConfirm() ? selected().map(choices::get) : choices.find(choice -> event.isChar(choice.key()));
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
            selected()
                    .flatMap(index -> choices.get(index).onHighlight())
                    .forEach(preview -> dispatch.accept(preview.get()));
        }
    }

    /**
     * Highlights the option of {@code key}, or the first one when there is none, without previewing it: for when
     * the list is shown anew.
     */
    public void highlightKey(Option<Character> key) {
        list.selected(key.flatMap(pressed -> choices.find(choice -> choice.key() == pressed))
                .map(choice -> lines.indexOf(new OptionLine(choices.indexOf(choice))))
                .getOrElse(this::firstOption));
    }

    private int firstOption() {
        return lines.indexWhere(OptionLine.class::isInstance);
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
     * A line of the list: an option, or the heading of a group of them, or the blank line before one.
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
}
