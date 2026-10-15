package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.SettingsProblem;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.ThemeName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class SettingsUpdateTest {

    private static final Path FILE = Path.of("/config/jazygit/config.toml");
    private static final Settings DARCULA = Settings.DEFAULT.withTheme(ThemeName.DARCULA);
    private static final Cmd.SaveSettings SAVE = new Cmd.SaveSettings(Settings.DEFAULT, DARCULA);

    private static Env env(SettingsStore settings) {
        return new Env(new StubGitProvider() {
        }, StubGitHub.empty(), settings);
    }

    private static SettingsStore saving(Either<SettingsProblem, Settings> outcome) {
        return new SettingsStore() {
            @Override
            public Loaded load() {
                return Loaded.DEFAULT;
            }

            @Override
            public Either<SettingsProblem, Settings> save(Settings previous, Settings next) {
                return outcome;
            }
        };
    }

    @Test
    void theLoadedSettingsAreUsedFromTheStart() {
        Model model = Update.init("repo", new SettingsStore.Loaded(DARCULA, Option.none())).model();

        assertEquals(DARCULA, model.settings());
        assertEquals(Option.none(), model.error());
    }

    @Test
    void aThemeAskedForAtStartupIsDrawnInsteadOfTheSavedOne() {
        Model model = Update.init("repo", new SettingsStore.Loaded(DARCULA, Option.none()), Option.of(ThemeName.LIGHT))
                .model();

        assertEquals(ThemeName.LIGHT, model.theme());
        assertEquals(DARCULA, model.settings());
    }

    @Test
    void withoutAThemeAskedForAtStartupTheSavedOneIsDrawn() {
        Model model = Update.init("repo", new SettingsStore.Loaded(DARCULA, Option.none()), Option.none()).model();

        assertEquals(ThemeName.DARCULA, model.theme());
    }

    @Test
    void aProblemLoadingTheSettingsIsShownInTheCommandLogOnly() {
        SettingsProblem invalid = new SettingsProblem.Invalid(FILE, List.of("ui.theme"));

        Model model = Update.init("repo", new SettingsStore.Loaded(Settings.DEFAULT, Option.of(invalid))).model();

        assertEquals(Option.of(new AppError.SettingsNotLoaded(invalid)), model.error());
        assertEquals(Option.none(), model.popup());
    }

    @Test
    void savingChangesNothingWhenItWorks() {
        Model model = TestModels.loaded().withSettings(DARCULA);

        Msg saved = Program.perform(env(saving(Either.right(DARCULA))), SAVE);

        assertEquals(new SettingsMsg.Saved(), saved);
        assertEquals(Update.Next.of(model), Update.update(model, saved));
    }

    @Test
    void savingThatFailsKeepsTheSettingAndShowsTheError() {
        Model model = TestModels.loaded().withSettings(DARCULA);
        SettingsProblem unwritable = new SettingsProblem.Unwritable(FILE, "read-only");

        Model after = Update.update(model, Program.perform(env(saving(Either.left(unwritable))), SAVE)).model();

        AppError error = new AppError.SettingsNotSaved(new Failure.Settings(unwritable));
        assertEquals(DARCULA, after.settings());
        assertEquals(Option.of(error), after.error());
        assertEquals(Option.of(new ErrorPopup(error)), after.popup());
    }
}
