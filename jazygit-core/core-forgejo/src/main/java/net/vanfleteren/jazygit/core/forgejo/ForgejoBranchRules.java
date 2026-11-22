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
     * {@code -} is then. None when it is no glob: a class or a brace left open, a range not closed right after it, or
     * a {@code \} that ends it.
     */
    static Option<Pattern> glob(String pattern) {
        return regex(pattern)
                .flatMap(regex ->
                        Try.of(() -> Pattern.compile(regex, Pattern.DOTALL)).toOption());
    }

    private static Option<String> regex(String glob) {
        StringBuilder regex = new StringBuilder();
        int braces = 0;
        int at = 0;
        while (at < glob.length()) {
            char c = glob.charAt(at);
            if (c == '\\') {
                if (at + 1 == glob.length()) {
                    return Option.none();
                }
                regex.append(Pattern.quote(String.valueOf(glob.charAt(at + 1))));
                at += 2;
            } else if (c == '[') {
                Option<Read> read = characterClass(glob, at + 1);
                if (read.isEmpty()) {
                    return Option.none();
                }
                regex.append(read.get().regex());
                at = read.get().end();
            } else {
                if (c == '{') {
                    braces++;
                    regex.append("(?:");
                } else if (c == '}' && braces > 0) {
                    braces--;
                    regex.append(')');
                } else if (c == ',' && braces > 0) {
                    regex.append('|');
                } else if (c == '*') {
                    regex.append(".*");
                } else if (c == '?') {
                    regex.append('.');
                } else {
                    regex.append(Pattern.quote(String.valueOf(c)));
                }
                at++;
            }
        }
        return braces > 0 ? Option.none() : Option.of(regex.toString());
    }

    /**
     * The class of characters of {@code glob} from {@code from}, just after its {@code [}, as gobwas' lexer reads it
     * ({@code fetchRange}): after a {@code !}, a range when its second character is {@code -}, which {@code ]} must
     * close right after; else characters up to {@code ]}, each itself, a {@code \} taking the next as it is.
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
        StringBuilder characters = new StringBuilder();
        while (at < glob.length()) {
            char c = glob.charAt(at);
            if (c == ']') {
                return Option.of(new Read(set(not, open, characters.toString()), at + 1));
            }
            if (c == '\\') {
                if (at + 1 == glob.length()) {
                    return Option.none();
                }
                at++;
            }
            characters.append(literal(glob.charAt(at)));
            at++;
        }
        return Option.none();
    }

    // No character matches an empty class, and any does when it is negated, as Java does not take [].
    private static String set(boolean not, String open, String characters) {
        if (!characters.isEmpty()) {
            return open + characters + "]";
        }
        return not ? "." : "(?!)";
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
