package net.vanfleteren.jazygit.feature.help;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.layout.Constraint;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.DialogElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.Iterator;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.PopupView;
import net.vanfleteren.jazygit.ui.widgets.WithDetails;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Popup listing the keys of a panel and what they do, under which the details of the highlighted key show when it
 * has any. A key is run with the arrow keys and Enter, or by pressing it; a key of the panel that does nothing for
 * what is selected in it is struck through. Escape or ? closes it. Whether it is open lives in the {@link Model}.
 */
public final class HelpDialog implements PopupView {

    public static final String ID = "help";

    private static final int WIDTH = 50;

    // The key right-aligned in a column of this many characters, then a space and the description.
    private static final int KEY_COLUMN = 12;
    private static final String KEY_FORMAT = "%" + KEY_COLUMN + "s %s";

    // A key has details when the messages have this after the key of its description.
    private static final String DETAILS_SUFFIX = ".details";

    /**
     * What the symbols in a panel mean, as the symbols and the message keys of their descriptions.
     */
    private static final Map<String, List<Tuple2<String, String>>> LEGENDS = HashMap.of(
            Scopes.BRANCHES.id(), List.of(
                    Tuple.of(Messages.get("branches.gone"), "help.branches.legend.gone"),
                    Tuple.of(Messages.get("branches.worktree"), "help.branches.legend.worktree"),
                    Tuple.of("#42", "help.branches.legend.pullRequest"),
                    Tuple.of("✓ ✗ ●", "help.branches.legend.checks"),
                    Tuple.of("✔ ✎ ?", "help.branches.legend.review")));

    /**
     * A line of the list: a key, which can be highlighted, or a text around them.
     */
    private sealed interface Row {
    }

    /**
     * A key, with the message it sends if it does anything.
     *
     * @param matches whether an event is this key
     */
    private record Key(String label, String descriptionKey, Predicate<KeyEvent> matches, Option<Msg> action)
            implements Row {
    }

    /**
     * @param style the class it is drawn with, if any
     */
    private record Text(String text, Option<String> style) implements Row {

        Text(String text) {
            this(text, Option.none());
        }

        static Text heading(String messageKey) {
            return new Text(Messages.get(messageKey), Option.of(Theme.Classes.BOLD));
        }
    }

    private final Consumer<Msg> dispatch;
    private final Keymap keymap;
    private final ListElement<?> list;
    // The help the rows show, as of the last render; none while it is closed.
    private Option<HelpPopup> shownPopup = Option.none();
    private List<Row> rows = List.empty();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public HelpDialog(Consumer<Msg> dispatch) {
        this(dispatch, Keymap.standard());
    }

    HelpDialog(Consumer<Msg> dispatch, Keymap keymap) {
        this.dispatch = dispatch;
        this.keymap = keymap;
        this.list = list()
                .id(ID)
                .focusable()
                .highlightSymbol("")
                .onKeyEvent(this::handleKey);
    }

    /**
     * The popup, while the model asks for the keybindings of a panel: the keys of that panel, then the
     * global ones.
     */
    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        Option<HelpPopup> open = model.popup(HelpPopup.class).filter(popup -> keymap.titleKey(popup.scopeId()).isDefined());
        show(open);
        return open.map(popup -> {
            int widest = rows.map(HelpDialog::line).map(String::length).max().getOrElse(0);
            DialogElement frame = DialogFrame.withHint(
                            Messages.get("dialog.help.title", Messages.get(keymap.titleKey(popup.scopeId()).get())),
                            Math.max(WIDTH, widest + DialogFrame.FRAME), "dialog.help.hint",
                            List.of(list.constraint(Constraint.fill())))
                    // The rows, the blank line and the hint, plus the border.
                    .length(rows.size() + 2 + 2);
            return new WithDetails(frame, selected().flatMap(HelpDialog::details),
                    rows.flatMap(row -> row instanceof Key key ? details(key) : Option.none()));
        });
    }

    /**
     * Shows the rows of {@code open}, from its first key when it was not open before.
     */
    private void show(Option<HelpPopup> open) {
        if (!open.equals(shownPopup)) {
            shownPopup = open;
            rows = open.map(this::rows).getOrElse(List.empty());
            list.elements(rows.map(HelpDialog::element).toJavaArray(StyledElement[]::new));
            list.selected(Math.max(0, rows.indexWhere(Key.class::isInstance)));
        }
    }

    /**
     * The keys of the panel of {@code popup} and what they mean, then the global keys.
     */
    private List<Row> rows(HelpPopup popup) {
        List<Keymap.Binding<?>> bindings = keymap.bindings(popup.scopeId());
        // A help opened without the actions of the keys runs none of them.
        List<Option<Msg>> actions = popup.actions().padTo(bindings.size(), Option.none());
        return bindings.zipWith(actions, (binding, action) -> (Row) new Key(binding.label(), binding.descriptionKey(),
                        binding::matches, action))
                .appendAll(legend(popup.scopeId()))
                .append(new Text(""))
                .append(Text.heading("dialog.help.global"))
                .appendAll(keymap.globals().map(global -> new Key(global.label(), global.descriptionKey(),
                        event -> global.handle(event).isDefined(), Option.of(global.action()))));
    }

    /**
     * What the symbols of the panel {@code scopeId} mean, under a heading; nothing for a panel without any.
     */
    private static List<Row> legend(String scopeId) {
        return LEGENDS.get(scopeId)
                .map(symbols -> List.<Row>of(new Text(""), Text.heading("dialog.help.legend"))
                        .appendAll(symbols.map(symbol -> new Text(line(symbol._1, symbol._2)))))
                .getOrElse(List.empty());
    }

    private static StyledElement<?> element(Row row) {
        StyledElement<?> line = text(line(row));
        return switch (row) {
            case Key key -> key.action().isDefined() ? line : line.addClass(Theme.Classes.DISABLED);
            case Text text -> text.style().<StyledElement<?>>map(line::addClass).getOrElse(line);
        };
    }

    private static String line(Row row) {
        return switch (row) {
            case Key key -> line(key.label(), key.descriptionKey());
            case Text text -> text.text();
        };
    }

    private static String line(String key, String descriptionKey) {
        return KEY_FORMAT.formatted(key, Messages.get(descriptionKey));
    }

    private static Option<String> details(Key key) {
        return Messages.find(key.descriptionKey() + DETAILS_SUFFIX);
    }

    /**
     * The highlighted key, if the highlighted row is one.
     */
    private Option<Key> selected() {
        return rows.lift().apply(list.selected()).flatMap(row -> row instanceof Key key ? Option.of(key) : Option.none());
    }

    private EventResult handleKey(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE || Keymap.HELP_KEY.equals(event.string())) {
            dispatch.accept(new HelpMsg.Closed());
            return EventResult.HANDLED;
        }
        if (event.isConfirm()) {
            selected().forEach(this::run);
            return EventResult.HANDLED;
        }
        if (event.matches(Actions.MOVE_UP) || event.matches(Actions.MOVE_DOWN)) {
            move(event.matches(Actions.MOVE_UP) ? -1 : 1);
            return EventResult.HANDLED;
        }
        // The keys of the panel come first, as they are listed first.
        Option<Key> pressed = rows.iterator()
                .flatMap(row -> row instanceof Key key ? Option.of(key) : Option.none())
                .find(key -> key.matches().test(event));
        pressed.forEach(this::run);
        return pressed.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    /**
     * Highlights the next key in {@code direction}, skipping the texts; stays put at either end.
     */
    private void move(int direction) {
        int from = list.selected();
        Iterator<Integer> next = direction < 0
                ? Iterator.iterate(from - 1, i -> i - 1).takeWhile(i -> i >= 0)
                : Iterator.iterate(from + 1, i -> i + 1).takeWhile(i -> i < rows.size());
        next.find(i -> rows.get(i) instanceof Key).forEach(list::selected);
    }

    /**
     * Runs {@code key}, unless it does nothing for what is selected in the panel.
     */
    private void run(Key key) {
        // Quitting is up to the app, which only sees the message as is.
        key.action().forEach(msg -> dispatch.accept(msg instanceof Msg.Quit ? msg : new HelpMsg.Chosen(msg)));
    }
}
