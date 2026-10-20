package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.BiFunction;

/**
 * What a search of the commits looks for, as typed on one line: bare words, and the prefixes {@code author:},
 * {@code path:} and {@code -S:}. A value with spaces goes in double quotes, as in {@code author:"Jane Doe"}. Only
 * those prefixes count: {@code feat:} is text of the message. A commit matches when it matches every filter given.
 *
 * @param message the bare words, as one phrase the message contains
 * @param authors the authors, of whom the commit has one
 * @param paths   the paths, of which the commit changes one
 * @param pickaxe the text the commit changes how often it occurs, as {@code git log -S} looks for it
 */
public record CommitQuery(Option<String> message, List<String> authors, List<String> paths, Option<String> pickaxe) {

    private static final List<Prefix> PREFIXES = List.of(
            new Prefix("author:", (query, value) -> query.withAuthors(query.authors.append(value))),
            new Prefix("path:", (query, value) -> query.withPaths(query.paths.append(value))),
            new Prefix("-S:", (query, value) -> query.withPickaxe(Option.of(value))));

    private static final CommitQuery NOTHING =
            new CommitQuery(Option.none(), List.empty(), List.empty(), Option.none());

    /**
     * The query {@code text} types. A prefix with nothing after it filters nothing; the last {@code -S:} counts.
     */
    public static CommitQuery parse(String text) {
        List<String> tokens = tokens(text);
        CommitQuery prefixed = tokens.foldLeft(
                NOTHING,
                (query, token) -> PREFIXES.find(prefix -> token.startsWith(prefix.name()))
                        .map(prefix -> Option.of(
                                        unquoted(token.substring(prefix.name().length())))
                                .filter(value -> !value.isEmpty())
                                .map(value -> prefix.add().apply(query, value))
                                .getOrElse(query))
                        .getOrElse(query));
        String words = tokens.filter(token -> PREFIXES.forAll(prefix -> !token.startsWith(prefix.name())))
                .map(CommitQuery::unquoted)
                .filter(word -> !word.isEmpty())
                .mkString(" ");
        return prefixed.withMessage(Option.when(!words.isEmpty(), words));
    }

    /**
     * Whether the query filters nothing.
     */
    public boolean isEmpty() {
        return equals(NOTHING);
    }

    /**
     * Whether the message, the authors and the pickaxe match ignoring case: unless one of them has an uppercase
     * letter. The paths always keep their case.
     */
    public boolean ignoresCase() {
        return authors.appendAll(message)
                .appendAll(pickaxe)
                .forAll(text -> text.chars().noneMatch(Character::isUpperCase));
    }

    public CommitQuery withMessage(Option<String> message) {
        return new CommitQuery(message, authors, paths, pickaxe);
    }

    public CommitQuery withAuthors(List<String> authors) {
        return new CommitQuery(message, authors, paths, pickaxe);
    }

    public CommitQuery withPaths(List<String> paths) {
        return new CommitQuery(message, authors, paths, pickaxe);
    }

    public CommitQuery withPickaxe(Option<String> pickaxe) {
        return new CommitQuery(message, authors, paths, pickaxe);
    }

    /**
     * The words of {@code text} as typed, quotes included: separated by spaces, except those between quotes. A
     * quote that is not closed runs to the end.
     */
    private static List<String> tokens(String text) {
        return List.ofAll(text.toCharArray())
                .foldLeft(new Tokens(List.empty(), "", false), Tokens::add)
                .done();
    }

    private static String unquoted(String token) {
        return token.replace("\"", "");
    }

    /**
     * The words read so far, the one being read, and whether that one is between quotes.
     */
    private record Tokens(List<String> read, String current, boolean quoted) {

        Tokens add(char c) {
            return !quoted && Character.isWhitespace(c)
                    ? new Tokens(done(), "", false)
                    : new Tokens(read, current + c, quoted ^ c == '"');
        }

        List<String> done() {
            return current.isEmpty() ? read : read.append(current);
        }
    }

    /**
     * A prefix, and how a value after it adds to a query.
     */
    private record Prefix(String name, BiFunction<CommitQuery, String, CommitQuery> add) {}
}
