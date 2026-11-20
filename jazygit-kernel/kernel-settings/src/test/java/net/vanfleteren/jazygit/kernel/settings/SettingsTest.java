package net.vanfleteren.jazygit.kernel.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * The table of settings: what a setting keeps in the file, it reads back.
 */
class SettingsTest {

    private static final SettingKeys KERNEL = SettingKeys.KERNEL;

    /**
     * Settings with each setting the app writes away from its default.
     */
    private static final Settings CHANGED = Settings.DEFAULT
            .withTheme(ThemeName.DARCULA)
            .withRepositoryWidth(40)
            .withPullRequestsWidth(50)
            .withDiffStyle(DiffStyle.PLAIN)
            .withDiffSyntax(false)
            .withDiffWords(false)
            .withRepositoryHeights(List.of(20, 20, 20))
            .withPullRequestsHeights(List.of(40, 30))
            .withAnimations(false)
            .withAutoRefresh(false)
            .withAutoFetch(false)
            .withDiffFilters(List.of("java-imports", "lockfiles"));

    @Test
    void eachSettingTheAppWritesIsReadBackAsItWas() {
        Settings.Read read = Settings.read(KERNEL, key -> found(CHANGED.entries(KERNEL), List.of(key.split("\\."))));

        assertEquals(CHANGED, read.settings());
        assertTrue(read.invalid().isEmpty());
    }

    @Test
    void eachSettingDiffersFromTheDefaultInTheChangedSettings() {
        List<String> same = KERNEL.all()
                .filter(key -> !key.changed(Settings.DEFAULT, CHANGED))
                .map(SettingKey::path);

        // Only the user writes the diff filters.
        assertEquals(List.of(FilterDefinitions.KEY), same);
    }

    @Test
    void theDefaultsKeepOnlyTheThemeInTheFile() {
        assertEquals(
                List.of(List.of("ui", "theme")),
                Settings.DEFAULT.entries(KERNEL).keySet().toList());
    }

    @Test
    void aSettingSetBackToItsDefaultHasNoEntry() {
        Settings back = CHANGED.withRepositoryWidth(Settings.DEFAULT.repositoryWidth());

        assertTrue(CHANGED.entries(KERNEL).containsKey(List.of("ui", "repository-width")));
        assertFalse(back.entries(KERNEL).containsKey(List.of("ui", "repository-width")));
    }

    @Test
    void aSettingIsTakenOverOnItsOwn() {
        Settings taken = Settings.DIFF_STYLE.take(Settings.DEFAULT, CHANGED);

        assertEquals(Settings.DEFAULT.withDiffStyle(DiffStyle.PLAIN), taken);
    }

    @Test
    void aSettingSetBackToItsInitialValueIsNotHeld() {
        Settings back = Settings.DEFAULT.withAnimations(false).withAnimations(true);

        assertEquals(Settings.DEFAULT, back);
        assertTrue(back.held().isEmpty());
    }

    @Test
    void theOrderSettingsAreSetInDoesNotMatter() {
        assertEquals(
                Settings.DEFAULT.withAnimations(false).withTheme(ThemeName.LIGHT),
                Settings.DEFAULT.withTheme(ThemeName.LIGHT).withAnimations(false));
    }

    @Test
    void aSettingOfAnotherOwnerIsHeldAsTheKernelsAre() {
        SettingKey<Boolean> mine = SettingKey.of("mine.on", false, Codec.bool());
        Settings on = Settings.DEFAULT.with(mine, true);

        assertTrue(on.get(mine));
        assertFalse(Settings.DEFAULT.get(mine));
        assertEquals(Settings.DEFAULT, on.with(mine, false));
        assertEquals(
                LinkedHashMap.of(List.of("mine", "on"), (Object) true), on.entries(new SettingKeys(List.of(mine))));
    }

    @Test
    void anUnsetOptionalSettingKeepsNothing() {
        Codec<Option<String>> codec = Codec.text(Option::some, text -> text).optional();

        assertTrue(codec.encode("github.executable", Option.none()).isEmpty());
        assertEquals(
                List.of(Tuple.of(List.of("github", "executable"), (Object) "/opt/gh")),
                codec.encode("github.executable", Option.some("/opt/gh")).toList());
    }

    @Test
    void aValueThatIsNotValidAsAWholeIsWrongAtItsKey() {
        assertEquals(
                new Decoded<>(Option.none(), List.of("ui.animations")),
                Codec.bool().decode("ui.animations", "yes"));
        assertEquals(
                new Decoded<>(Option.some(false), List.empty()), Codec.bool().decode("ui.animations", false));
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
