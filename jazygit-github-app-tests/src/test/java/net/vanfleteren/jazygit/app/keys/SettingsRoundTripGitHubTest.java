package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.vanfleteren.jazygit.features.agent.settings.AgentProgram;
import net.vanfleteren.jazygit.features.agent.settings.Agents;
import net.vanfleteren.jazygit.features.changes.edit.EditorSettings;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.features.github.settings.AccountBrowsers;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettings;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.settings.Editors;
import net.vanfleteren.jazygit.kernel.settings.FilterDefinition;
import net.vanfleteren.jazygit.kernel.settings.FilterDefinitions;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.SettingsFile;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore.Loaded;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The tests of SettingsRoundTripTest with GitHub in the app (ADR 171).
 */
class SettingsRoundTripGitHubTest {

    /**
     * A user's file: comments, keys the app does not know, settings away from their default and at it, and the lists
     * only the user writes.
     */
    private static final String USERS_FILE = """
            # my settings
            future = true

            old-key = "kept"

            [ui]
            # the one I like
            theme = "darcula"
            repository-width = 40
            diff-syntax = false
            repository-heights = [20, 20, 20]
            animations = false
            diff-filters = ["java-imports"]
            other = 3

            [git]
            # fetch by hand
            auto-fetch = false

            [github]
            executable = "/opt/gh"

            [github.browsers."github.com"]
            # personal
            cvf = "firefox -P personal"

            [[editors]]
            # the IDE
            name = "IntelliJ"
            command = ["idea", "{dir}"]
            shortcut = "i"

            [[diff-filters]]
            id = "mine"
            name = "Generated sources"
            paths = ["**/generated/**"]

            [mine]
            x = [1, 2]
            """;

    /**
     * The settings of {@link #USERS_FILE}.
     */
    private static final Settings USERS_SETTINGS = Settings.DEFAULT
            .withTheme(ThemeName.DARCULA)
            .with(GitHubSettings.GH_EXECUTABLE, Option.some("/opt/gh"))
            .withRepositoryWidth(40)
            .withDiffSyntax(false)
            .withRepositoryHeights(List.of(20, 20, 20))
            .withAnimations(false)
            .withAutoFetch(false)
            .with(
                    GitHubSettings.BROWSERS,
                    AccountBrowsers.NONE.with("github.com", "cvf", Option.some("firefox -P personal")))
            .with(
                    EditorSettings.EDITORS,
                    new Editors(List.of(
                            EditorProgram.ofDir("IntelliJ", List.of("idea", "{dir}"), false, Option.some('i')))))
            .withDiffFilters(List.of("java-imports"))
            .withFilterDefinitions(new FilterDefinitions(List.of(new FilterDefinition(
                    "mine",
                    Option.some("Generated sources"),
                    List.empty(),
                    List.of("**/generated/**"),
                    Option.none(),
                    false))));

    /**
     * A change to each setting the app writes, in the order the file has them: to another value, to its default, or
     * away from its default where the user's file has none.
     */
    private static final Map<String, UnaryOperator<Settings>> CHANGES = LinkedHashMap.ofEntries(
            change(Settings.THEME.path(), settings -> settings.withTheme(ThemeName.LIGHT)),
            change(Settings.REPOSITORY_WIDTH.path(), settings -> settings.withRepositoryWidth(Settings.DEFAULT_WIDTH)),
            change(Settings.DIFF_STYLE.path(), settings -> settings.withDiffStyle(DiffStyle.PLAIN)),
            change(Settings.DIFF_SYNTAX.path(), settings -> settings.withDiffSyntax(true)),
            change(Settings.DIFF_WORDS.path(), settings -> settings.withDiffWords(false)),
            change(Settings.DIFF_PAIRS.path(), settings -> settings.withDiffPairs(false)),
            change(Settings.REPOSITORY_HEIGHTS.path(), settings -> settings.withRepositoryHeights(List.of(25, 25, 25))),
            change(Settings.ANIMATIONS.path(), settings -> settings.withAnimations(true)),
            change(Settings.AUTO_REFRESH.path(), settings -> settings.withAutoRefresh(false)),
            // The last key of its table: the table goes too.
            change(Settings.AUTO_FETCH.path(), settings -> settings.withAutoFetch(true)),
            change(
                    Settings.DIFF_FILTERS.path(),
                    settings -> settings.withDiffFilters(List.of("java-imports", "lockfiles"))),
            change(
                    GitHubSettings.GH_EXECUTABLE.path(),
                    settings -> settings.with(GitHubSettings.GH_EXECUTABLE, Option.none())),
            change(
                    GitHubSettings.BROWSERS.path(),
                    settings -> settings.with(
                            GitHubSettings.BROWSERS,
                            settings.get(GitHubSettings.BROWSERS)
                                    .with("github.com", "cvf-work", Option.some("firefox -P work")))),
            // The layout of GitHub's screen, at the paths the kernel kept it at (ADR 168).
            change(PrOverview.WIDTH.path(), settings -> settings.with(PrOverview.WIDTH, 50)),
            change(PrOverview.HEIGHTS.path(), settings -> settings.with(PrOverview.HEIGHTS, List.of(40, 30))),
            change(
                    Agents.SETTING.path(),
                    settings -> settings.with(
                            Agents.SETTING,
                            settings.get(Agents.SETTING)
                                    .added(new AgentProgram(
                                            "Codex", List.of("codex"), true, Option.none(), Option.of("Fix it"))))));

    @TempDir
    Path dir;

    @Test
    void theUsersFileIsReadAsItWas() throws IOException {
        assertEquals(
                new Loaded(USERS_SETTINGS, Option.none(), StandardSettings.keys()),
                usersFile().load());
    }

    @Test
    void eachSettingTheAppWritesHasAChange() {
        List<String> written = StandardSettings.keys()
                .all()
                .map(SettingKey::path)
                .removeAll(List.of(EditorSettings.EDITORS.path(), Settings.FILTER_DEFINITIONS.path()));

        assertEquals(written, CHANGES.keySet().toList());
    }

    static Stream<String> changed() {
        return CHANGES.keySet().toJavaStream();
    }

    @ParameterizedTest
    @MethodSource("changed")
    void changingOneSettingWritesWhatItDid(String key) throws IOException {
        SettingsFile file = usersFile();
        Settings read = file.load().settings();

        file.save(read, CHANGES.get(key).get().apply(read));

        assertWritten(key);
    }

    @Test
    void changingEverySettingWritesWhatItDid() throws IOException {
        SettingsFile file = usersFile();
        Settings read = file.load().settings();

        file.save(read, CHANGES.values().foldLeft(read, (settings, change) -> change.apply(settings)));

        assertWritten("every-setting");
    }

    @Test
    void settingAllBackToTheirDefaultWritesWhatItDid() throws IOException {
        SettingsFile file = usersFile();
        Settings read = file.load().settings();

        file.save(
                read,
                Settings.DEFAULT
                        .with(EditorSettings.EDITORS, read.get(EditorSettings.EDITORS))
                        .withFilterDefinitions(read.filterDefinitions()));

        assertWritten("back-to-default");
    }

    @Test
    void savingWithoutAFileWritesWhatItDid() throws IOException {
        SettingsFile file = new SettingsFile(dir.resolve("config.toml"), StandardSettings.keys());

        file.save(
                Settings.DEFAULT,
                CHANGES.values().foldLeft(Settings.DEFAULT, (settings, change) -> change.apply(settings)));

        assertWritten("new-file");
    }

    private SettingsFile usersFile() throws IOException {
        Path file = dir.resolve("config.toml");
        Files.writeString(file, USERS_FILE);
        return new SettingsFile(file, StandardSettings.keys());
    }

    /**
     * That the settings file is as {@code name} has it; when updating, it is kept as {@code name} instead.
     */
    private void assertWritten(String name) throws IOException {
        String written = Files.readString(dir.resolve("config.toml"));
        String resource = name + ".toml";
        if (Boolean.getBoolean("settings.round-trip.update")) {
            Path kept = Path.of("src", "test", "resources", "settings-round-trip", resource);
            Files.createDirectories(kept.getParent());
            Files.writeString(kept, written);
        } else {
            assertEquals(expected(resource), written, name);
        }
    }

    private static String expected(String resource) throws IOException {
        try (InputStream in =
                SettingsRoundTripGitHubTest.class.getResourceAsStream("/settings-round-trip/" + resource)) {
            return in == null ? "(no " + resource + ")" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Tuple2<String, UnaryOperator<Settings>> change(String key, UnaryOperator<Settings> change) {
        return Tuple.of(key, change);
    }
}
