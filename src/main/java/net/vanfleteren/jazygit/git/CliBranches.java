package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.regex.Pattern;

import static net.vanfleteren.jazygit.git.Refs.DEFAULT_REMOTE;
import static net.vanfleteren.jazygit.git.Refs.HEADS;
import static net.vanfleteren.jazygit.git.Refs.REMOTES;

/**
 * Reads the branches through {@code git for-each-ref}, in one call for all of them.
 */
public final class CliBranches {

    private static final String ORIGIN = Refs.remote(DEFAULT_REMOTE, "");
    private static final List<String> FIELDS = List.of("%(refname)", "%(objectname)", "%(committerdate:unix)",
            "%(HEAD)", "%(upstream:short)", "%(upstream:track)", "%(symref)", "%(upstream)");
    private static final String FORMAT = FIELDS.mkString(GitOutput.REF_FIELD_SEPARATOR);
    private static final Pattern AHEAD = Pattern.compile("ahead (\\d{1,9})");
    private static final Pattern BEHIND = Pattern.compile("behind (\\d{1,9})");
    // The checked out branch first, then the most recently committed to.
    private static final Comparator<Branch> ORDER = Comparator.comparing(Branch::current).reversed()
            .thenComparing(Comparator.comparing(Branch::tipTime).reversed())
            .thenComparing(Branch::name);

    private CliBranches() {
    }

    /**
     * The local branches: the checked out one first, then the most recently committed to.
     */
    public static Either<ProcessError, List<Branch>> list(Path workTree) {
        return GitProcess.read(workTree, GitCommand.reading("for-each-ref", "--format=" + FORMAT, HEADS, REMOTES),
                CliBranches::parse);
    }

    /**
     * The name of the branch {@code branch} follows on its remote, read from git's configuration.
     */
    public static Either<ProcessError, Option<String>> remoteBranch(Path workTree, String branch) {
        return GitProcess.read(workTree,
                GitCommand.reading("for-each-ref", "--format=%(upstream:remoteref)", Refs.head(branch)),
                raw -> Either.right(remoteBranch(raw)));
    }

    /**
     * Parses {@code %(upstream:remoteref)}, e.g. {@code refs/heads/main}; it is empty without an upstream.
     */
    static Option<String> remoteBranch(String raw) {
        String ref = raw.strip();
        return GitOutput.after(ref, HEADS).orElse(Option.of(ref)).filter(name -> !name.isEmpty());
    }

    /**
     * One line of {@code for-each-ref} output.
     *
     * @param upstream    the short name of the upstream, e.g. {@code origin/main}
     * @param track       how far the branch is from its upstream, e.g. {@code [ahead 1, behind 2]}; none when
     *                    they are equal, {@code [gone]} when the upstream does not exist
     * @param symref      what a symbolic ref points to, e.g. {@code refs/remotes/origin/main}
     * @param upstreamRef the remote-tracking ref of the upstream, e.g. {@code refs/remotes/origin/main}
     */
    private record Ref(String name, Sha tip, Instant time, boolean head, Option<String> upstream,
                       Option<String> track, Option<String> symref, Option<String> upstreamRef) {

        static Either<UnexpectedOutput, Ref> parse(String line) {
            return GitOutput.fields(line, FIELDS.size()).flatMap(f -> GitOutput.sha(f.get(1)).flatMap(tip ->
                    GitOutput.epochSeconds(f.get(2)).map(time -> new Ref(f.get(0), tip, time, f.get(3).equals("*"),
                            present(f.get(4)), present(f.get(5)), present(f.get(6)), present(f.get(7))))));
        }

        private static Option<String> present(String field) {
            return Option.of(field).filter(text -> !text.isEmpty());
        }

        /**
         * The name of the local branch, if this is one.
         */
        Option<String> localName() {
            return GitOutput.after(name, HEADS);
        }

        /**
         * The remote-tracking ref of the branch it is pushed to: its upstream, or the one of the same name
         * on {@code origin}.
         */
        String pushedTo(String branch) {
            return upstreamRef.getOrElse(() -> ORIGIN + branch);
        }
    }

    static Either<UnexpectedOutput, List<Branch>> parse(String raw) {
        return Either.sequenceRight(GitOutput.nonBlankLines(raw).map(Ref::parse)).map(refs -> branches(refs.toList()));
    }

    private static List<Branch> branches(List<Ref> refs) {
        Set<String> remote = refs.map(Ref::name).filter(name -> name.startsWith(REMOTES)).toSet();
        List<String> local = refs.flatMap(Ref::localName);
        Option<String> defaultBranch = defaultBranch(refs, local);
        return refs
                .flatMap(ref -> ref.localName().map(name -> new Branch(name, ref.head(), ref.tip(), ref.time(),
                        remote.contains(ref.pushedTo(name)),
                        defaultBranch.contains(name),
                        upstream(ref))))
                .sorted(ORDER);
    }

    /**
     * The main branch: the one {@code origin/HEAD} points to, as set by a clone. Without it, the
     * first of {@code main} and {@code master} that is among {@code branches}.
     */
    private static Option<String> defaultBranch(List<Ref> refs, List<String> branches) {
        return refs.find(ref -> ref.name().equals(ORIGIN + "HEAD"))
                .flatMap(Ref::symref)
                .flatMap(target -> GitOutput.after(target, ORIGIN))
                .orElse(() -> List.of("main", "master").find(branches::contains));
    }

    private static Option<Upstream> upstream(Ref ref) {
        String track = ref.track().getOrElse("");
        return ref.upstream()
                .filter(name -> !track.equals("[gone]"))
                .map(name -> new Upstream(name, count(AHEAD, track), count(BEHIND, track)));
    }

    private static int count(Pattern pattern, String track) {
        // At most 9 digits, so the count fits an int.
        return Option.ofOptional(pattern.matcher(track).results().findFirst())
                .map(match -> Integer.parseInt(match.group(1)))
                .getOrElse(0);
    }
}
