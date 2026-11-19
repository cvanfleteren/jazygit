package net.vanfleteren.jazygit.kernel.settings;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore.Loaded;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilterDefinitionsTest {

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("config.toml");
    }

    private Loaded load(String content) throws IOException {
        Files.writeString(file(), content);
        return new SettingsFile(file()).load();
    }

    @Test
    void theBuiltInFiltersAreReadFromTheJarEachWithAnIdOfItsOwn() {
        List<FilterDefinition> builtIn = FilterDefinitions.NONE.withBuiltIn().all();

        assertTrue(builtIn.size() >= 15, builtIn.toString());
        assertTrue(builtIn.forAll(FilterDefinition::builtIn));
        assertEquals(
                builtIn.size(), builtIn.map(FilterDefinition::id).distinct().size());
        assertTrue(builtIn.map(FilterDefinition::id).containsAll(List.of("java-imports", "lockfiles", "generated")));
    }

    @Test
    void aBuiltInFilterWithoutLinesHasNone() {
        FilterDefinition lockfiles = FilterDefinitions.NONE
                .withBuiltIn()
                .all()
                .find(filter -> filter.id().equals("lockfiles"))
                .get();

        assertEquals(Option.none(), lockfiles.lines());
        assertTrue(lockfiles.paths().contains("flake.lock"));
    }

    @Test
    void readsTheUsersFiltersBesideTheEnabledOnes() throws IOException {
        Loaded loaded = load("""
                [ui]
                diff-filters = ["java-imports", "mine"]

                [[diff-filters]]
                id = "mine"
                name = "Generated sources"
                paths = ["**/generated/**"]

                [[diff-filters]]
                id = "logs"
                languages = ["java"]
                lines = ['^\\s*log\\.debug\\(']
                """);

        assertEquals(Option.none(), loaded.problem());
        assertEquals(List.of("java-imports", "mine"), loaded.settings().diffFilters());
        assertEquals(
                List.of(
                        new FilterDefinition(
                                "mine",
                                Option.of("Generated sources"),
                                List.empty(),
                                List.of("**/generated/**"),
                                Option.none(),
                                false),
                        new FilterDefinition(
                                "logs",
                                Option.none(),
                                List.of("java"),
                                List.empty(),
                                Option.of(List.of("^\\s*log\\.debug\\(")),
                                false)),
                loaded.settings().filterDefinitions().all());
    }

    @Test
    void anEntryThatIsWrongIsLeftOutAndSaidSo() throws IOException {
        Loaded loaded = load("""
                [[diff-filters]]
                name = "No id"
                paths = ["*"]

                [[diff-filters]]
                id = "lines"
                paths = ["*"]
                lines = "not an array"

                [[diff-filters]]
                id = "fine"
                paths = ["*.lock"]

                [[diff-filters]]
                id = "fine"
                paths = ["*.again"]
                """);

        assertEquals(
                Option.of(new SettingsProblem.Invalid(
                        file(), List.of("diff-filters[1].id", "diff-filters[2].lines", "diff-filters[4].id"))),
                loaded.problem());
        assertEquals(
                List.of("fine"), loaded.settings().filterDefinitions().all().map(FilterDefinition::id));
    }

    @Test
    void aFilterOfTheUserReplacesTheBuiltInOneOfItsIdInItsPlace() {
        FilterDefinition mine = new FilterDefinition(
                "lockfiles", Option.of("Mine"), List.empty(), List.of("my.lock"), Option.none(), false);
        FilterDefinition other =
                new FilterDefinition("other", Option.none(), List.empty(), List.of("x"), Option.none(), false);

        List<FilterDefinition> all =
                new FilterDefinitions(List.of(other, mine)).withBuiltIn().all();
        List<FilterDefinition> builtIn = FilterDefinitions.NONE.withBuiltIn().all();

        assertEquals(builtIn.size() + 1, all.size());
        assertEquals(mine, all.get(builtIn.map(FilterDefinition::id).indexOf("lockfiles")));
        assertEquals(other, all.last());
    }

    @Test
    void savingTheEnabledFiltersKeepsTheUsersFilters() throws IOException {
        Settings loaded = load("""
                [[diff-filters]]
                id = "mine"
                paths = ["*.gen"]
                """).settings();

        new SettingsFile(file()).save(loaded, loaded.withDiffFilters(List.of("mine", "lockfiles")));

        Loaded reread = new SettingsFile(file()).load();
        assertEquals(Option.none(), reread.problem());
        assertEquals(List.of("mine", "lockfiles"), reread.settings().diffFilters());
        assertEquals(
                List.of("mine"), reread.settings().filterDefinitions().all().map(FilterDefinition::id));
    }
}
