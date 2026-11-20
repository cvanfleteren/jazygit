package net.vanfleteren.jazygit.kernel.ui.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Entries;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.settings.Field;
import net.vanfleteren.jazygit.kernel.state.settings.FieldPopup;
import net.vanfleteren.jazygit.kernel.state.settings.FieldProblem;
import net.vanfleteren.jazygit.kernel.state.settings.FormPopup;
import net.vanfleteren.jazygit.kernel.state.settings.ListMsg;
import net.vanfleteren.jazygit.kernel.state.settings.ListPopup;
import net.vanfleteren.jazygit.kernel.state.settings.ListSetting;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog.Cell;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog.Keyed;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog.Row;
import net.vanfleteren.jazygit.kernel.ui.widgets.TextInputDialog;

/**
 * The dialogs that edit a list of the settings (ADR 164): its entries, where {@code a} adds one, Enter edits the
 * highlighted one, {@code d} deletes it and {@code K} and {@code J} move it; the form of an entry, which shows what is
 * wrong with it next to each field and saves it with {@code s}; and the input of a field's value. Any list a feature
 * declares is edited with them, so none needs dialogs of its own; the settings panel shows them.
 */
public final class ListDialogs {

    /**
     * The entries of the list.
     */
    public static final PickDialog.Spec<ListPopup, Integer> LIST = new PickDialog.Spec<ListPopup, Integer>(
                    "settings-list",
                    ListPopup.class,
                    popup -> Messages.get(popup.list().titleKey()),
                    ListDialogs::entries,
                    ListMsg.EditRequested::new,
                    ListMsg.Closed::new,
                    Option.of("settingsPanel.list.empty"))
            .hintedBy("settingsPanel.list.hint")
            .withKeys(
                    new Keyed<>('a', highlighted -> Option.of(new ListMsg.AddRequested())),
                    new Keyed<>('d', highlighted -> highlighted.map(ListMsg.Deleted::new)),
                    new Keyed<>('K', highlighted -> highlighted.map(index -> new ListMsg.Moved(index, -1))),
                    new Keyed<>('J', highlighted -> highlighted.map(index -> new ListMsg.Moved(index, 1))));

    /**
     * The form of an entry.
     */
    @SuppressWarnings({"unchecked", "rawtypes"}) // The form of any entry is one popup type.
    public static final PickDialog.Spec<FormPopup<?>, Integer> FORM = new PickDialog.Spec<FormPopup<?>, Integer>(
                    "settings-form",
                    (Class<FormPopup<?>>) (Class) FormPopup.class,
                    ListDialogs::formTitle,
                    ListDialogs::fields,
                    ListMsg.FieldPicked::new,
                    ListMsg.FormCancelled::new,
                    Option.none())
            .hintedBy("settingsPanel.form.hint")
            .withKeys(new Keyed<>('s', highlighted -> Option.of(new ListMsg.FormSaved())));

    /**
     * The value of a field.
     */
    public static final TextInputDialog.Spec<FieldPopup> FIELD = new TextInputDialog.Spec<>(
            "settings-field",
            FieldPopup.class,
            popup -> Messages.get(
                    popup.form().list().form().fields().get(popup.field()).labelKey()),
            Option.none(),
            ListMsg.FieldEntered::new,
            ListMsg.FieldCancelled::new);

    /**
     * How a warning starts.
     */
    static final String WARNING = "⚠ ";

    private ListDialogs() {}

    private static List<Row<Integer>> entries(ListPopup popup) {
        return entryRows(popup.list(), popup.panel().settings());
    }

    /**
     * A row for each entry, which stands for where it is in the list: its name, or what is wrong in it when the app
     * cannot read it.
     */
    static <E> List<Row<Integer>> entryRows(ListSetting<E> list, Settings settings) {
        return settings.get(list.key())
                .all()
                .zipWithIndex()
                .map(entry -> new Row<>(
                        entry._2,
                        entry._1
                                .value()
                                .map(value -> List.of(Cell.of(list.name().apply(value))))
                                .getOrElse(() -> List.of(Cell.styled(
                                        WARNING
                                                + Messages.get(
                                                        "settingsPanel.list.wrong",
                                                        invalid(entry._1).mkString(", ")),
                                        Theme.Classes.ERROR)))));
    }

    private static <E> List<String> invalid(Entries.Entry<E> entry) {
        return entry instanceof Entries.Entry.Invalid<E> invalid ? invalid.wrong() : List.empty();
    }

    private static String formTitle(FormPopup<?> popup) {
        return title(popup);
    }

    private static <E> String title(FormPopup<E> popup) {
        String list = Messages.get(popup.list().titleKey());
        return popup.index().isEmpty()
                ? Messages.get("settingsPanel.form.new", list)
                : Messages.get(
                        "settingsPanel.form.title", list, popup.list().name().apply(popup.draft()));
    }

    private static List<Row<Integer>> fields(FormPopup<?> popup) {
        return rows(popup);
    }

    /**
     * A row for each field, which stands for where it is among them: its label, its value, and what is wrong with it,
     * if anything.
     */
    static <E> List<Row<Integer>> rows(FormPopup<E> popup) {
        List<FieldProblem> problems =
                popup.problems(popup.panel().settings().get(popup.list().key()));
        return popup.list()
                .form()
                .fields()
                .zipWithIndex()
                .map(field -> new Row<>(
                        field._2,
                        List.of(Cell.of(Messages.get(field._1.labelKey())), Cell.of(value(field._1, popup.draft())))
                                .appendAll(problems.filter(
                                                problem -> problem.fieldKey().equals(field._1.labelKey()))
                                        .map(ListDialogs::problem))));
    }

    private static <E> String value(Field<E> field, E entry) {
        return switch (field) {
            case Field.Text<E> text -> text.get().apply(entry);
            case Field.Toggle<E> toggle -> SettingsRows.onOff(toggle.get().test(entry));
            case Field.Letter<E> letter ->
                letter.get().apply(entry).map(String::valueOf).getOrElse(() -> Messages.get("settingsPanel.form.none"));
        };
    }

    private static Cell problem(FieldProblem problem) {
        return Cell.styled(
                WARNING + Messages.get(problem.messageKey(), problem.args().toJavaArray()),
                problem.blocks() ? Theme.Classes.ERROR : Theme.Classes.NOTICE);
    }
}
