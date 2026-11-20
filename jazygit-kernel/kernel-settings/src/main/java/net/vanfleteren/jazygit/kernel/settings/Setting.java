package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.lib.lens.Lens;

/**
 * One of the settings: its {@link SettingKey}, and where it is in a {@link Settings}. A read of the file applies the
 * settings one by one, as they changed in it (ADR 111).
 *
 * <p>Adding a setting is its {@link SettingKey}, a component of {@link Settings}, and a line in {@link #ALL}.
 *
 * @param key  the setting, as declared
 * @param lens where it is in some settings
 * @param <T>  what it is
 */
record Setting<T>(SettingKey<T> key, Lens<Settings, T> lens) {

    /**
     * Every setting the settings file holds, in the order a new file has them.
     */
    static final List<Setting<?>> ALL = List.of(
            new Setting<>(Settings.THEME, Lens.of(Settings::theme, Settings::withTheme)),
            new Setting<>(Settings.GH_EXECUTABLE, Lens.of(Settings::ghExecutable, Settings::withGhExecutable)),
            new Setting<>(Settings.REPOSITORY_WIDTH, Lens.of(Settings::repositoryWidth, Settings::withRepositoryWidth)),
            new Setting<>(
                    Settings.PULL_REQUESTS_WIDTH,
                    Lens.of(Settings::pullRequestsWidth, Settings::withPullRequestsWidth)),
            new Setting<>(Settings.DIFF_STYLE, Lens.of(Settings::diffStyle, Settings::withDiffStyle)),
            new Setting<>(Settings.DIFF_SYNTAX, Lens.of(Settings::diffSyntax, Settings::withDiffSyntax)),
            new Setting<>(Settings.DIFF_WORDS, Lens.of(Settings::diffWords, Settings::withDiffWords)),
            new Setting<>(
                    Settings.REPOSITORY_HEIGHTS, Lens.of(Settings::repositoryHeights, Settings::withRepositoryHeights)),
            new Setting<>(
                    Settings.PULL_REQUESTS_HEIGHTS,
                    Lens.of(Settings::pullRequestsHeights, Settings::withPullRequestsHeights)),
            new Setting<>(Settings.ANIMATIONS, Lens.of(Settings::animations, Settings::withAnimations)),
            new Setting<>(Settings.AUTO_REFRESH, Lens.of(Settings::autoRefresh, Settings::withAutoRefresh)),
            new Setting<>(Settings.AUTO_FETCH, Lens.of(Settings::autoFetch, Settings::withAutoFetch)),
            new Setting<>(Settings.BROWSERS, Lens.of(Settings::browsers, Settings::withBrowsers)),
            new Setting<>(Settings.EDITORS, Lens.of(Settings::editors, Settings::withEditors)),
            new Setting<>(Settings.DIFF_FILTERS, Lens.of(Settings::diffFilters, Settings::withDiffFilters)),
            new Setting<>(
                    Settings.FILTER_DEFINITIONS, Lens.of(Settings::filterDefinitions, Settings::withFilterDefinitions)),
            new Setting<>(Settings.AGENTS, Lens.of(Settings::agents, Settings::withAgents)));

    /**
     * Its key in the settings file, as a problem with it names it.
     */
    String path() {
        return key.path();
    }

    /**
     * {@code read} with this setting as {@code value} finds it in the file, and with the keys of what is wrong in it;
     * as it was when {@code value} finds nothing.
     */
    Settings.Read readInto(Settings.Read read, Function<String, Option<Object>> value) {
        return value.apply(path())
                .map(found -> key.codec().decode(path(), found))
                .map(decoded -> new Settings.Read(
                        decoded.value()
                                .map(found -> lens.map(read.settings(), held -> found))
                                .getOrElse(read.settings()),
                        read.invalid().appendAll(decoded.invalid())))
                .getOrElse(read);
    }

    /**
     * How {@code settings} keep this setting in the settings file: nothing when it is never kept, nor when it is at
     * its initial value, unless it is always kept.
     */
    Map<List<String>, Object> entriesOf(Settings settings) {
        return lens.get(settings)
                .filter(value -> switch (key.kept()) {
                    case ALWAYS -> true;
                    case WHEN_CHANGED -> !key.initial().equals(value);
                    case NEVER -> false;
                })
                .map(value -> key.codec().encode(path(), value))
                .getOrElse(LinkedHashMap.empty());
    }

    /**
     * {@code to}, with this setting as {@code from} has it.
     */
    Settings take(Settings to, Settings from) {
        return lens.get(from).map(value -> lens.map(to, held -> value)).getOrElse(to);
    }

    /**
     * Whether {@code first} and {@code second} differ in it.
     */
    boolean differs(Settings first, Settings second) {
        return !lens.get(first).equals(lens.get(second));
    }
}
