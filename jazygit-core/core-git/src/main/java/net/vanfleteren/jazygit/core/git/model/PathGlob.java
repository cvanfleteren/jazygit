package net.vanfleteren.jazygit.core.git.model;

import io.vavr.collection.List;
import java.util.regex.Pattern;

/**
 * A glob that picks the files a diff filter applies to (ADR 144). One without a {@code /} is matched against the name
 * of a file, so {@code go.sum} is every {@code go.sum}; one with a {@code /} against its path from the root of the
 * repository. {@code *} and {@code ?} do not cross a {@code /}, {@code **} does, {@code [abc]} and {@code {a,b}} are
 * as {@code FileSystem.getPathMatcher("glob:…")} has them. A glob that starts with {@code **}{@code /} also matches
 * without it, as git has it, so {@code **}{@code /__snapshots__/*.snap} matches a {@code __snapshots__} at the root.
 *
 * <p>It is made with {@link #of}: a record's constructor is as public as the record, and is not to be called with a
 * {@code regex} that is not {@code glob} translated.
 *
 * @param glob  the glob as it was written
 * @param regex what it matches, equal for globs written alike (ADR 144)
 */
public record PathGlob(String glob, Regex regex) {

    /**
     * {@code glob}, ready to match.
     */
    public static PathGlob of(String glob) {
        String leading = "**/";
        String body = glob.startsWith(leading) ? glob.substring(leading.length()) : glob;
        String prefix = glob.startsWith(leading) ? "(?:.*/)?" : "";
        return new PathGlob(glob, Regex.of(prefix + translated(List.ofAll(body.toCharArray()), false)));
    }

    /**
     * Whether the file at {@code path}, from the root of the repository, is one the glob names.
     */
    public boolean matches(String path) {
        String subject = glob.contains("/") ? path : path.substring(path.lastIndexOf('/') + 1);
        return regex.matches(subject);
    }

    /**
     * The regex of the glob's characters {@code rest}, inside braces when {@code braced}.
     */
    private static String translated(List<Character> rest, boolean braced) {
        if (rest.isEmpty()) {
            return "";
        }
        char c = rest.head();
        List<Character> tail = rest.tail();
        return switch (c) {
            case '*' ->
                !tail.isEmpty() && tail.head() == '*'
                        ? ".*" + translated(tail.tail(), braced)
                        : "[^/]*" + translated(tail, braced);
            case '?' -> "[^/]" + translated(tail, braced);
            case '[' -> {
                int close = tail.indexOf(']');
                if (close < 0) {
                    yield Pattern.quote("[") + translated(tail, braced);
                }
                List<Character> inside = tail.take(close);
                String negated = !inside.isEmpty() && inside.head() == '!' ? "^" : "";
                String members = (negated.isEmpty() ? inside : inside.tail())
                        .map(m -> m == '\\' || m == '[' || m == '&' ? "\\" + m : String.valueOf(m))
                        .mkString();
                yield "[" + negated + members + "]" + translated(tail.drop(close + 1), braced);
            }
            case '{' -> "(?:" + translated(tail, true);
            case '}' -> braced ? ")" + translated(tail, false) : Pattern.quote("}") + translated(tail, braced);
            case ',' -> braced ? "|" + translated(tail, braced) : Pattern.quote(",") + translated(tail, braced);
            default -> Pattern.quote(String.valueOf(c)) + translated(tail, braced);
        };
    }
}
