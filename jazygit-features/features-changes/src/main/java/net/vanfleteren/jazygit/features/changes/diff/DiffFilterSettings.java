package net.vanfleteren.jazygit.features.changes.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Codec;
import net.vanfleteren.jazygit.kernel.settings.Decoded;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Part;
import net.vanfleteren.jazygit.kernel.state.diff.HunkFilters;

/**
 * Where the diff filters are kept (ADR 144, 184): the filters the user set and those enabled in the settings, and whether
 * all are switched off for this session in a part of the model. The kernel reads them as {@link HunkFilters}.
 */
public final class DiffFilterSettings {

    /**
     * The ids of the diff filters enabled, each once, in the order they were enabled.
     */
    public static final SettingKey<List<String>> ENABLED =
            SettingKey.of("ui.diff-filters", List.empty(), Codec.whole(DiffFilterSettings::ids, List::toJavaList));
    /**
     * The diff filters the user set; as the editors, only the user writes them.
     */
    public static final SettingKey<FilterDefinitions> DEFINITIONS =
            SettingKey.userOwned(FilterDefinitions.KEY, FilterDefinitions.NONE, Codec.readOnly(value -> {
                FilterDefinitions.Read read = FilterDefinitions.read(value, false);
                return new Decoded<>(Option.some(read.filters()), read.invalid());
            }));
    /**
     * Whether the diff filters are all switched off for this session, whichever are enabled.
     */
    public static final Part<Boolean> OFF = Part.of("changes.diff.filters-off", false);

    private DiffFilterSettings() {}

    /**
     * The diff filters of {@code model} to choose from, those enabled, and whether they are switched off.
     */
    public static DiffFilters filters(Model model) {
        Settings settings = model.preferences().settings();
        return DiffFilters.of(settings.get(DEFINITIONS), settings.get(ENABLED), model.part(OFF));
    }

    /**
     * The diff filters of {@code model} as the kernel hides hunks with them.
     */
    public static HunkFilters hiding(Model model) {
        DiffFilters filters = filters(model);
        return new HunkFilters(filters.enabledFilters(), filters.off());
    }

    /**
     * The ids {@code value} holds, if it is an array of text: each once, in its order.
     */
    private static Option<List<String>> ids(Object value) {
        return Option.of(value)
                .filter(java.util.List.class::isInstance)
                .map(array -> List.ofAll((java.util.List<?>) array))
                .filter(items -> items.forAll(String.class::isInstance))
                .map(items -> items.map(String.class::cast).distinct());
    }
}
