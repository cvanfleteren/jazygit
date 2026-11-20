package net.vanfleteren.jazygit.kernel.state.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.SettingsProblem;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Program;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.popup.ErrorPopup;
import org.junit.jupiter.api.Test;

class SettingsUpdateTest {

    private static final Path FILE = Path.of("/config/jazygit/config.toml");
    private static final Settings DARCULA = Settings.DEFAULT.withTheme(ThemeName.DARCULA);
    private static final SaveSettings SAVE = new SaveSettings(Settings.DEFAULT, DARCULA);

    private static Env env(SettingsStore settings) {
        return new Env(StubGit.of(), StubGitHub.of(), settings);
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
        Model model = TestModels.init("repo", new SettingsStore.Loaded(DARCULA, Option.none()))
                .model();

        assertEquals(DARCULA, model.preferences().settings());
        assertEquals(Option.none(), model.outcome().error());
    }

    @Test
    void aThemeAskedForAtStartupIsDrawnInsteadOfTheSavedOne() {
        Model model = TestModels.init(
                        "repo", new SettingsStore.Loaded(DARCULA, Option.none()), Option.of(ThemeName.LIGHT))
                .model();

        assertEquals(ThemeName.LIGHT, model.preferences().theme());
        assertEquals(DARCULA, model.preferences().settings());
    }

    @Test
    void withoutAThemeAskedForAtStartupTheSavedOneIsDrawn() {
        Model model = TestModels.init("repo", new SettingsStore.Loaded(DARCULA, Option.none()), Option.none())
                .model();

        assertEquals(ThemeName.DARCULA, model.preferences().theme());
    }

    @Test
    void aProblemLoadingTheSettingsIsShownInTheCommandLogOnly() {
        SettingsProblem invalid = new SettingsProblem.Invalid(FILE, List.of("ui.theme"));

        Model model = TestModels.init("repo", new SettingsStore.Loaded(Settings.DEFAULT, Option.of(invalid)))
                .model();

        assertEquals(
                Option.of(new SettingsError.NotLoaded(invalid)), model.outcome().error());
        assertEquals(Option.none(), model.popup());
    }

    @Test
    void savingKeepsWhatTheFileHasNowWhenItWorks() {
        // The file has plain diffs, which the save leaves as they are.
        SettingsStore.Loaded file =
                new SettingsStore.Loaded(Settings.DEFAULT.withDiffStyle(DiffStyle.PLAIN), Option.none());
        Model model = TestModels.loaded()
                .mapPreferences(preferences -> preferences.withSettings(DARCULA).withFile(file));

        Msg saved = Program.perform(env(saving(Either.right(DARCULA))), SAVE);

        assertEquals(new SettingsMsg.Saved(Settings.DEFAULT, DARCULA), saved);
        assertEquals(
                Update.Next.of(model.mapPreferences(preferences -> preferences.withFile(
                        new SettingsStore.Loaded(DARCULA.withDiffStyle(DiffStyle.PLAIN), Option.none())))),
                Update.update(model, saved));
    }

    @Test
    void whatTheFileHadGoesOnToTheRepositoryShownInstead() {
        SettingsStore.Loaded file = new SettingsStore.Loaded(DARCULA, Option.none());
        Option<SettingsProblem> reported = Option.of(new SettingsProblem.Invalid(FILE, List.of("ui.theme")));

        Model model = Update.update(TestModels.loaded(), new SettingsMsg.Carried(file, reported, Option.none()))
                .model();

        assertEquals(file, model.preferences().file());
        assertEquals(reported, model.preferences().reported());
        assertEquals(Option.none(), model.outcome().error());
    }

    @Test
    void aProblemTheSessionWasShowingShowsInTheRepositoryShownInstead() {
        SettingsProblem invalid = new SettingsProblem.Invalid(FILE, List.of("ui.theme"));

        Model model = Update.update(
                        TestModels.loaded(),
                        new SettingsMsg.Carried(SettingsStore.Loaded.DEFAULT, Option.of(invalid), Option.of(invalid)))
                .model();

        assertEquals(
                Option.of(new SettingsError.NotReread(invalid)), model.outcome().error());
        assertEquals(Option.of(invalid), model.preferences().reported());
    }

    @Test
    void theStartKeepsWhatTheFileHadAndWhatWasReported() {
        SettingsProblem invalid = new SettingsProblem.Invalid(FILE, List.of("ui.theme"));
        SettingsStore.Loaded loaded = new SettingsStore.Loaded(DARCULA, Option.of(invalid));

        Model model = TestModels.init("repo", loaded).model();

        assertEquals(loaded, model.preferences().file());
        assertEquals(Option.of(invalid), model.preferences().reported());
    }

    @Test
    void showingTheRepositoryAgainThatFailsShowsTheError() {
        Failure failure = new Failure.Settings(new SettingsProblem.Unwritable(FILE, "read-only"));

        Model after = Update.update(TestModels.loaded(), new SettingsMsg.NotShownAgain(failure))
                .model();

        AppError error = new SettingsError.NotShownAgain(failure);
        assertEquals(Option.of(error), after.outcome().error());
        assertEquals(Option.of(new ErrorPopup(error)), after.popup());
    }

    @Test
    void theRepositoryShownAgainChangesNothing() {
        Model model = TestModels.loaded();

        assertEquals(model, Update.update(model, new SettingsMsg.ShownAgain()).model());
    }

    @Test
    void savingThatFailsKeepsTheSettingAndShowsTheError() {
        Model model = TestModels.loaded().mapPreferences(preferences -> preferences.withSettings(DARCULA));
        SettingsProblem unwritable = new SettingsProblem.Unwritable(FILE, "read-only");

        Model after = Update.update(model, Program.perform(env(saving(Either.left(unwritable))), SAVE))
                .model();

        AppError error = new SettingsError.NotSaved(new Failure.Settings(unwritable));
        assertEquals(DARCULA, after.preferences().settings());
        assertEquals(Option.of(error), after.outcome().error());
        assertEquals(Option.of(new ErrorPopup(error)), after.popup());
    }
}
