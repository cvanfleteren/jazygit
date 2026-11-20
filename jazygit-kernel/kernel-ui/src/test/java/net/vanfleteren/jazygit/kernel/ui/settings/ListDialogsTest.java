package net.vanfleteren.jazygit.kernel.ui.settings;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.kernel.settings.Codec;
import net.vanfleteren.jazygit.kernel.settings.Decoded;
import net.vanfleteren.jazygit.kernel.settings.Entries;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.TableCodec;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;
import net.vanfleteren.jazygit.kernel.state.settings.Field;
import net.vanfleteren.jazygit.kernel.state.settings.FieldProblem;
import net.vanfleteren.jazygit.kernel.state.settings.Form;
import net.vanfleteren.jazygit.kernel.state.settings.FormPopup;
import net.vanfleteren.jazygit.kernel.state.settings.ListMsg;
import net.vanfleteren.jazygit.kernel.state.settings.ListPopup;
import net.vanfleteren.jazygit.kernel.state.settings.ListSetting;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;
import org.junit.jupiter.api.Test;

/**
 * The dialogs that edit a list of the settings (ADR 164): the entries, each by its name or by what is wrong in it,
 * with keys that act on the highlighted one, and the form, with what is wrong next to each field.
 */
class ListDialogsTest {

    record Tool(String name, boolean terminal) {}

    private static final TableCodec<Tool> CODEC = new TableCodec<>() {
        @Override
        public List<String> fields() {
            return List.of("name");
        }

        @Override
        public Decoded<Tool> decode(String at, LinkedHashMap<String, Object> table) {
            return new Decoded<>(table.get("name").map(name -> new Tool(String.valueOf(name), false)), List.empty());
        }

        @Override
        public LinkedHashMap<String, Object> encode(Tool tool) {
            return LinkedHashMap.of("name", tool.name());
        }
    };

    private static final SettingKey<Entries<Tool>> TOOLS = SettingKey.of("tools", Entries.none(), Codec.tables(CODEC));

    private static final ListSetting<Tool> LIST = new ListSetting<>(
            TOOLS,
            "settingsPanel.list.none",
            Tool::name,
            new Form<>(
                    new Tool("", false),
                    List.of(
                            new Field.Text<>("settingsPanel.on", Tool::name, (tool, name) -> new Tool(name, false)),
                            new Field.Toggle<>(
                                    "settingsPanel.off", Tool::terminal, (tool, on) -> new Tool(tool.name(), on))),
                    (tool, others) -> Option.when(
                                    tool.name().isEmpty(),
                                    FieldProblem.error("settingsPanel.on", "settingsPanel.reading"))
                            .toList()));

    private static final Entries<Tool> TOOLS_READ = new Entries<>(List.of(
            Entries.Entry.Valid.of(new Tool("vim", true)),
            new Entries.Entry.Invalid<>(LinkedHashMap.of("nme", (Object) "x"), List.of("tools[2].name"))));

    private final java.util.List<Msg> dispatched = new ArrayList<>();

    private static Model with(Entries<Tool> tools, java.util.function.Function<SettingsPanelPopup, Popup> open) {
        Model model = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().with(TOOLS, tools)));
        return model.openPopup(
                open.apply(new SettingsPanelPopup(model.preferences().settings())));
    }

    @Test
    void theEntriesShowByNameOrByWhatIsWrongInThem() {
        assertEquals(
                List.of(List.of("vim"), List.of("⚠ not valid: name")),
                ListDialogs.entryRows(
                                LIST,
                                TestModels.loaded().preferences().settings().with(TOOLS, TOOLS_READ))
                        .map(row -> row.cells().map(PickDialog.Cell::text)));
    }

    @Test
    void whatIsWrongInAnEntryDoesNotNameWhereItWasOnceItMoved() {
        Entries<Tool> moved = TOOLS_READ.moved(1, -1);

        assertEquals(
                List.of(List.of("⚠ not valid: name"), List.of("vim")),
                ListDialogs.entryRows(
                                LIST,
                                TestModels.loaded().preferences().settings().with(TOOLS, moved))
                        .map(row -> row.cells().map(PickDialog.Cell::text)));
    }

    @Test
    void theRowOfThePanelNamesTheEntries() {
        Choice<SettingsPanelPopup> row = SettingsRows.list('i', "settingsPanel.list.wrong", LIST);
        SettingsPanelPopup panel = new SettingsPanelPopup(
                TestModels.loaded().preferences().settings().with(TOOLS, TOOLS_READ));

        assertEquals("not valid: vim, ⚠ one not valid", row.label().apply(panel));
        assertEquals(new ListMsg.Opened(LIST, 'i'), row.msg().get());
    }

    @Test
    void theFormShowsEachFieldWithWhatIsWrongWithIt() {
        FormPopup<Tool> form = FormPopup.of(
                LIST,
                new SettingsPanelPopup(TestModels.loaded().preferences().settings()),
                Option.none(),
                new Tool("", true));

        assertEquals(
                List.of(List.of("on", "", "⚠ reading…"), List.of("off", "on")),
                ListDialogs.rows(form).map(row -> row.cells().map(PickDialog.Cell::text)));
    }

    @Test
    void theKeysOfTheListActOnTheHighlightedEntry() throws Exception {
        PickDialog<ListPopup, Integer> dialog = new PickDialog<>(ListDialogs.LIST, dispatched::add);
        Model model = with(TOOLS_READ, panel -> new ListPopup(LIST, panel));
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(dialog, model))) {
            SettledPilot.focus(runner, "settings-list");
            String screen = RenderedText.ofLarge(runner, () -> view(dialog, model));
            assertTrue(screen.contains("(a) add   (enter) edit   (d) delete   (K/J) move   (esc) back"), screen);
            var pilot = SettledPilot.of(runner);
            pilot.press('d');
            pilot.press('J');
            pilot.press('a');
            pilot.press(KeyCode.ENTER);
            pilot.press(KeyCode.ESCAPE);
            pilot.pause();
        }

        assertEquals(
                java.util.List.of(
                        new ListMsg.Deleted(0),
                        new ListMsg.Moved(0, 1),
                        new ListMsg.AddRequested(),
                        new ListMsg.EditRequested(0),
                        new ListMsg.Closed()),
                dispatched);
    }

    @Test
    void theFormIsSavedWithItsKey() throws Exception {
        @SuppressWarnings({"unchecked", "rawtypes"})
        PickDialog<FormPopup<?>, Integer> dialog = new PickDialog<>(ListDialogs.FORM, dispatched::add);
        Model model = with(TOOLS_READ, panel -> FormPopup.of(LIST, panel, Option.some(0), new Tool("vim", true)));
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(dialog, model))) {
            SettledPilot.focus(runner, "settings-form");
            RenderedText.ofLarge(runner, () -> view(dialog, model));
            var pilot = SettledPilot.of(runner);
            pilot.press(KeyCode.ENTER);
            pilot.press('s');
            pilot.press(KeyCode.ESCAPE);
            pilot.pause();
        }

        assertEquals(
                java.util.List.of(new ListMsg.FieldPicked(0), new ListMsg.FormSaved(), new ListMsg.FormCancelled()),
                dispatched);
    }

    private static Element view(PickDialog<?, ?> dialog, Model model) {
        return stack(text("background"), dialog.render(model).get());
    }
}
