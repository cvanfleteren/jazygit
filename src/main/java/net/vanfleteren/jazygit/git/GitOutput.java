package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.Sha;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Splitting and reading what git prints. Whatever does not have the expected shape is an
 * {@link UnexpectedOutput}, never an exception.
 */
final class GitOutput {

    /**
     * What separates the fields of one record in a {@code --format}: the unit separator, which git writes
     * as {@code %x1f}. It does not occur in names, messages or paths.
     */
    static final String FIELD_SEPARATOR = "\u001f";
    /**
     * {@link #FIELD_SEPARATOR} in the {@code --format} of {@code git log} and other commands that take a
     * pretty format.
     */
    static final String PRETTY_FIELD_SEPARATOR = "%x1f";
    /**
     * {@link #FIELD_SEPARATOR} in the {@code --format} of {@code git for-each-ref}.
     */
    static final String REF_FIELD_SEPARATOR = "%1f";

    private GitOutput() {
    }

    /**
     * The lines of {@code raw}.
     */
    static List<String> lines(String raw) {
        return List.ofAll(raw.lines());
    }

    /**
     * The lines of {@code raw} that are not blank.
     */
    static List<String> nonBlankLines(String raw) {
        return lines(raw).filter(line -> !line.isBlank());
    }

    /**
     * The NUL-terminated records of {@code -z} output, without the blank ones.
     */
    static List<String> records(String raw) {
        return List.of(raw.split("\0")).filter(record -> !record.isBlank());
    }

    /**
     * The {@code count} fields of {@code record}, separated by {@link #FIELD_SEPARATOR}; the last one takes
     * the rest of the record.
     */
    static Either<UnexpectedOutput, List<String>> fields(String record, int count) {
        return fields(record, FIELD_SEPARATOR, count);
    }

    /**
     * The {@code count} fields of {@code record}, separated by {@code separator}; the last one takes the rest
     * of the record.
     */
    static Either<UnexpectedOutput, List<String>> fields(String record, String separator, int count) {
        List<String> fields = List.of(record.split(Pattern.quote(separator), count));
        return fields.size() == count ? Either.right(fields) : Either.left(new UnexpectedOutput(record));
    }

    static Either<UnexpectedOutput, Sha> sha(String text) {
        return Sha.parse(text).toEither(() -> new UnexpectedOutput(text));
    }

    static Either<UnexpectedOutput, Instant> epochSeconds(String text) {
        return number(text).map(Instant::ofEpochSecond);
    }

    static Either<UnexpectedOutput, Long> number(String text) {
        return Try.of(() -> Long.parseLong(text.strip())).toEither().mapLeft(e -> new UnexpectedOutput(text));
    }

    /**
     * The text after {@code prefix}, if {@code text} starts with it.
     */
    static Option<String> after(String text, String prefix) {
        return text.startsWith(prefix) ? Option.of(text.substring(prefix.length())) : Option.none();
    }
}
