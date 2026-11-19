package net.vanfleteren.jazygit.core.git.model;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.Locale;
import java.util.regex.PatternSyntaxException;
import net.vanfleteren.jazygit.lib.syntax.Language;

/**
 * A filter that hides the hunks of a diff not worth reviewing, like imports or a lockfile (ADR 144). It applies to the
 * files of its {@code languages}, or those its {@code paths} name; a hunk of such a file is hidden when its changed
 * lines all match it, as {@link DiffFiltering} has it.
 *
 * @param id        what the settings name it by, and what a filter of the user replaces a built-in one by
 * @param name      what it is shown as
 * @param languages the languages of the files it applies to
 * @param paths     the globs of the files it applies to besides
 * @param lines     the patterns a changed line matches it by, anywhere in the line; none when it matches every line,
 *                  which hides all of a file
 */
public record DiffFilter(
        String id, String name, Set<Language> languages, List<PathGlob> paths, Option<List<Regex>> lines) {

    /**
     * The filter {@code id} as it is written, or what is wrong with it: a pattern that does not compile, a language
     * not known, or neither languages nor paths.
     */
    public static Either<Problem, DiffFilter> of(
            String id, Option<String> name, List<String> languages, List<String> paths, Option<List<String>> lines) {
        if (languages.isEmpty() && paths.isEmpty()) {
            return Either.left(new Problem.NoScope());
        }
        Option<String> unknown = languages.find(language -> language(language).isEmpty());
        if (unknown.isDefined()) {
            return Either.left(new Problem.UnknownLanguage(unknown.get()));
        }
        Either<Problem, Option<List<Regex>>> compiled = lines.fold(
                () -> Either.right(Option.none()),
                patterns -> Either.sequenceRight(patterns.map(DiffFilter::compiled))
                        .map(List::ofAll)
                        .map(Option::some));
        return compiled.map(patterns -> new DiffFilter(
                id,
                name.getOrElse(id),
                languages.flatMap(DiffFilter::language).toSet(),
                paths.map(PathGlob::of),
                patterns));
    }

    /**
     * Whether it applies to the file at {@code path}: by its language, as its name or extension tells it, or by one
     * of its globs.
     */
    public boolean appliesTo(String path) {
        return Language.of(path, Option.none()).exists(languages::contains) || paths.exists(glob -> glob.matches(path));
    }

    /**
     * Whether the changed line {@code text}, without its marker, matches it.
     */
    public boolean matches(String text) {
        return lines.fold(() -> true, patterns -> patterns.exists(pattern -> pattern.find(text)));
    }

    /**
     * The language {@code name} stands for: a {@link Language} in lower case.
     */
    private static Option<Language> language(String name) {
        return List.of(Language.values())
                .find(language -> language.name().toLowerCase(Locale.ROOT).equals(name));
    }

    private static Either<Problem, Regex> compiled(String pattern) {
        return Try.of(() -> Regex.of(pattern))
                .toEither()
                .mapLeft(failure -> new Problem.BadPattern(
                        pattern,
                        failure instanceof PatternSyntaxException syntax
                                ? syntax.getDescription()
                                : String.valueOf(failure.getMessage())));
    }

    /**
     * Why a filter as it is written cannot be used.
     */
    public sealed interface Problem {

        /**
         * It names neither languages nor paths, so it applies to no file.
         */
        record NoScope() implements Problem {}

        /**
         * It names a language the lexer does not know.
         */
        record UnknownLanguage(String name) implements Problem {}

        /**
         * One of its patterns does not compile, for {@code reason}.
         */
        record BadPattern(String pattern, String reason) implements Problem {}
    }
}
