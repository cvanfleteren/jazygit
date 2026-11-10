package net.vanfleteren.jazygit.feature.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.RepositoryTheme;
import net.vanfleteren.jazygit.state.SaveSettings;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

class ThemeUpdateTest {

    @Test
    void requestOpensTheChoiceWithTheCurrentTheme() {
        Model model = Update.update(TestModels.loaded(), new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();

        assertEquals(
                Option.of(new ThemePopup(
                        ThemeName.DARK, Option.none(), ThemeScope.EVERYWHERE, RepositoryTheme.NONE, ThemeName.DARK)),
                model.popup(ThemePopup.class));
    }

    @Test
    void theHighlightedThemeShowsWithoutBeingSaved() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();

        Next previewed = Update.update(open, new ThemeMsg.Previewed(ThemeName.DARCULA));

        assertEquals(ThemeName.DARCULA, previewed.model().preferences().theme());
        assertEquals(
                TestModels.PLAIN.settings(), previewed.model().preferences().settings());
        assertEquals(List.empty(), previewed.cmds());
        assertEquals(open.popup(), previewed.model().popup());
    }

    @Test
    void cancellingShowsTheThemeFromBeforeAgain() {
        Model open = Update.update(
                        TestModels.loaded()
                                .mapPreferences(
                                        preferences -> preferences.withThemeOverride(Option.of(ThemeName.LIGHT))),
                        new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();
        Model previewed =
                Update.update(open, new ThemeMsg.Previewed(ThemeName.DARCULA)).model();

        Model cancelled = Update.update(previewed, new ThemeMsg.Cancelled()).model();

        assertEquals(ThemeName.LIGHT, cancelled.preferences().theme());
        assertEquals(Option.none(), cancelled.popup());
    }

    @Test
    void choosingAfterHighlightingAnotherThemeSavesTheChosenOne() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();
        Model previewed =
                Update.update(open, new ThemeMsg.Previewed(ThemeName.DARCULA)).model();

        Next chosen = Update.update(previewed, new ThemeMsg.Chosen(ThemeName.LIGHT));

        assertEquals(ThemeName.LIGHT, chosen.model().preferences().theme());
        assertEquals(Option.none(), chosen.model().preferences().themeOverride());
        assertEquals(
                List.of(new SaveSettings(
                        TestModels.PLAIN.settings(), TestModels.PLAIN.settings().withTheme(ThemeName.LIGHT))),
                chosen.cmds());
    }

    @Test
    void aPreviewAfterThePopupClosedIsDropped() {
        Model closed = TestModels.loaded();

        assertEquals(
                closed,
                Update.update(closed, new ThemeMsg.Previewed(ThemeName.DARCULA)).model());
    }

    @Test
    void requestWhileADialogIsOpenLeavesThatDialogOpen() {
        Model typing = TestModels.loaded().openPopup(new net.vanfleteren.jazygit.feature.commit.create.CommitPopup());

        Model model = Update.update(typing, new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();

        assertEquals(typing.popup(), model.popup());
    }

    @Test
    void choosingAThemeClosesThePopupSwitchesAndSavesIt() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();

        Next chosen = Update.update(open, new ThemeMsg.Chosen(ThemeName.DARCULA));

        assertEquals(Option.none(), chosen.model().popup(ThemePopup.class));
        assertEquals(ThemeName.DARCULA, chosen.model().preferences().settings().theme());
        assertEquals(
                List.of(new SaveSettings(
                        TestModels.PLAIN.settings(), TestModels.PLAIN.settings().withTheme(ThemeName.DARCULA))),
                chosen.cmds());
    }

    @Test
    void choosingTheCurrentThemeSavesNothing() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();

        Next chosen = Update.update(open, new ThemeMsg.Chosen(ThemeName.DARK));

        assertEquals(Option.none(), chosen.model().popup(ThemePopup.class));
        assertEquals(List.empty(), chosen.cmds());
    }

    @Test
    void requestOpensTheChoiceWithTheThemeAskedForAtStartup() {
        Model model = Update.update(
                        TestModels.loaded()
                                .mapPreferences(
                                        preferences -> preferences.withThemeOverride(Option.of(ThemeName.LIGHT))),
                        new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();

        assertEquals(
                Option.of(new ThemePopup(
                        ThemeName.LIGHT,
                        Option.of(ThemeName.LIGHT),
                        ThemeScope.EVERYWHERE,
                        RepositoryTheme.NONE,
                        ThemeName.DARK)),
                model.popup(ThemePopup.class));
    }

    @Test
    void choosingTheThemeAskedForAtStartupSavesIt() {
        Model open = Update.update(
                        TestModels.loaded()
                                .mapPreferences(
                                        preferences -> preferences.withThemeOverride(Option.of(ThemeName.LIGHT))),
                        new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();

        Next chosen = Update.update(open, new ThemeMsg.Chosen(ThemeName.LIGHT));

        assertEquals(Option.none(), chosen.model().preferences().themeOverride());
        assertEquals(ThemeName.LIGHT, chosen.model().preferences().theme());
        assertEquals(
                List.of(new SaveSettings(
                        TestModels.PLAIN.settings(), TestModels.PLAIN.settings().withTheme(ThemeName.LIGHT))),
                chosen.cmds());
    }

    @Test
    void choosingTheSavedThemeOverTheOneAskedForAtStartupSwitchesBackAndSavesNothing() {
        Model open = Update.update(
                        TestModels.loaded()
                                .mapPreferences(
                                        preferences -> preferences.withThemeOverride(Option.of(ThemeName.LIGHT))),
                        new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();

        Next chosen = Update.update(open, new ThemeMsg.Chosen(ThemeName.DARK));

        assertEquals(ThemeName.DARK, chosen.model().preferences().theme());
        assertEquals(List.empty(), chosen.cmds());
    }

    @Test
    void cancellingKeepsTheThemeAskedForAtStartup() {
        Model open = Update.update(
                        TestModels.loaded()
                                .mapPreferences(
                                        preferences -> preferences.withThemeOverride(Option.of(ThemeName.LIGHT))),
                        new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();

        Next cancelled = Update.update(open, new ThemeMsg.Cancelled());

        assertEquals(ThemeName.LIGHT, cancelled.model().preferences().theme());
        assertEquals(List.empty(), cancelled.cmds());
    }

    @Test
    void cancellingKeepsTheTheme() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();

        Next cancelled = Update.update(open, new ThemeMsg.Cancelled());

        assertEquals(Option.none(), cancelled.model().popup(ThemePopup.class));
        assertEquals(ThemeName.DARK, cancelled.model().preferences().settings().theme());
        assertEquals(List.empty(), cancelled.cmds());
    }

    private static final RepositoryTheme.Inherited WORK =
            new RepositoryTheme.Inherited(ThemeName.LIGHT, "/home/alice/.gitconfig-work");

    private static Model inRepository(RepositoryTheme theme) {
        return TestModels.loaded().mapPreferences(preferences -> preferences.withRepositoryTheme(theme));
    }

    @Test
    void theThemeOfTheRepositoryShowsInsteadOfTheSavedOne() {
        assertEquals(
                ThemeName.LIGHT,
                inRepository(new RepositoryTheme(Option.none(), Option.of(WORK)))
                        .preferences()
                        .theme());
        assertEquals(
                ThemeName.EMBER,
                inRepository(new RepositoryTheme(Option.of(ThemeName.EMBER), Option.of(WORK)))
                        .preferences()
                        .theme());
    }

    @Test
    void requestForTheRepositoryOpensAtTheThemeItShows() {
        RepositoryTheme theme = new RepositoryTheme(Option.none(), Option.of(WORK));

        Model model = Update.update(inRepository(theme), new ThemeMsg.Requested(ThemeScope.REPOSITORY))
                .model();

        assertEquals(
                Option.of(new ThemePopup(ThemeName.LIGHT, Option.none(), ThemeScope.REPOSITORY, theme, ThemeName.DARK)),
                model.popup(ThemePopup.class));
    }

    @Test
    void choosingForTheRepositoryKeepsItInItsGitConfiguration() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested(ThemeScope.REPOSITORY))
                .model();
        Model previewed =
                Update.update(open, new ThemeMsg.Previewed(ThemeName.DARCULA)).model();

        Next chosen = Update.update(previewed, new ThemeMsg.Chosen(ThemeName.EMBER));

        assertEquals(Option.none(), chosen.model().popup(ThemePopup.class));
        assertEquals(ThemeName.EMBER, chosen.model().preferences().theme());
        assertEquals(Option.none(), chosen.model().preferences().themeOverride());
        assertEquals(TestModels.PLAIN.settings(), chosen.model().preferences().settings());
        assertEquals(List.of(new ThemeCmd(Option.of(ThemeName.EMBER))), chosen.cmds());
    }

    @Test
    void choosingTheThemeTheRepositoryHasAlreadyKeepsNothing() {
        Model open = Update.update(
                        inRepository(new RepositoryTheme(Option.of(ThemeName.EMBER), Option.none())),
                        new ThemeMsg.Requested(ThemeScope.REPOSITORY))
                .model();

        Next chosen = Update.update(open, new ThemeMsg.Chosen(ThemeName.EMBER));

        assertEquals(ThemeName.EMBER, chosen.model().preferences().theme());
        assertEquals(List.empty(), chosen.cmds());
    }

    @Test
    void clearingFallsBackToTheInheritedThemeAndTakesTheOwnAway() {
        Model open = Update.update(
                        inRepository(new RepositoryTheme(Option.of(ThemeName.EMBER), Option.of(WORK))),
                        new ThemeMsg.Requested(ThemeScope.REPOSITORY))
                .model();

        Model previewed = Update.update(open, new ThemeMsg.FallbackPreviewed()).model();
        Next cleared = Update.update(previewed, new ThemeMsg.Cleared());

        assertEquals(ThemeName.LIGHT, previewed.preferences().theme());
        assertEquals(Option.none(), cleared.model().popup(ThemePopup.class));
        assertEquals(
                new RepositoryTheme(Option.none(), Option.of(WORK)),
                cleared.model().preferences().repositoryTheme());
        assertEquals(ThemeName.LIGHT, cleared.model().preferences().theme());
        assertEquals(List.of(new ThemeCmd(Option.none())), cleared.cmds());
    }

    @Test
    void clearingWithNothingInheritedFallsBackToTheSavedTheme() {
        Model open = Update.update(
                        inRepository(new RepositoryTheme(Option.of(ThemeName.EMBER), Option.none())),
                        new ThemeMsg.Requested(ThemeScope.REPOSITORY))
                .model();

        Next cleared = Update.update(open, new ThemeMsg.Cleared());

        assertEquals(ThemeName.DARK, cleared.model().preferences().theme());
    }

    @Test
    void choosingForAllRepositoriesLeavesTheThemeOfTheRepository() {
        RepositoryTheme theme = new RepositoryTheme(Option.of(ThemeName.EMBER), Option.none());
        Model open = Update.update(inRepository(theme), new ThemeMsg.Requested(ThemeScope.EVERYWHERE))
                .model();

        Next chosen = Update.update(open, new ThemeMsg.Chosen(ThemeName.LIGHT));

        assertEquals(theme, chosen.model().preferences().repositoryTheme());
        assertEquals(ThemeName.EMBER, chosen.model().preferences().theme());
        assertEquals(ThemeName.LIGHT, chosen.model().preferences().settings().theme());
    }
}
