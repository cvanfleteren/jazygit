package net.vanfleteren.jazygit.kernel.state.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Codec;
import net.vanfleteren.jazygit.kernel.settings.Decoded;
import net.vanfleteren.jazygit.kernel.settings.Entries;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.TableCodec;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.preferences.SaveSettings;
import org.junit.jupiter.api.Test;

/**
 * Editing a list of the settings from the settings panel (ADR 164): the entries are added, edited, deleted and moved,
 * each change saved, and the form saves an entry only when nothing wrong with it blocks that.
 */
class ListChangeTest {

    /**
     * A tool: a name, a shortcut, and whether it runs in the terminal.
     */
    record Tool(String name, Option<Character> shortcut, boolean terminal) {

        static Tool named(String name) {
            return new Tool(name, Option.none(), false);
        }
    }

    private static final TableCodec<Tool> CODEC = new TableCodec<>() {
        @Override
        public List<String> fields() {
            return List.of("name");
        }

        @Override
        public Decoded<Tool> decode(String at, LinkedHashMap<String, Object> table) {
            return new Decoded<>(table.get("name").map(String::valueOf).map(Tool::named), List.empty());
        }

        @Override
        public LinkedHashMap<String, Object> encode(Tool tool) {
            return LinkedHashMap.of("name", tool.name());
        }
    };

    private static final SettingKey<Entries<Tool>> TOOLS = SettingKey.of("tools", Entries.none(), Codec.tables(CODEC));

    private static final Form<Tool> FORM = new Form<>(
            Tool.named(""),
            List.of(
                    new Field.Text<>(
                            "name", Tool::name, (tool, name) -> new Tool(name, tool.shortcut(), tool.terminal())),
                    new Field.Letter<>(
                            "shortcut", Tool::shortcut, (tool, key) -> new Tool(tool.name(), key, tool.terminal())),
                    new Field.Toggle<>(
                            "terminal", Tool::terminal, (tool, on) -> new Tool(tool.name(), tool.shortcut(), on))),
            (tool, others) -> Option.when(tool.name().isBlank(), FieldProblem.error("name", "empty"))
                    .toList()
                    .appendAll(Option.when(
                            others.exists(other -> other.name().equals(tool.name())),
                            FieldProblem.error("name", "taken")))
                    .appendAll(Option.when(tool.shortcut().contains('x'), FieldProblem.warning("shortcut", "odd"))));

    private static final ListSetting<Tool> LIST = new ListSetting<>(TOOLS, "tools", Tool::name, FORM);

    private static final int NAME = 0;
    private static final int SHORTCUT = 1;
    private static final int TERMINAL = 2;

    /**
     * The settings panel open, with {@code tools} in the settings.
     */
    private static Model panel(Entries<Tool> tools) {
        Model model = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().with(TOOLS, tools)));
        return model.openPopup(new SettingsPanelPopup(model.preferences().settings()));
    }

    private static Model listed(Entries<Tool> tools) {
        return ListChange.update(panel(tools), new ListMsg.Opened(LIST, 'i')).model();
    }

    private static Model after(Model model, ListMsg... msgs) {
        return List.of(msgs)
                .foldLeft(model, (now, msg) -> ListChange.update(now, msg).model());
    }

    private static Model typed(Model model, String text) {
        return model.popup(FieldPopup.class)
                .map(popup -> model.openPopup(popup.withText(text)))
                .getOrElse(model);
    }

    private static final Entries<Tool> TWO = Entries.of(List.of(Tool.named("vim"), Tool.named("idea")));

    @Test
    void theListOpensFromThePanelAndGoesBackToItsRowWithTheSettingsAsTheyAreNow() {
        Model listed = listed(TWO);
        SettingsPanelPopup panel = listed.popup(ListPopup.class).get().panel();
        Model changed = ListChange.update(listed, new ListMsg.Deleted(0)).model();

        Model back = ListChange.update(changed, new ListMsg.Closed()).model();

        assertEquals(Option.some('i'), panel.opensAt());
        assertEquals(Option.of(panel.withSettings(changed.preferences().settings())), back.popup());
    }

    @Test
    void anEntryAddedInTheFormIsSavedAtTheEndOfTheList() {
        Model form = after(listed(TWO), new ListMsg.AddRequested(), new ListMsg.FieldPicked(NAME));
        Settings kept = form.preferences().settings();

        Next saved = ListChange.update(after(typed(form, "code"), new ListMsg.FieldEntered()), new ListMsg.FormSaved());

        Entries<Tool> three = TWO.added(Tool.named("code"));
        assertEquals(three, saved.model().preferences().settings().get(TOOLS));
        assertEquals(List.<Cmd>of(new SaveSettings(kept, kept.with(TOOLS, three))), saved.cmds());
        assertEquals(
                Option.of(new ListPopup(
                        LIST, listed(TWO).popup(ListPopup.class).get().panel().withSettings(kept.with(TOOLS, three)))),
                saved.model().popup());
        assertEquals(ListCursor.START.at(2), saved.model().listCursor());
    }

    @Test
    void anEntryWithAProblemThatBlocksIsNotSaved() {
        Model form = after(listed(TWO), new ListMsg.EditRequested(1), new ListMsg.FieldPicked(NAME));
        Model taken = after(typed(form, "vim"), new ListMsg.FieldEntered());

        Next refused = ListChange.update(taken, new ListMsg.FormSaved());

        assertEquals(taken, refused.model());
        assertEquals(List.empty(), refused.cmds());
        assertEquals(
                List.of(FieldProblem.error("name", "taken")),
                taken.popup(FormPopup.class).get().problems(TWO));
    }

    @Test
    void anEntryIsSavedWithAWarning() {
        Model form = after(listed(TWO), new ListMsg.EditRequested(0), new ListMsg.FieldPicked(SHORTCUT));

        Model saved = after(typed(form, "xyz"), new ListMsg.FieldEntered(), new ListMsg.FormSaved());

        assertEquals(
                Option.some(new Tool("vim", Option.some('x'), false)),
                saved.preferences().settings().get(TOOLS).at(0).flatMap(Entries.Entry::value));
    }

    @Test
    void aShortcutLeftEmptyIsNone() {
        Model form = after(
                listed(Entries.of(List.of(new Tool("vim", Option.some('v'), false)))),
                new ListMsg.EditRequested(0),
                new ListMsg.FieldPicked(SHORTCUT));

        Model entered = after(typed(form, "  "), new ListMsg.FieldEntered());

        assertEquals(
                new Tool("vim", Option.none(), false),
                entered.popup(FormPopup.class).get().draft());
    }

    @Test
    void aToggleTurnsInTheFormWithoutSaving() {
        Model form = after(listed(TWO), new ListMsg.EditRequested(0));

        Next toggled = ListChange.update(form, new ListMsg.FieldPicked(TERMINAL));

        assertEquals(
                new Tool("vim", Option.none(), true),
                toggled.model().popup(FormPopup.class).get().draft());
        assertTrue(toggled.model().popup(FormPopup.class).get().holdsInput());
        assertEquals(List.empty(), toggled.cmds());
        assertEquals(TWO, toggled.model().preferences().settings().get(TOOLS));
    }

    @Test
    void cancellingTheFormGoesBackToTheListOnTheEntryWithoutSaving() {
        Model form = after(listed(TWO), new ListMsg.EditRequested(1), new ListMsg.FieldPicked(TERMINAL));

        Next cancelled = ListChange.update(form, new ListMsg.FormCancelled());

        assertEquals(listed(TWO).popup(), cancelled.model().popup());
        assertEquals(ListCursor.START.at(1), cancelled.model().listCursor());
        assertEquals(List.empty(), cancelled.cmds());
    }

    @Test
    void cancellingAFieldGoesBackToTheFormAsItWas() {
        Model form = after(listed(TWO), new ListMsg.EditRequested(0));
        Model typing =
                typed(ListChange.update(form, new ListMsg.FieldPicked(NAME)).model(), "nvim");

        Model cancelled = after(typing, new ListMsg.FieldCancelled());

        assertEquals(form.popup(), cancelled.popup());
    }

    @Test
    void anEditedEntryKeepsWhatItsTableHadThatTheAppDoesNotKnow() {
        Entries<Tool> kept = new Entries<>(
                List.of(new Entries.Entry.Valid<>(Tool.named("vim"), LinkedHashMap.of("since", (Object) 2024))));
        Model form = after(listed(kept), new ListMsg.EditRequested(0), new ListMsg.FieldPicked(NAME));

        Model saved = after(typed(form, "nvim"), new ListMsg.FieldEntered(), new ListMsg.FormSaved());

        assertEquals(
                new Entries<>(List.of(
                        new Entries.Entry.Valid<>(Tool.named("nvim"), LinkedHashMap.of("since", (Object) 2024)))),
                saved.preferences().settings().get(TOOLS));
    }

    @Test
    void anEntryTheAppCannotReadIsNotEdited() {
        Entries<Tool> wrong = new Entries<>(List.of(
                new Entries.Entry.Invalid<>(LinkedHashMap.of("nme", (Object) "vim"), List.of("tools[1].name"))));
        Model listed = listed(wrong);

        assertEquals(
                listed, ListChange.update(listed, new ListMsg.EditRequested(0)).model());
    }

    @Test
    void anEntryIsDeletedAndTheListStaysOnTheOneThatTookItsPlace() {
        Entries<Tool> three = TWO.added(Tool.named("code"));

        Next deleted = ListChange.update(listed(three), new ListMsg.Deleted(2));

        assertEquals(TWO, deleted.model().preferences().settings().get(TOOLS));
        assertEquals(ListCursor.START.at(1), deleted.model().listCursor());
        assertEquals(1, deleted.cmds().size());
    }

    @Test
    void anEntryMovesAndTheListFollowsIt() {
        Next moved = ListChange.update(listed(TWO), new ListMsg.Moved(1, -1));

        assertEquals(
                List.of("idea", "vim"),
                moved.model().preferences().settings().get(TOOLS).values().map(Tool::name));
        assertEquals(ListCursor.START.at(0), moved.model().listCursor());
    }

    @Test
    void anEntryDoesNotMovePastTheEndsOfTheList() {
        Model listed = listed(TWO);

        Next moved = ListChange.update(listed, new ListMsg.Moved(0, -1));

        assertEquals(listed, moved.model());
        assertEquals(List.empty(), moved.cmds());
    }
}
