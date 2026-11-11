package net.vanfleteren.jazygit.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.settings.SettingsStore.Applied;
import net.vanfleteren.jazygit.settings.SettingsStore.Loaded;
import org.junit.jupiter.api.Test;

class LoadedTest {

    private static final Path FILE = Path.of("/config/jazygit/config.toml");
    private static final Loaded DEFAULT = Loaded.DEFAULT;

    private static Loaded read(Settings settings) {
        return new Loaded(settings, Option.none());
    }

    private static Loaded invalid(Settings settings, String... keys) {
        return new Loaded(settings, Option.of(new SettingsProblem.Invalid(FILE, List.of(keys))));
    }

    @Test
    void aSettingEditedInTheFileIsApplied() {
        Loaded now = read(Settings.DEFAULT.withTheme(ThemeName.DARCULA));

        Applied applied = now.appliedTo(Settings.DEFAULT, DEFAULT);

        assertEquals(Settings.DEFAULT.withTheme(ThemeName.DARCULA), applied.settings());
        assertEquals(HashSet.of(Settings.THEME), applied.changed());
        assertTrue(applied.themeChanged());
    }

    @Test
    void aSettingOnlyTheSessionHasStaysWhenTheFileDidNotChangeIt() {
        // The animations were turned off, but could not be saved.
        Settings inUse = Settings.DEFAULT.withAnimations(false);

        Applied applied = DEFAULT.appliedTo(inUse, DEFAULT);

        assertEquals(inUse, applied.settings());
        assertEquals(HashSet.empty(), applied.changed());
    }

    @Test
    void aSettingOnlyTheSessionHasGivesWayToAnEditOfThatSetting() {
        Settings inUse = Settings.DEFAULT.withAnimations(false).withTheme(ThemeName.LIGHT);
        Loaded now = read(Settings.DEFAULT.withTheme(ThemeName.DARCULA));

        Applied applied = now.appliedTo(inUse, DEFAULT);

        assertEquals(Settings.DEFAULT.withAnimations(false).withTheme(ThemeName.DARCULA), applied.settings());
        assertFalse(applied.changed().contains(Settings.ANIMATIONS));
    }

    @Test
    void aSettingRemovedFromTheFileGoesBackToItsDefault() {
        Settings inUse = Settings.DEFAULT.withRepositoryWidth(40);
        Loaded before = read(inUse);

        Applied applied = DEFAULT.appliedTo(inUse, before);

        assertEquals(Settings.DEFAULT, applied.settings());
    }

    @Test
    void aValueThatIsNotValidKeepsTheOneInUse() {
        Settings inUse = Settings.DEFAULT.withTheme(ThemeName.DARCULA);
        // Read with the default in place of the theme that is not valid.
        Loaded now = invalid(Settings.DEFAULT.withAnimations(false), Settings.THEME);

        Applied applied = now.appliedTo(inUse, read(inUse));

        assertEquals(inUse.withAnimations(false), applied.settings());
        assertFalse(applied.themeChanged());
    }

    @Test
    void aValueMadeValidAgainIsAppliedEvenWhenItIsWhatTheDefaultWas() {
        Settings inUse = Settings.DEFAULT.withTheme(ThemeName.DARCULA);
        Loaded before = invalid(Settings.DEFAULT, Settings.THEME);

        Applied applied = read(Settings.DEFAULT).appliedTo(inUse, before);

        assertEquals(Settings.DEFAULT, applied.settings());
        assertTrue(applied.themeChanged());
    }

    @Test
    void aFileThatCannotBeReadKeepsAllTheSettingsInUse() {
        Settings inUse = Settings.DEFAULT.withTheme(ThemeName.DARCULA).withAnimations(false);
        Loaded unreadable = new Loaded(Settings.DEFAULT, Option.of(new SettingsProblem.Unreadable(FILE, "not TOML")));

        Applied applied = unreadable.appliedTo(inUse, read(inUse));

        assertEquals(inUse, applied.settings());
        assertEquals(HashSet.empty(), applied.changed());
    }

    @Test
    void aFileReadAgainAfterItCouldNotBeReadAppliesAllItHas() {
        Settings inUse = Settings.DEFAULT.withAnimations(false);
        Loaded unreadable = new Loaded(Settings.DEFAULT, Option.of(new SettingsProblem.Unreadable(FILE, "not TOML")));

        Applied applied = DEFAULT.appliedTo(inUse, unreadable);

        assertEquals(Settings.DEFAULT, applied.settings());
    }

    @Test
    void editorsWithOneWrongEntryStillApply() {
        Editors editors = new Editors(List.of(new EditorProgram("Kate", List.of("kate"), false, Option.none())));
        Loaded now = invalid(Settings.DEFAULT.withEditors(editors), "editors[2].command");

        Applied applied = now.appliedTo(Settings.DEFAULT, DEFAULT);

        assertEquals(editors, applied.settings().editors());
    }

    @Test
    void savingPutsTheSettingsThatChangedInWhatTheFileHas() {
        Loaded before = read(Settings.DEFAULT.withTheme(ThemeName.LIGHT));
        // The session has animations off, which the file does not.
        Settings previous = Settings.DEFAULT.withTheme(ThemeName.LIGHT).withAnimations(false);

        Loaded saved = before.saved(previous, previous.withRepositoryWidth(40));

        assertEquals(read(Settings.DEFAULT.withTheme(ThemeName.LIGHT).withRepositoryWidth(40)), saved);
    }

    @Test
    void aHandEditStillAppliesAfterASaveOfAnotherSetting() {
        // The file had the light theme; the user edits it to darcula by hand, then drags a pane, which is saved.
        Settings inUse = Settings.DEFAULT.withTheme(ThemeName.LIGHT);
        Loaded before = read(inUse).saved(inUse, inUse.withRepositoryWidth(40));
        Loaded now = read(Settings.DEFAULT.withTheme(ThemeName.DARCULA).withRepositoryWidth(40));

        Applied applied = now.appliedTo(inUse.withRepositoryWidth(40), before);

        assertEquals(Settings.DEFAULT.withTheme(ThemeName.DARCULA).withRepositoryWidth(40), applied.settings());
    }

    @Test
    void aSettingOnlyTheSessionHasStaysAfterASaveOfAnotherSetting() {
        // Turning the animations off failed to save; dragging a pane is saved after.
        Settings inUse = Settings.DEFAULT.withAnimations(false);
        Loaded before = DEFAULT.saved(inUse, inUse.withRepositoryWidth(40));
        Loaded now = read(Settings.DEFAULT.withRepositoryWidth(40));

        Applied applied = now.appliedTo(inUse.withRepositoryWidth(40), before);

        assertEquals(inUse.withRepositoryWidth(40), applied.settings());
    }

    @Test
    void savingASettingThatWasNotValidMakesItValid() {
        Loaded before = invalid(Settings.DEFAULT, Settings.THEME, Settings.ANIMATIONS);

        Loaded saved = before.saved(Settings.DEFAULT, Settings.DEFAULT.withTheme(ThemeName.DARCULA));

        assertEquals(invalid(Settings.DEFAULT.withTheme(ThemeName.DARCULA), Settings.ANIMATIONS), saved);
    }

    @Test
    void savingTheLastSettingThatWasNotValidLeavesNoProblem() {
        Loaded before = invalid(Settings.DEFAULT, Settings.THEME);

        Loaded saved = before.saved(Settings.DEFAULT, Settings.DEFAULT.withTheme(ThemeName.DARCULA));

        assertEquals(read(Settings.DEFAULT.withTheme(ThemeName.DARCULA)), saved);
    }
}
