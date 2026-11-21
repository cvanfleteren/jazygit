package net.vanfleteren.jazygit.features.chrome.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.files.StubEditor;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.features.changes.commit.create.CommitPopup;
import net.vanfleteren.jazygit.features.changes.diff.DiffOptionsUpdate;
import net.vanfleteren.jazygit.features.chrome.theme.ThemeMsg;
import net.vanfleteren.jazygit.features.chrome.theme.ThemePopup;
import net.vanfleteren.jazygit.features.chrome.theme.ThemeScope;
import net.vanfleteren.jazygit.kernel.settings.Codec;
import net.vanfleteren.jazygit.kernel.settings.Decoded;
import net.vanfleteren.jazygit.kernel.settings.Entries;
import net.vanfleteren.jazygit.kernel.settings.RecentRepositories;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.SettingKeys;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.SettingsProblem;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.settings.TableCodec;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.CommandLog;
import net.vanfleteren.jazygit.kernel.state.popup.*;
import net.vanfleteren.jazygit.kernel.state.preferences.RepositoryTheme;
import net.vanfleteren.jazygit.kernel.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.kernel.state.preferences.SettingsError;
import net.vanfleteren.jazygit.kernel.state.preferences.SettingsMsg;
import net.vanfleteren.jazygit.kernel.state.preferences.ShowAgain;
import net.vanfleteren.jazygit.kernel.state.settings.Form;
import net.vanfleteren.jazygit.kernel.state.settings.ListPopup;
import net.vanfleteren.jazygit.kernel.state.settings.ListSetting;
import net.vanfleteren.jazygit.kernel.state.settings.SectionReads;
import net.vanfleteren.jazygit.kernel.state.settings.SettingMsg;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;
import net.vanfleteren.jazygit.kernel.state.view.*;
import org.junit.jupiter.api.Test;

class SettingsPanelUpdateTest {

    private static final SettingsPanelPopup READ = new SettingsPanelPopup(TestModels.PLAIN.settings());

    @Test
    void requestingShowsTheSettingsAndReadsWhatEachSectionReadsThenTheFile() {
        SectionReads added = model -> List.of(new SettingsPanelCmd.EditFile());

        Next next = SettingsPanelUpdate.requested(TestModels.loaded(), List.of(added));

        assertEquals(Option.of(READ), next.model().popup(SettingsPanelPopup.class));
        assertEquals(List.of(new SettingsPanelCmd.EditFile(), new SettingsPanelCmd.ReadFile()), next.cmds());
    }

    @Test
    void thePanelShowsTheThemeTheGitConfigurationGivesTheRepository() {
        RepositoryTheme own = new RepositoryTheme(Option.of(ThemeName.EMBER), Option.none());

        Next next = Update.update(
                TestModels.loaded().mapPreferences(preferences -> preferences.withRepositoryTheme(own)),
                new SettingsPanelMsg.Requested());

        assertEquals(
                Option.of(own),
                next.model()
                        .popup(SettingsPanelPopup.class)
                        .map(panel -> panel.section(SettingsPanelUpdate.REPOSITORY_THEME)));
    }

    @Test
    void requestingWhileADialogIsOpenLeavesThatDialogOpen() {
        Model typing = TestModels.loaded().openPopup(new CommitPopup());

        Next next = Update.update(typing, new SettingsPanelMsg.Requested());

        assertEquals(typing, next.model());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void theThemeIsChosenInItsOwnDialog() {
        Model open = TestModels.loaded().openPopup(READ);

        Model choosing =
                Update.update(open, new SettingsPanelMsg.ThemeRequested()).model();

        assertEquals(
                Option.of(new ThemePopup(
                        ThemeName.DARK,
                        Option.none(),
                        ThemeScope.EVERYWHERE,
                        RepositoryTheme.NONE,
                        ThemeName.DARK,
                        Option.of(READ.withOpensAt(Option.of('t'))))),
                choosing.popup(ThemePopup.class));
    }

    @Test
    void cancellingTheThemeGoesBackToTheSettingsOnTheTheme() {
        Model open = TestModels.loaded().openPopup(READ);
        Model choosing =
                Update.update(open, new SettingsPanelMsg.ThemeRequested()).model();
        Model previewed =
                Update.update(choosing, new ThemeMsg.Previewed(ThemeName.LIGHT)).model();

        Model cancelled = Update.update(previewed, new ThemeMsg.Cancelled()).model();

        assertEquals(Option.of(READ.withOpensAt(Option.of('t'))), cancelled.popup(SettingsPanelPopup.class));
        assertEquals(ThemeName.DARK, cancelled.preferences().theme());
    }

    @Test
    void cancellingTheRepositoryThemeGoesBackToTheSettingsOnTheRepositoryTheme() {
        Model open = TestModels.loaded().openPopup(READ);
        Model choosing = Update.update(open, new SettingsPanelMsg.RepositoryThemeRequested())
                .model();

        Model cancelled = Update.update(choosing, new ThemeMsg.Cancelled()).model();

        assertEquals(Option.of(READ.withOpensAt(Option.of('r'))), cancelled.popup(SettingsPanelPopup.class));
    }

    @Test
    void theAnimationsAreTurnedOffAndOnAgainAndKeptWithThePanelStillOpen() {
        Model open = TestModels.loaded().openPopup(READ);
        Settings kept = open.preferences().settings();

        Next off = Update.update(open, new SettingMsg.Toggled(Settings.ANIMATIONS));

        assertFalse(off.model().preferences().settings().animations());
        assertEquals(
                Option.of(READ.withSettings(kept.withAnimations(false))),
                off.model().popup(SettingsPanelPopup.class));
        assertEquals(List.of(new SaveSettings(kept, kept.withAnimations(false))), off.cmds());

        Next on = Update.update(off.model(), new SettingMsg.Toggled(Settings.ANIMATIONS));

        assertTrue(on.model().preferences().settings().animations());
        assertEquals(Option.of(READ), on.model().popup(SettingsPanelPopup.class));
    }

    @Test
    void readingTheRepositoryEverySoOftenIsTurnedOffAndOnAgainAndKeptWithThePanelStillOpen() {
        Model open = TestModels.loaded().openPopup(READ);
        Settings kept = open.preferences().settings();

        Next off = Update.update(open, new SettingMsg.Toggled(Settings.AUTO_REFRESH));

        assertFalse(off.model().preferences().settings().autoRefresh());
        assertEquals(
                Option.of(READ.withSettings(kept.withAutoRefresh(false))),
                off.model().popup(SettingsPanelPopup.class));
        assertEquals(List.of(new SaveSettings(kept, kept.withAutoRefresh(false))), off.cmds());

        Next on = Update.update(off.model(), new SettingMsg.Toggled(Settings.AUTO_REFRESH));

        assertTrue(on.model().preferences().settings().autoRefresh());
        assertEquals(Option.of(READ), on.model().popup(SettingsPanelPopup.class));
    }

    @Test
    void fetchingEverySoOftenIsTurnedOffAndOnAgainAndKeptWithThePanelStillOpen() {
        Model open = TestModels.loaded().openPopup(READ);
        Settings kept = open.preferences().settings();

        Next off = Update.update(open, new SettingMsg.Toggled(Settings.AUTO_FETCH));

        assertFalse(off.model().preferences().settings().autoFetch());
        assertEquals(
                Option.of(READ.withSettings(kept.withAutoFetch(false))),
                off.model().popup(SettingsPanelPopup.class));
        assertEquals(List.of(new SaveSettings(kept, kept.withAutoFetch(false))), off.cmds());

        Next on = Update.update(off.model(), new SettingMsg.Toggled(Settings.AUTO_FETCH));

        assertTrue(on.model().preferences().settings().autoFetch());
        assertEquals(Option.of(READ), on.model().popup(SettingsPanelPopup.class));
    }

    private static final Path FILE = Path.of("/home/me/.config/jazygit/config.toml");
    private static final SettingsProblem INVALID_THEME = new SettingsProblem.Invalid(FILE, List.of("ui.theme"));
    /**
     * A setting that shows the repository again when it changes in the file, as the gh executable does (ADR 163).
     */
    private static final SettingKey<Option<String>> EXECUTABLE = SettingKey.of(
                    "github.executable",
                    Option.none(),
                    Codec.text(path -> Option.when(!path.isBlank(), path), path -> path)
                            .optional())
            .withReopens(true);

    private static SettingsStore.Loaded read(Settings settings) {
        return new SettingsStore.Loaded(settings, Option.none());
    }

    /**
     * A file read with the gh executable set, by keys that have {@link #EXECUTABLE} too, with {@code problem} if any.
     */
    private static SettingsStore.Loaded readWithExecutable(Option<SettingsProblem> problem) {
        return new SettingsStore.Loaded(
                Settings.DEFAULT.with(EXECUTABLE, Option.of("/opt/gh")),
                problem,
                SettingKeys.KERNEL.and(List.of(EXECUTABLE)));
    }

    /**
     * A settings file at {@link #FILE} that reads as {@code loaded}, and is made as {@code made} says.
     */
    private static SettingsStore file(SettingsStore.Loaded loaded, Either<SettingsProblem, Path> made) {
        return new SettingsStore() {
            @Override
            public Loaded load() {
                return loaded;
            }

            @Override
            public Either<SettingsProblem, Settings> save(Settings previous, Settings next) {
                return Either.right(next);
            }

            @Override
            public Option<Path> file() {
                return Option.of(FILE);
            }

            @Override
            public Either<SettingsProblem, Path> made() {
                return made;
            }
        };
    }

    private static Env env(Git git, SettingsStore settings, Terminal terminal) {
        return new Env(git, settings, RecentRepositories.NONE, Repositories.NONE, terminal);
    }

    @Test
    void eOpensTheSettingsFile() {
        Next next = Update.update(TestModels.loaded().openPopup(READ), new SettingsPanelMsg.FileRequested());

        assertEquals(List.of(new SettingsPanelCmd.EditFile()), next.cmds());
        assertEquals(Option.of(READ), next.model().popup(SettingsPanelPopup.class));
    }

    @Test
    void theFileIsMadeEditedWithTheTerminalHandedOverAndReadOnceTheEditorIsClosed() {
        java.util.List<String> events = new ArrayList<>();
        Terminal terminal = new Terminal() {
            @Override
            public <T> T handOver(Supplier<T> program) {
                events.add("leave");
                T result = program.get();
                events.add("come back");
                return result;
            }
        };
        Git git = StubGit.of().withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> edit(List<String> files) {
                events.add("edit " + files.mkString(" "));
                return Either.right(List.empty());
            }
        });
        SettingsStore.Loaded edited = read(Settings.DEFAULT.withAnimations(false));

        Msg msg = new SettingsPanelCmd.EditFile().run(env(git, file(edited, Either.right(FILE)), terminal));

        assertEquals(new SettingsPanelMsg.FileEdited(List.empty(), edited), msg);
        assertEquals(java.util.List.of("leave", "edit " + FILE, "come back"), events);
    }

    @Test
    void aFileThatCannotBeMadeIsNotOpened() {
        java.util.List<String> events = new ArrayList<>();
        Git git = StubGit.of().withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> edit(List<String> files) {
                events.add("edit");
                return Either.right(List.empty());
            }
        });
        SettingsProblem unwritable = new SettingsProblem.Unwritable(FILE, "Permission denied");

        Msg msg = new SettingsPanelCmd.EditFile()
                .run(env(git, file(SettingsStore.Loaded.DEFAULT, Either.left(unwritable)), Terminal.NONE));

        assertEquals(new SettingsPanelMsg.FileNotEdited(new Failure.Settings(unwritable)), msg);
        assertEquals(java.util.List.of(), events);
        AppError.OperationFailed error = assertInstanceOf(
                AppError.OperationFailed.class,
                Update.update(TestModels.loaded().openPopup(READ), msg)
                        .model()
                        .outcome()
                        .error()
                        .get());
        assertEquals(SettingsPanelCmd.EditFile.EDIT_SETTINGS, error.operation());
    }

    @Test
    void anEditorThatFailsShowsWhy() {
        Git git = StubGit.of().withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> edit(List<String> files) {
                return Either.left(new GitFailure(new GitError.EditorFailed("nvim", 1), List.empty()));
            }
        });

        Msg msg = new SettingsPanelCmd.EditFile()
                .run(env(git, file(SettingsStore.Loaded.DEFAULT, Either.right(FILE)), Terminal.NONE));

        assertInstanceOf(SettingsPanelMsg.FileNotEdited.class, msg);
    }

    @Test
    void whatChangedInTheFileAppliesOnceTheEditorIsClosedAndThePanelShowsIt() {
        Settings edited = Settings.DEFAULT.withTheme(ThemeName.DARCULA).withAnimations(false);

        Model model = Update.update(
                        TestModels.loaded().openPopup(READ),
                        new SettingsPanelMsg.FileEdited(List.empty(), read(edited)))
                .model();

        assertEquals(edited, model.preferences().settings());
        assertEquals(read(edited), model.preferences().file());
        assertEquals(Option.of(READ.withSettings(edited)), model.popup(SettingsPanelPopup.class));
        assertEquals(Option.none(), model.outcome().error());
    }

    @Test
    void readingTheFileAsThePanelOpensShowsWhereItIsAndKeepsASettingOnlyTheSessionHas() {
        // The animations were turned off, but could not be saved: the file has them on still.
        Model model = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withAnimations(false)))
                .openPopup(READ.withSettings(READ.settings().withAnimations(false)));

        Model read = Update.update(
                        model, new SettingsPanelMsg.FileRead(Option.of(FILE.toString()), SettingsStore.Loaded.DEFAULT))
                .model();

        assertFalse(read.preferences().settings().animations());
        assertEquals(
                Option.of(READ.withSettings(read.preferences().settings())
                        .with(SettingsPanelUpdate.FILE, Option.of(FILE.toString()))),
                read.popup(SettingsPanelPopup.class));
    }

    /**
     * A list of names, which a list dialog edits over the settings panel.
     */
    private static final ListSetting<String> NAMES = new ListSetting<>(
            SettingKey.of("names", Entries.none(), Codec.tables(new TableCodec<String>() {
                @Override
                public List<String> fields() {
                    return List.of("name");
                }

                @Override
                public Decoded<String> decode(String at, LinkedHashMap<String, Object> table) {
                    return new Decoded<>(table.get("name").map(String::valueOf), List.empty());
                }

                @Override
                public LinkedHashMap<String, Object> encode(String name) {
                    return LinkedHashMap.of("name", name);
                }
            })),
            "names",
            name -> name,
            new Form<>("", List.empty(), (name, others) -> List.empty()));

    @Test
    void readingTheFileWhileAListIsOpenShowsItInThePanelTheListGoesBackTo() {
        Model model = TestModels.loaded().openPopup(new ListPopup(NAMES, READ));

        Model read = Update.update(
                        model,
                        new SettingsPanelMsg.FileRead(
                                Option.of(FILE.toString()), read(Settings.DEFAULT.withAnimations(false))))
                .model();

        assertFalse(read.preferences().settings().animations());
        assertEquals(
                Option.of(new ListPopup(
                        NAMES,
                        READ.withSettings(read.preferences().settings())
                                .with(SettingsPanelUpdate.FILE, Option.of(FILE.toString())))),
                read.popup(ListPopup.class));
    }

    @Test
    void aValueThatIsNotValidKeepsTheOneInUseAndIsReported() {
        Model model = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withTheme(ThemeName.DARCULA)))
                .openPopup(READ);

        Model read = Update.update(
                        model,
                        new SettingsPanelMsg.FileEdited(
                                List.empty(), new SettingsStore.Loaded(Settings.DEFAULT, Option.of(INVALID_THEME))))
                .model();

        assertEquals(ThemeName.DARCULA, read.preferences().settings().theme());
        assertEquals(
                Option.of(new SettingsError.NotReread(INVALID_THEME)),
                read.outcome().error());
        assertEquals(Option.of(INVALID_THEME), read.preferences().reported());
    }

    @Test
    void aProblemReportedBeforeIsReportedAgainAfterTheEditor() {
        Model model = TestModels.loaded()
                .mapPreferences(preferences -> preferences.withReported(Option.of(INVALID_THEME)))
                .openPopup(READ);

        Model read = Update.update(
                        model,
                        new SettingsPanelMsg.FileEdited(
                                List.empty(), new SettingsStore.Loaded(Settings.DEFAULT, Option.of(INVALID_THEME))))
                .model();

        assertEquals(
                Option.of(new SettingsError.NotReread(INVALID_THEME)),
                read.outcome().error());
    }

    @Test
    void aProblemReportedBeforeIsNotReportedAgainAsThePanelOpens() {
        Model model = TestModels.loaded()
                .mapPreferences(preferences -> preferences.withReported(Option.of(INVALID_THEME)))
                .openPopup(READ);
        SettingsProblem other = new SettingsProblem.Invalid(FILE, List.of("ui.animations"));

        Model same = Update.update(
                        model,
                        new SettingsPanelMsg.FileRead(
                                Option.of(FILE.toString()),
                                new SettingsStore.Loaded(Settings.DEFAULT, Option.of(INVALID_THEME))))
                .model();
        Model changed = Update.update(
                        model,
                        new SettingsPanelMsg.FileRead(
                                Option.of(FILE.toString()),
                                new SettingsStore.Loaded(Settings.DEFAULT, Option.of(other))))
                .model();

        assertEquals(Option.none(), same.outcome().error());
        assertEquals(
                Option.of(new SettingsError.NotReread(other)), changed.outcome().error());
    }

    @Test
    void aThemeChangedInTheFileReplacesTheOneAskedForAtStartup() {
        Model model = TestModels.loaded()
                .mapPreferences(preferences -> preferences.withThemeOverride(Option.of(ThemeName.LIGHT)))
                .openPopup(READ);

        Model changed = Update.update(
                        model,
                        new SettingsPanelMsg.FileEdited(
                                List.empty(), read(Settings.DEFAULT.withTheme(ThemeName.DARCULA))))
                .model();
        Model unchanged = Update.update(
                        model,
                        new SettingsPanelMsg.FileEdited(List.empty(), read(Settings.DEFAULT.withAnimations(false))))
                .model();

        assertEquals(ThemeName.DARCULA, changed.preferences().theme());
        assertEquals(ThemeName.LIGHT, unchanged.preferences().theme());
    }

    @Test
    void aGhExecutableChangedInTheFileShowsTheRepositoryAgain() {
        Model model = TestModels.loaded().openPopup(READ);

        Next next =
                Update.update(model, new SettingsPanelMsg.FileEdited(List.empty(), readWithExecutable(Option.none())));

        assertEquals(Option.of("/opt/gh"), next.model().preferences().settings().get(EXECUTABLE));
        assertEquals(List.of(new ShowAgain(Landing.of(model.repoView()))), next.cmds());
    }

    @Test
    void aProblemFoundWithAGhExecutableChangedInTheFileGoesOnToTheRepositoryShownAgain() {
        Model model = TestModels.loaded().openPopup(READ);

        Next next = Update.update(
                model, new SettingsPanelMsg.FileEdited(List.empty(), readWithExecutable(Option.of(INVALID_THEME))));
        // The repository is shown again, in a session of its own.
        Model shownAgain = Update.update(TestModels.loaded(), SettingsPanelUpdate.carried(next.model()))
                .model();

        assertEquals(List.of(new ShowAgain(Landing.of(model.repoView()))), next.cmds());
        assertEquals(
                Option.of(new SettingsError.NotReread(INVALID_THEME)),
                shownAgain.outcome().error());
    }

    @Test
    void whatTheSessionKnewOfTheFileGoesOnWithoutAnotherErrorItWasShowing() {
        SettingsStore.Loaded file = read(Settings.DEFAULT.withAnimations(false));
        Model last = TestModels.loaded()
                .mapPreferences(preferences -> preferences.withFile(file).withReported(Option.of(INVALID_THEME)))
                .fail(new SettingsError.NotSaved(
                        new Failure.Settings(new SettingsProblem.Unwritable(FILE, "Permission denied"))));

        assertEquals(
                new SettingsMsg.Carried(file, Option.of(INVALID_THEME), Option.none()),
                SettingsPanelUpdate.carried(last));
    }

    @Test
    void aDiffStyleChangedInTheFileReadsTheDiffsAgain() {
        Model model = TestModels.loaded().openPopup(READ);
        Settings edited = Settings.DEFAULT.withDiffStyle(net.vanfleteren.jazygit.kernel.settings.DiffStyle.PLAIN);

        Next next = Update.update(model, new SettingsPanelMsg.FileEdited(List.empty(), read(edited)));

        assertEquals(edited.diffStyle(), next.model().preferences().settings().diffStyle());
        assertEquals(DiffOptionsUpdate.changed(next.model()).cmds(), next.cmds());
    }

    @Test
    void theEditIsInTheCommandLog() {
        List<ExecutedCommand> commands = List.of(ExecutedCommand.of("nvim", FILE.toString()));

        Model model = Update.update(
                        TestModels.loaded().openPopup(READ),
                        new SettingsPanelMsg.FileEdited(commands, SettingsStore.Loaded.DEFAULT))
                .model();

        assertEquals(
                CommandLog.append(TestModels.loaded(), SettingsPanelCmd.EditFile.EDIT_SETTINGS, commands)
                        .outcome()
                        .commandLog(),
                model.outcome().commandLog());
    }

    @Test
    void readingTheFileReadsItAndWhereItIs() {
        SettingsStore.Loaded loaded = read(Settings.DEFAULT.withAnimations(false));

        Msg msg =
                new SettingsPanelCmd.ReadFile().run(env(StubGit.of(), file(loaded, Either.right(FILE)), Terminal.NONE));

        assertEquals(new SettingsPanelMsg.FileRead(Option.of(FILE.toString()), loaded), msg);
    }
}
