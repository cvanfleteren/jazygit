package net.vanfleteren.jazygit.features.chrome.help;

import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.layout.Constraint;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.DialogElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Legends;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.kernel.ui.widgets.FilteredCursor;
import net.vanfleteren.jazygit.kernel.ui.widgets.ListKeys;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;
import net.vanfleteren.jazygit.kernel.ui.widgets.WithDetails;

/**
 * Popup listing the keys of a panel and what they do, under which the details of the highlighted key show when it
 * has any. A key is run with the arrow keys and Enter, or by pressing it; a key of the panel that does nothing for
 * what is selected in it is struck through. Escape or ? closes it. Whether it is open, and the highlighted key, live
 * in the {@link Model}.
 */
public final class HelpDialog implements PopupView {

    public static final String ID = "help";

    private static final int WIDTH = 70;

    // The key right-aligned in a column this many screen columns wide, then a space and the description.
    private static final int KEY_COLUMN = 12;

    // A key has details when the messages have this after the key of its description.
    private static final String DETAILS_SUFFIX = ".details";

    /**
     * What git's symbols in a panel mean, as the symbols and the message keys of their descriptions; the features add
     * what theirs mean after them ({@link Legends}, ADR 168).
     */
    private static final Map<String, List<Tuple2<String, String>>> LEGENDS = HashMap.of(
            Scopes.BRANCHES.id(),
            List.of(
                    Tuple.of(Messages.get("branchRow.gone"), "chrome.help.branches.legend.gone"),
                    Tuple.of("⇡2", "chrome.help.branches.legend.ahead"),
                    Tuple.of("⇣3", "chrome.help.branches.legend.behind"),
                    Tuple.of(Messages.get("branchRow.worktree.short"), "chrome.help.branches.legend.worktree")));

    private final Consumer<Msg> dispatch;
    private final Keymap keymap;
    private final ListElement<?> list;
    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public HelpDialog(Consumer<Msg> dispatch) {
        this(dispatch, Keymap.standard());
    }

    HelpDialog(Consumer<Msg> dispatch, Keymap keymap) {
        this.dispatch = dispatch;
        this.keymap = keymap;
        this.list = list().id(ID).focusable().highlightSymbol("");
    }

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                // Help is opened from a pane, which has the focus then.
                PopupSlot.returningToFocus(new HelpDialog(dispatch)));
    }

    /**
     * What the symbols of the panel {@code scopeId} mean, under a heading; nothing for a panel without any. Those of a
     * feature that is off in {@code model} are left out (ADR 171).
     */
    private static List<Row> legend(Model model, String scopeId) {
        List<Tuple2<String, String>> symbols = LEGENDS.get(scopeId)
                .getOrElse(List.empty())
                .appendAll(Legends.standard()
                        .of(scopeId)
                        .filter(legend -> legend.feature().forAll(model::on))
                        .map(legend -> Tuple.of(legend.symbols(), legend.descriptionKey())));
        return symbols.isEmpty()
                ? List.empty()
                : List.<Row>of(new Text(""), Text.heading("chrome.dialog.help.legend"))
                        .appendAll(symbols.map(symbol -> new Text(line(symbol._1, symbol._2))));
    }

    private static StyledElement<?> element(Row row) {
        StyledElement<?> line = text(line(row));
        return switch (row) {
            case Key key when key.action().isDefined() -> line;
            case Key ignore -> line.addClass(Theme.Classes.DISABLED);
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
        return TextWidth.alignRight(key, KEY_COLUMN) + " " + Messages.get(descriptionKey);
    }

    private static Option<String> details(Key key) {
        return Messages.find(key.descriptionKey() + DETAILS_SUFFIX);
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
        return model.popup(HelpPopup.class)
                .filter(popup -> keymap.titleKey(popup.scopeId()).isDefined())
                .map(popup -> render(model, popup, model.listCursor()));
    }

    private Element render(Model model, HelpPopup popup, ListCursor cursor) {
        List<Row> rows = rows(model, popup);
        // Until the user moves, the first key is highlighted; the help has no filter.
        FilteredCursor<Row> view = FilteredCursor.of(filter -> rows, Key.class::isInstance, cursor);
        list.elements(rows.map(HelpDialog::element).toJavaArray(StyledElement[]::new))
                .selected(view.selected())
                .onKeyEvent(event -> handleKey(view, event))
                .onMouseEvent(event -> ListKeys.wheel(
                        event, view.selected(), view.size(), view::highlightable, line -> highlight(view, line)));
        int widest = rows.map(HelpDialog::line).map(String::length).max().getOrElse(0);
        DialogElement frame = DialogFrame.withHint(
                        Messages.get(
                                "chrome.dialog.help.title",
                                Messages.get(keymap.titleKey(popup.scopeId()).get())),
                        Math.max(WIDTH, widest + DialogFrame.FRAME),
                        "chrome.dialog.help.hint",
                        List.of(list.constraint(Constraint.fill())))
                // The rows, the blank line and the hint, plus the border.
                .length(rows.size() + 2 + 2);
        return new WithDetails(
                frame,
                key(view).flatMap(HelpDialog::details),
                rows.filter(Key.class::isInstance).map(Key.class::cast).flatMap(HelpDialog::details));
    }

    /**
     * The keys of the panel of {@code popup} and what they mean, then the global keys; those of a feature that is off
     * in {@code model} are left out (ADR 171), and a key bound by several features that are on is listed once
     * (ADR 175).
     */
    private List<Row> rows(Model model, HelpPopup popup) {
        List<Keymap.Binding<?>> bindings = keymap.bindings(popup.scopeId());
        // A help opened without the actions of the keys runs none of them.
        List<Option<Msg>> actions = popup.actions().padTo(bindings.size(), Option.none());
        return bindings.zip(actions)
                .filter(key -> key._1().feature().forAll(model::on))
                // A key that several features bind, as two forges do, is listed once: as the first of them that is on
                // has it (ADR 175).
                .distinctBy(key -> key._1().key())
                .map(key -> (Row) new Key(key._1().label(), key._1().descriptionKey(), key._1()::matches, key._2()))
                .appendAll(legend(model, popup.scopeId()))
                .append(new Text(""))
                .append(Text.heading("chrome.dialog.help.global"))
                .appendAll(keymap.globals()
                        .filter(global -> global.feature().forAll(model::on))
                        .distinctBy(Keymap.Global::label)
                        .map(global -> new Key(
                                global.label(),
                                global.descriptionKey(),
                                event -> global.handle(event).isDefined(),
                                Option.of(global.action()))));
    }

    private EventResult handleKey(FilteredCursor<Row> view, KeyEvent event) {
        if (event.isCancel() || Keymap.HELP_KEY.equals(event.string())) {
            dispatch.accept(new PopupMsg.Dismissed(HelpPopup.class));
            return EventResult.HANDLED;
        }
        if (event.isConfirm()) {
            key(view).forEach(this::run);
            return EventResult.HANDLED;
        }
        Option<Integer> moved = ListKeys.move(event, view.selected(), view.size(), view::highlightable);
        if (moved.isDefined()) {
            moved.filter(line -> line != view.selected()).forEach(line -> highlight(view, line));
            return EventResult.HANDLED;
        }
        // The keys of the panel come first, as they are listed first.
        Option<Key> pressed = view.lines()
                .iterator()
                .filter(Key.class::isInstance)
                .map(Key.class::cast)
                .find(key -> key.matches().test(event));
        pressed.forEach(this::run);
        return pressed.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    private void highlight(FilteredCursor<Row> view, int line) {
        dispatch.accept(new PopupMsg.ListCursorChanged(view.at(line)));
    }

    /**
     * The highlighted key, if the highlighted row is one.
     */
    private static Option<Key> key(FilteredCursor<Row> view) {
        return view.highlighted().map(Key.class::cast);
    }

    /**
     * Runs {@code key}, unless it does nothing for what is selected in the panel.
     */
    private void run(Key key) {
        // Quitting is up to the model, which only sees the message as is.
        key.action().forEach(msg -> dispatch.accept(msg instanceof Msg.Quit ? msg : new HelpMsg.Chosen(msg)));
    }

    /**
     * A line of the list: a key, which can be highlighted, or a text around them.
     */
    private sealed interface Row {}

    /**
     * A key, with the message it sends if it does anything.
     *
     * @param matches whether an event is this key
     */
    private record Key(String label, String descriptionKey, Predicate<KeyEvent> matches, Option<Msg> action)
            implements Row {}

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
}
