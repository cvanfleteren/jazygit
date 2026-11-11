package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * One of the settings, by its key in the settings file: what it is in a {@link Settings}, and how one takes it over
 * from another. A read of the file applies the settings one by one, as they changed in it (ADR 111).
 *
 * @param key   its key in the settings file, as {@link Settings#read} reports it when it is not valid
 * @param value what it is in some settings
 * @param take  the first settings, with it as the second has it
 */
record Setting(String key, Function<Settings, Object> value, BiFunction<Settings, Settings, Settings> take) {

    /**
     * Every setting the settings file holds.
     */
    static final List<Setting> ALL = List.of(
            new Setting(Settings.THEME, Settings::theme, (to, from) -> to.withTheme(from.theme())),
            new Setting(
                    Settings.GH_EXECUTABLE,
                    Settings::ghExecutable,
                    (to, from) -> to.withGhExecutable(from.ghExecutable())),
            new Setting(
                    Settings.REPOSITORY_WIDTH,
                    Settings::repositoryWidth,
                    (to, from) -> to.withRepositoryWidth(from.repositoryWidth())),
            new Setting(
                    Settings.PULL_REQUESTS_WIDTH,
                    Settings::pullRequestsWidth,
                    (to, from) -> to.withPullRequestsWidth(from.pullRequestsWidth())),
            new Setting(Settings.DIFF_STYLE, Settings::diffStyle, (to, from) -> to.withDiffStyle(from.diffStyle())),
            new Setting(Settings.DIFF_SYNTAX, Settings::diffSyntax, (to, from) -> to.withDiffSyntax(from.diffSyntax())),
            new Setting(Settings.DIFF_WORDS, Settings::diffWords, (to, from) -> to.withDiffWords(from.diffWords())),
            new Setting(
                    Settings.REPOSITORY_HEIGHTS,
                    Settings::repositoryHeights,
                    (to, from) -> to.withRepositoryHeights(from.repositoryHeights())),
            new Setting(
                    Settings.PULL_REQUESTS_HEIGHTS,
                    Settings::pullRequestsHeights,
                    (to, from) -> to.withPullRequestsHeights(from.pullRequestsHeights())),
            new Setting(Settings.ANIMATIONS, Settings::animations, (to, from) -> to.withAnimations(from.animations())),
            new Setting(Settings.BROWSERS, Settings::browsers, (to, from) -> to.withBrowsers(from.browsers())),
            // An entry of the editors that is wrong is left out, and reported; the others still apply. Only editors
            // that are no array at all are not valid as a whole.
            new Setting(Editors.KEY, Settings::editors, (to, from) -> to.withEditors(from.editors())));

    /**
     * Whether {@code first} and {@code second} differ in it.
     */
    boolean differs(Settings first, Settings second) {
        return !value.apply(first).equals(value.apply(second));
    }
}
