package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.regex.Pattern;

/**
 * What the base branch of a pull request on Forgejo requires before it is merged, as far as jazygit tells it (ADR
 * 181), as {@code GET /repos/{owner}/{repo}/branches/{branch}} gives it.
 *
 * @param guarded           whether a rule of the repository protects the branch
 * @param statusChecks      whether the rule requires checks to pass
 * @param contexts          the checks it requires, as globs; none for all of them, by their combined state
 * @param requiredApprovals how many approvals it requires
 */
public record ForgejoBranchRules(boolean guarded, boolean statusChecks, List<String> contexts, int requiredApprovals) {

    /**
     * No rule: nothing is waited for.
     */
    public static final ForgejoBranchRules NONE = new ForgejoBranchRules(false, false, List.empty(), 0);

    /**
     * Whether a pull request whose head's checks are {@code status}, with {@code approvals} approvals, is not merged
     * yet by these rules: a required check has not passed, or approvals are missing. Forgejo merges it once they are
     * met when it is merged when its checks succeed; else it refuses it.
     */
    public boolean waitFor(CommitStatus status, int approvals) {
        return guarded && ((statusChecks && !passed(status)) || approvals < requiredApprovals);
    }

    /**
     * Whether the checks are passed as Forgejo tells it: without required contexts, their combined state succeeded,
     * which none do not; with them, each glob matches a check, and every check it matches passed. A context that is
     * no glob is skipped, as Forgejo skips one it cannot compile.
     */
    private boolean passed(CommitStatus status) {
        if (contexts.isEmpty()) {
            return !status.statuses().isEmpty()
                    && status.statuses().forAll(check -> check.state() == CommitStatus.State.PASSED);
        }
        return contexts.flatMap(ForgejoBranchRules::glob).forAll(glob -> {
            List<CommitStatus.Status> matched = status.statuses()
                    .filter(check -> glob.matcher(check.context()).matches());
            return !matched.isEmpty() && matched.forAll(check -> check.state() == CommitStatus.State.PASSED);
        });
    }

    /**
     * {@code pattern} as Forgejo compiles a required context, with gobwas' glob and no separators: {@code *} any
     * text, {@code ?} any character, a class {@code [...]} one of its characters and {@code [!...]} one not of them,
     * and {@code {a,b}} either; {@code \} takes the character after it as it is; anything else as it is. A class is,
     * as gobwas' lexer reads it, a range {@code [a-z]}, or else characters up to {@code ]}, each only itself, as
     * {@code -} is then. A {@code \} that ends it is dropped, as gobwas does. None when it is no glob: a class or a
     * brace left open, a class with no character, or a range not closed right after it.
     */
    static Option<Pattern> glob(String pattern) {
        return regex(pattern, 0, 0, "")
                .flatMap(regex ->
                        Try.of(() -> Pattern.compile(regex, Pattern.DOTALL)).toOption());
    }

    // The regular expression of glob from at on, after soFar, with braces open; none where gobwas' lexer fails.
    private static Option<String> regex(String glob, int at, int braces, String soFar) {
        if (at == glob.length()) {
            return braces > 0 ? Option.none() : Option.of(soFar);
        }
        char c = glob.charAt(at);
        if (c == '[') {
            return characterClass(glob, at + 1).flatMap(read -> regex(glob, read.end(), braces, soFar + read.regex()));
        }
        if (c == '\\') {
            return at + 1 == glob.length()
                    ? regex(glob, at + 1, braces, soFar)
                    : regex(glob, at + 2, braces, soFar + quote(glob.charAt(at + 1)));
        }
        return switch (c) {
            case '{' -> regex(glob, at + 1, braces + 1, soFar + "(?:");
            case '}' -> regex(glob, at + 1, Math.max(braces - 1, 0), soFar + (braces > 0 ? ")" : quote(c)));
            case ',' -> regex(glob, at + 1, braces, soFar + (braces > 0 ? "|" : quote(c)));
            case '*' -> regex(glob, at + 1, braces, soFar + ".*");
            case '?' -> regex(glob, at + 1, braces, soFar + ".");
            default -> regex(glob, at + 1, braces, soFar + quote(c));
        };
    }

    private static String quote(char c) {
        return Pattern.quote(String.valueOf(c));
    }

    /**
     * The class of characters of {@code glob} from {@code from}, just after its {@code [}, as gobwas' lexer reads it
     * ({@code fetchRange}): after a {@code !}, a range when its second character is {@code -}, which {@code ]} must
     * close right after; else characters up to {@code ]}, at least one, each itself, a {@code \} taking the next as it
     * is.
     */
    private static Option<Read> characterClass(String glob, int from) {
        boolean not = from < glob.length() && glob.charAt(from) == '!';
        int at = not ? from + 1 : from;
        String open = not ? "[^" : "[";
        if (at + 1 < glob.length() && glob.charAt(at + 1) == '-') {
            return at + 3 < glob.length() && glob.charAt(at + 3) == ']'
                    ? Option.of(new Read(
                            open + literal(glob.charAt(at)) + "-" + literal(glob.charAt(at + 2)) + "]", at + 4))
                    : Option.none();
        }
        return characters(glob, at, "")
                .filter(read -> !read.regex().isEmpty())
                .map(read -> new Read(open + read.regex() + "]", read.end()));
    }

    // The characters of a class from at up to its ], after soFar, each itself; none when it is left open.
    private static Option<Read> characters(String glob, int at, String soFar) {
        if (at == glob.length()) {
            return Option.none();
        }
        char c = glob.charAt(at);
        if (c == ']') {
            return Option.of(new Read(soFar, at + 1));
        }
        if (c == '\\') {
            return at + 1 == glob.length()
                    ? Option.none()
                    : characters(glob, at + 2, soFar + literal(glob.charAt(at + 1)));
        }
        return characters(glob, at + 1, soFar + literal(c));
    }

    // c as only itself in a class of Java's: [, &, ^, - or ] are not.
    private static String literal(char c) {
        return Character.isLetterOrDigit(c) ? String.valueOf(c) : "\\" + c;
    }

    /**
     * A part of a glob read as {@code regex}, up to just before {@code end}.
     */
    private record Read(String regex, int end) {}
}
