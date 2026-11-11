package net.vanfleteren.jazygit.state.load;

import io.vavr.control.Option;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Lens;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Repo;
import net.vanfleteren.jazygit.state.view.RepoView;

/**
 * Where a {@link Targeted} load keeps what it reads in the model: the {@link Loadable} it fills, while the model
 * still wants what the load reads. What was read for another item than the one now shown, or for a view closed since,
 * finds no target, and is dropped.
 *
 * @param lens      the loadable, if the model still wants what the load reads
 * @param keepsRead whether a value that was read stays when reading it again fails, as it does for what the
 *                  repository holds: reading again can fail for a while, say on a timeout. What a view shows is
 *                  replaced by the failure.
 * @param <T>       what the load reads
 */
public record Target<T>(Lens<Model, Loadable<T>> lens, boolean keepsRead) {

    private static final Lens<Model, Repo> REPO = Lens.of(Model::repo, Model::withRepo);

    private static final Lens<Model, RepoView> REPO_VIEW = Lens.of(Model::repoView, Model::withRepoView);

    /**
     * A loadable of what git reports about the repository, which {@code get} reads and {@code set} sets.
     */
    public static <T> Target<T> inRepo(Function<Repo, Loadable<T>> get, BiFunction<Repo, Loadable<T>, Repo> set) {
        return new Target<>(REPO.andThen(Lens.of(get, set)), true);
    }

    /**
     * A loadable of what the repository screen shows, where {@code lens} finds it.
     */
    public static <T> Target<T> inView(Lens<RepoView, Loadable<T>> lens) {
        return new Target<>(REPO_VIEW.andThen(lens), false);
    }

    /**
     * What is kept here in {@code model}; none when the model does not want what the load reads.
     */
    public Option<Loadable<T>> in(Model model) {
        return lens.get(model);
    }

    /**
     * Whether {@code model} still wants what the load reads.
     */
    public boolean wanted(Model model) {
        return in(model).isDefined();
    }

    /**
     * Whether reading failed the last time, and no value is held.
     */
    public boolean failedIn(Model model) {
        return in(model).exists(Loadable::isFailed);
    }

    /**
     * {@code model} with {@code value} kept here, as {@link Loadable#reload} keeps it.
     */
    public Model reload(Model model, T value) {
        return lens.map(model, loadable -> loadable.reload(value));
    }

    /**
     * {@code model} with reading having failed for {@code failure}.
     */
    public Model failed(Model model, Failure failure) {
        return lens.map(model, loadable -> keepsRead ? loadable.failed(failure) : new Loadable.Failed<>(failure));
    }
}
