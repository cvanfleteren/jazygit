package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Blame;
import net.vanfleteren.jazygit.core.git.model.BlameAt;
import net.vanfleteren.jazygit.core.git.model.BlameOptions;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.FileVersion;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.diff.Version;
import net.vanfleteren.jazygit.kernel.state.diff.WholeFile;
import net.vanfleteren.jazygit.kernel.state.diff.WholeFile.Source;
import net.vanfleteren.jazygit.kernel.state.view.RepoView;
import net.vanfleteren.jazygit.lib.lens.Lens;

/**
 * Reads who last changed each line of {@code version} of the file the version of the area {@code area} shows, read as
 * {@code read} says (ADR 147): at the first of {@code at} that git can blame it at, with {@code options}. What it read
 * holds for nothing once the area shows another file, the file is read again, or whitespace is ignored otherwise than
 * {@code options} has it, and is dropped.
 */
public record ReadBlame(AreaId area, WholeFile read, FileVersion version, List<BlameAt> at, BlameOptions options)
        implements Targeted<Blame> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.blame", read.path()));
    }

    /**
     * The blame of the version, while the area shows it read so and blames it with whitespace as {@code options} has it.
     */
    @Override
    public Target<Blame> target() {
        return Target.inView(new Lens<RepoView, Loadable<Blame>>(
                view -> blaming(view).flatMap(blamed -> blamed.blames().read().get(version)),
                (view, change) -> blaming(view)
                        .filter(blamed -> blamed.blames().read().containsKey(version))
                        .map(blamed -> view.withVersion(
                                area,
                                blamed.withBlames(blamed.blames()
                                        .with(
                                                version,
                                                change.apply(blamed.blames()
                                                        .read()
                                                        .get(version)
                                                        .get())))))
                        .getOrElse(view)));
    }

    private Option<Version> blaming(RepoView view) {
        return view.versionOf(area)
                .filter(blamed ->
                        blamed.read().equals(read) && blamed.blames().ignoreWhitespace() == options.ignoreWhitespace());
    }

    @Override
    public Msg run(Env env) {
        return blame(env, at).fold(this::failed, blame -> new LoadMsg.BlameRead(this, blame));
    }

    /**
     * The blame at the first of {@code tried} git can blame the file at, or why the last could not.
     */
    private Either<Failure, Blame> blame(Env env, List<BlameAt> tried) {
        Either<Failure, Blame> first =
                env.git().history().blame(tried.head(), options).mapLeft(Failure::of);
        return first.isLeft() && tried.size() > 1 ? blame(env, tried.tail()) : first;
    }

    /**
     * Starts reading the blame of each version shown blamed that is not read yet, nor being read (ADR 147), keeping
     * that it is being read. The blames of a version read with whitespace ignored otherwise than the diff options have
     * it now are dropped first, of both versions: the one shown is read again at once, the other once it is shown. On a branch
     * without commits, a version of the working tree is not committed yet in any of its lines, and no blame is read.
     */
    public static Next blamed(Model model) {
        return model.repoView()
                .versions()
                .toList()
                .foldLeft(Next.of(model), (next, area) -> next.then(now -> blamed(now, area)));
    }

    private static Next blamed(Model model, Tuple2<AreaId, Version> area) {
        boolean ignore = model.preferences().ignoreWhitespace();
        Version version = area._2().withBlames(area._2().blames().readIgnoring(ignore));
        boolean unborn = model.repo()
                .status()
                .toOption()
                .exists(status -> status.headCommit().isEmpty());
        Model current =
                version.equals(area._2()) ? model : model.mapRepoView(view -> view.withVersion(area._1(), version));
        return version.unblamed()
                .map(shown -> {
                    Source source = version.read().source();
                    if (unborn && ofWorkingTree(source)) {
                        Blame none = Blame.uncommitted(
                                version.text().map(lines -> lines.size()).getOrElse(0), version.path());
                        return Next.of(kept(current, area._1(), version, shown, new Loadable.Loaded<>(none)));
                    }
                    ReadBlame read = new ReadBlame(
                            area._1(),
                            version.read(),
                            shown,
                            at(source, shown, version.path()),
                            new BlameOptions(ignore, version.lastLine()));
                    return Next.of(kept(current, area._1(), version, shown, Loadable.loading()), read);
                })
                .getOrElse(() -> Next.of(current));
    }

    private static Model kept(Model model, AreaId area, Version version, FileVersion shown, Loadable<Blame> blame) {
        return model.mapRepoView(view ->
                view.withVersion(area, version.withBlames(version.blames().with(shown, blame))));
    }

    private static boolean ofWorkingTree(Source source) {
        return source instanceof Source.WorkingTree || source instanceof Source.OnDisk;
    }

    /**
     * Where {@code version} of the file at {@code path}, read as {@code source} says, is blamed: at the revision its
     * lines are of, the original of a renamed file at its old path (ADR 147). A file a stash added is in the stash, or
     * among its untracked files in its third parent, which its list of files does not tell apart.
     */
    static List<BlameAt> at(Source source, FileVersion version, String path) {
        boolean original = version == FileVersion.ORIGINAL;
        return switch (source) {
            case Source.WorkingTree(FileEntry file, boolean staged) -> {
                String before = file.origin().getOrElse(path);
                yield List.of(
                        staged
                                ? original ? new BlameAt.AtHead(before) : new BlameAt.InIndex(path)
                                : original ? new BlameAt.InIndex(path) : new BlameAt.OnDisk(path));
            }
            case Source.OfCommit(Sha sha, CommitFile file) -> ofCommit(sha, file, original, path);
            case Source.OfStash(Sha sha, CommitFile file) -> ofStash(sha, file, original, path);
            case Source.OfFiles(Changes of, CommitFile file) ->
                switch (of) {
                    case Changes.OfCommit(Sha sha) -> ofCommit(sha, file, original, path);
                    case Changes.OfTip(var branch, Sha sha) -> ofCommit(sha, file, original, path);
                    case Changes.OfStash(Sha sha) -> ofStash(sha, file, original, path);
                    case Changes.OfBranch(var branch, var base, Sha sha) ->
                        ofRange(base.tip(), sha, file, original, path);
                    case Changes.OfPullRequest(PullRequestCommits commits, var _) ->
                        ofRange(commits.base(), commits.head(), file, original, path);
                };
            case Source.OnDisk(String onDisk) -> List.of(new BlameAt.OnDisk(onDisk));
            case Source.AtCommit(Sha sha, String atCommit) -> List.of(new BlameAt.InCommit(sha, atCommit));
        };
    }

    private static List<BlameAt> ofCommit(Sha sha, CommitFile file, boolean original, String path) {
        return List.of(
                original
                        ? new BlameAt.InCommit(sha, 1, file.origin().getOrElse(path))
                        : new BlameAt.InCommit(sha, path));
    }

    private static List<BlameAt> ofStash(Sha sha, CommitFile file, boolean original, String path) {
        if (original) {
            return List.of(new BlameAt.InCommit(sha, 1, file.origin().getOrElse(path)));
        }
        return file.type() == ChangeType.ADDED
                ? List.of(new BlameAt.InCommit(sha, path), new BlameAt.InCommit(sha, 3, path))
                : List.of(new BlameAt.InCommit(sha, path));
    }

    private static List<BlameAt> ofRange(Sha base, Sha tip, CommitFile file, boolean original, String path) {
        return List.of(
                original
                        ? new BlameAt.AtMergeBase(base, tip, file.origin().getOrElse(path))
                        : new BlameAt.InCommit(tip, path));
    }
}
