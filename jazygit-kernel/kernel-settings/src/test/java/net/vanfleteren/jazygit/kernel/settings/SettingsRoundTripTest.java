package net.vanfleteren.jazygit.kernel.settings;

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
import net.vanfleteren.jazygit.kernel.settings.SettingsStore.Loaded;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The settings file, byte for byte: a user's file is read as it was, and a change to one setting writes exactly what
 * it wrote before. This holds the settings to their file while their code is reshaped.
 *
 * <p>The files written are kept in {@code src/test/resources/settings-round-trip}. When a change to what is written
 * is wanted, run this test with {@code -Dsettings.round-trip.update=true} to write them again, and review the diff.
 */
class SettingsRoundTripTest {

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

            [[agents]]
            # the main one
            name = "Claude"
            command = ["claude", "{prompt}"]
            terminal = true
            shortcut = "c"
            prompt = "Fix {failures}"

            [mine]
            x = [1, 2]
            """;

    /**
     * The settings of {@link #USERS_FILE}.
     */
    private static final Settings USERS_SETTINGS = Settings.DEFAULT
            .withTheme(ThemeName.DARCULA)
            .withGhExecutable(Option.some("/opt/gh"))
            .withRepositoryWidth(40)
            .withDiffSyntax(false)
            .withRepositoryHeights(List.of(20, 20, 20))
            .withAnimations(false)
            .withAutoFetch(false)
            .withBrowsers(AccountBrowsers.NONE.with("github.com", "cvf", Option.some("firefox -P personal")))
            .withEditors(new Editors(
                    List.of(EditorProgram.ofDir("IntelliJ", List.of("idea", "{dir}"), false, Option.some('i')))))
            .withDiffFilters(List.of("java-imports"))
            .withFilterDefinitions(new FilterDefinitions(List.of(new FilterDefinition(
                    "mine",
                    Option.some("Generated sources"),
                    List.empty(),
                    List.of("**/generated/**"),
                    Option.none(),
                    false))))
            .withAgents(new Agents(List.of(new AgentProgram(
                    "Claude", List.of("claude", "{prompt}"), true, Option.some('c'), Option.some("Fix {failures}")))));

    /**
     * A change to each setting the app writes, by the key it has in the file: to another value, to its default, or
     * away from its default where the user's file has none.
     */
    private static final Map<String, UnaryOperator<Settings>> CHANGES = LinkedHashMap.ofEntries(
            change(Settings.THEME.path(), settings -> settings.withTheme(ThemeName.LIGHT)),
            change(Settings.GH_EXECUTABLE.path(), settings -> settings.withGhExecutable(Option.none())),
            change(Settings.REPOSITORY_WIDTH.path(), settings -> settings.withRepositoryWidth(Settings.DEFAULT_WIDTH)),
            change(Settings.PULL_REQUESTS_WIDTH.path(), settings -> settings.withPullRequestsWidth(50)),
            change(Settings.DIFF_STYLE.path(), settings -> settings.withDiffStyle(DiffStyle.PLAIN)),
            change(Settings.DIFF_SYNTAX.path(), settings -> settings.withDiffSyntax(true)),
            change(Settings.DIFF_WORDS.path(), settings -> settings.withDiffWords(false)),
            change(Settings.REPOSITORY_HEIGHTS.path(), settings -> settings.withRepositoryHeights(List.of(25, 25, 25))),
            change(
                    Settings.PULL_REQUESTS_HEIGHTS.path(),
                    settings -> settings.withPullRequestsHeights(List.of(40, 30))),
            change(Settings.ANIMATIONS.path(), settings -> settings.withAnimations(true)),
            change(Settings.AUTO_REFRESH.path(), settings -> settings.withAutoRefresh(false)),
            // The last key of its table: the table goes too.
            change(Settings.AUTO_FETCH.path(), settings -> settings.withAutoFetch(true)),
            change(
                    Settings.BROWSERS.path(),
                    settings -> settings.withBrowsers(
                            settings.browsers().with("github.com", "cvf-work", Option.some("firefox -P work")))),
            change(
                    Settings.DIFF_FILTERS.path(),
                    settings -> settings.withDiffFilters(List.of("java-imports", "lockfiles"))));

    @TempDir
    Path dir;

    @Test
    void theUsersFileIsReadAsItWas() throws IOException {
        assertEquals(new Loaded(USERS_SETTINGS, Option.none()), usersFile().load());
    }

    @Test
    void eachSettingTheAppWritesHasAChange() {
        List<String> written =
                Setting.ALL.map(Setting::path).removeAll(List.of(Editors.KEY, FilterDefinitions.KEY, Agents.KEY));

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
                        .withEditors(read.editors())
                        .withFilterDefinitions(read.filterDefinitions())
                        .withAgents(read.agents()));

        assertWritten("back-to-default");
    }

    @Test
    void savingWithoutAFileWritesWhatItDid() throws IOException {
        SettingsFile file = new SettingsFile(dir.resolve("config.toml"));

        file.save(
                Settings.DEFAULT,
                CHANGES.values().foldLeft(Settings.DEFAULT, (settings, change) -> change.apply(settings)));

        assertWritten("new-file");
    }

    private SettingsFile usersFile() throws IOException {
        Path file = dir.resolve("config.toml");
        Files.writeString(file, USERS_FILE);
        return new SettingsFile(file);
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
        try (InputStream in = SettingsRoundTripTest.class.getResourceAsStream("/settings-round-trip/" + resource)) {
            return in == null ? "(no " + resource + ")" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Tuple2<String, UnaryOperator<Settings>> change(String key, UnaryOperator<Settings> change) {
        return Tuple.of(key, change);
    }
}
