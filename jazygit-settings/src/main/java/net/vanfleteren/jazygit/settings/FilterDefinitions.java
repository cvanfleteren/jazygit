package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The diff filters as {@code [[diff-filters]]} tables list them, in their order (ADR 144): those that come with jazygit,
 * in a file of its own, or those the user set in the settings file. The user's are only read, never written by the
 * app.
 *
 * @param all the filters, in the order of the file
 */
public record FilterDefinitions(List<FilterDefinition> all) {

    /**
     * No filter.
     */
    public static final FilterDefinitions NONE = new FilterDefinitions(List.empty());

    /**
     * The key of the filters in the settings file: an array of tables.
     */
    static final String KEY = "diff-filters";

    private static final String RESOURCE = "diff-filters.toml";
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String LANGUAGES = "languages";
    private static final String PATHS = "paths";
    private static final String LINES = "lines";

    /**
     * The filters that come with jazygit, read once from the file in the jar.
     */
    private static final FilterDefinitions BUILT_IN = builtIn();

    /**
     * The filters to choose from with these, the user's, set: those that come with jazygit, each replaced by the user's
     * of the same id, then the user's other ones.
     */
    public FilterDefinitions withBuiltIn() {
        List<FilterDefinition> own = all;
        List<FilterDefinition> builtIn = BUILT_IN.all()
                .map(filter -> own.find(mine -> mine.id().equals(filter.id())).getOrElse(filter));
        return new FilterDefinitions(builtIn.appendAll(own.filter(
                mine -> !BUILT_IN.all().exists(filter -> filter.id().equals(mine.id())))));
    }

    /**
     * The filters {@code value} holds, an array of tables as the settings file is read, with the keys of what is
     * wrong in it, such as {@code diff-filters[2].id}, counted from 1 as the tables are in the file. An entry without
     * an id, one whose id an earlier entry has, or with a key that is not of its kind, is left out. When {@code value}
     * is no array, there are none, and {@code diff-filters} itself is wrong.
     */
    static Read read(Object value, boolean builtIn) {
        return Option.of(value)
                .filter(java.util.List.class::isInstance)
                .map(array -> List.ofAll((java.util.List<?>) array))
                .map(tables -> tables.zipWithIndex()
                        .foldLeft(
                                new Read(NONE, List.empty()),
                                (read, table) -> read.and(entry(table._1, table._2 + 1, read, builtIn))))
                .getOrElse(() -> new Read(NONE, List.of(KEY)));
    }

    /**
     * The filter of the entry at {@code number}, counted from 1, if it is one, with what is wrong in it; {@code before}
     * has the filters of the entries before it, whose ids it cannot take.
     */
    private static Read entry(Object value, int number, Read before, boolean builtIn) {
        String at = KEY + "[" + number + "].";
        if (!(value instanceof java.util.Map<?, ?> table)) {
            return new Read(NONE, List.of(KEY + "[" + number + "]"));
        }
        Option<String> id = text(table.get(ID))
                .filter(given ->
                        !before.filters().all().exists(filter -> filter.id().equals(given)));
        Object name = table.get(NAME);
        Option<List<String>> languages = texts(table.get(LANGUAGES));
        Option<List<String>> paths = texts(table.get(PATHS));
        Option<List<String>> lines = texts(table.get(LINES));
        List<String> wrong = Option.when(id.isEmpty(), at + ID)
                .toList()
                .appendAll(Option.when(name != null && text(name).isEmpty(), at + NAME))
                .appendAll(Option.when(table.get(LANGUAGES) != null && languages.isEmpty(), at + LANGUAGES))
                .appendAll(Option.when(table.get(PATHS) != null && paths.isEmpty(), at + PATHS))
                .appendAll(Option.when(table.get(LINES) != null && lines.isEmpty(), at + LINES));
        if (wrong.nonEmpty()) {
            return new Read(NONE, wrong);
        }
        return new Read(
                new FilterDefinitions(List.of(new FilterDefinition(
                        id.get(),
                        text(name),
                        languages.getOrElse(List.empty()),
                        paths.getOrElse(List.empty()),
                        Option.when(table.get(LINES) != null, lines::get),
                        builtIn))),
                List.empty());
    }

    private static FilterDefinitions builtIn() {
        return Try.withResources(() -> new InputStreamReader(
                        Objects.requireNonNull(FilterDefinitions.class.getResourceAsStream(RESOURCE)),
                        StandardCharsets.UTF_8))
                .of((Reader reader) -> TomlFile.parse(reader))
                .map(config -> read(SettingsFile.plain(config.get(KEY)), true).filters())
                .getOrElse(NONE);
    }

    /**
     * {@code value}, if it is text that is not blank.
     */
    private static Option<String> text(Object value) {
        return Option.of(value)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(text -> !text.isBlank());
    }

    /**
     * {@code value}, if it is an array of text, none of it blank.
     */
    private static Option<List<String>> texts(Object value) {
        return Option.of(value)
                .filter(java.util.List.class::isInstance)
                .map(array -> List.ofAll((java.util.List<?>) array))
                .filter(items -> items.forAll(item -> text(item).isDefined()))
                .map(items -> items.map(String.class::cast));
    }

    /**
     * Filters as read, with the keys of what was wrong in them.
     */
    record Read(FilterDefinitions filters, List<String> invalid) {

        /**
         * These filters and those of {@code next} after them, with what was wrong in both.
         */
        Read and(Read next) {
            return new Read(
                    new FilterDefinitions(filters.all().appendAll(next.filters().all())),
                    invalid.appendAll(next.invalid()));
        }
    }
}
