package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.collection.List;

/**
 * The settings a store reads and writes, which the app gives it (ADR 163): the kernel's own, and in time each
 * feature's.
 *
 * @param all each setting, in the order a new file has them
 */
public record SettingKeys(List<SettingKey<?>> all) {

    /**
     * The kernel's own settings.
     */
    public static final SettingKeys KERNEL = new SettingKeys(List.of(
            Settings.THEME,
            Settings.GH_EXECUTABLE,
            Settings.REPOSITORY_WIDTH,
            Settings.PULL_REQUESTS_WIDTH,
            Settings.DIFF_STYLE,
            Settings.DIFF_SYNTAX,
            Settings.DIFF_WORDS,
            Settings.REPOSITORY_HEIGHTS,
            Settings.PULL_REQUESTS_HEIGHTS,
            Settings.ANIMATIONS,
            Settings.AUTO_REFRESH,
            Settings.AUTO_FETCH,
            Settings.BROWSERS,
            Settings.EDITORS,
            Settings.DIFF_FILTERS,
            Settings.FILTER_DEFINITIONS,
            Settings.AGENTS));

    /**
     * These keys, then {@code more}, as the app gathers the kernel's and each feature's.
     */
    public SettingKeys and(Iterable<? extends SettingKey<?>> more) {
        return new SettingKeys(all.appendAll(more));
    }

    /**
     * The paths that clash: one that two keys have, or one that is inside another's, as {@code ui} is in
     * {@code ui.theme}, so that writing one would change the other. None when the keys can all be kept.
     */
    public List<String> clashes() {
        List<String> paths = all.map(SettingKey::path);
        return paths.zipWithIndex()
                .filter(path -> paths.zipWithIndex()
                        .exists(other -> !other._2.equals(path._2)
                                && (other._1.equals(path._1) || path._1.startsWith(other._1 + "."))))
                .map(path -> path._1)
                .distinct();
    }
}
