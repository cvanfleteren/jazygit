package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * The table of settings: what a setting keeps in the file, it reads back.
 */
class SettingTest {

    /**
     * Settings with each setting the app writes away from its default.
     */
    private static final Settings CHANGED = Settings.DEFAULT
            .withTheme(ThemeName.DARCULA)
            .withGhExecutable(Option.some("/opt/gh"))
            .withRepositoryWidth(40)
            .withPullRequestsWidth(50)
            .withDiffStyle(DiffStyle.PLAIN)
            .withDiffSyntax(false)
            .withDiffWords(false)
            .withRepositoryHeights(List.of(20, 20, 20))
            .withPullRequestsHeights(List.of(40, 30))
            .withAnimations(false)
            .withBrowsers(AccountBrowsers.NONE.with("github.com", "octocat", Option.some("firefox")));

    @Test
    void eachSettingTheAppWritesIsReadBackAsItWas() {
        Settings.Read read = Settings.read(key -> found(CHANGED.entries(), List.of(key.split("\\."))));

        assertEquals(CHANGED, read.settings());
        assertTrue(read.invalid().isEmpty());
    }

    @Test
    void eachSettingDiffersFromTheDefaultInTheChangedSettings() {
        List<String> same = Setting.ALL
                .filter(setting -> !setting.differs(Settings.DEFAULT, CHANGED))
                .map(Setting::key);

        // Only the user writes the editors.
        assertEquals(List.of(Editors.KEY), same);
    }

    @Test
    void theDefaultsKeepOnlyTheThemeInTheFile() {
        assertEquals(
                List.of(List.of("ui", "theme")),
                Settings.DEFAULT.entries().keySet().toList());
    }

    @Test
    void aSettingSetBackToItsDefaultHasNoEntry() {
        Settings back = CHANGED.withRepositoryWidth(Settings.DEFAULT.repositoryWidth());

        assertTrue(CHANGED.entries().containsKey(List.of("ui", "repository-width")));
        assertFalse(back.entries().containsKey(List.of("ui", "repository-width")));
    }

    @Test
    void aSettingIsTakenOverOnItsOwn() {
        Settings taken = Setting.ALL
                .find(setting -> setting.key().equals(Settings.DIFF_STYLE))
                .get()
                .take(Settings.DEFAULT, CHANGED);

        assertEquals(Settings.DEFAULT.withDiffStyle(DiffStyle.PLAIN), taken);
    }

    /**
     * What the file has at {@code path}, as it is read: a value, or a table of what is under it.
     */
    private static Option<Object> found(Map<List<String>, Object> entries, List<String> path) {
        return entries.get(path)
                .orElse(() -> Option.of(entries.keySet()
                                .toList()
                                .filter(key -> key.size() > path.size() && key.startsWith(path))
                                .map(key -> key.get(path.size()))
                                .distinct())
                        .filter(List::nonEmpty)
                        // The names under the path all lead somewhere.
                        .map(names -> names.toLinkedMap(name -> Tuple.of(
                                        name, found(entries, path.append(name)).get()))
                                .toJavaMap()));
    }
}
