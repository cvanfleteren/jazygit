package net.vanfleteren.jazygit.kernel.state.diff;

import io.vavr.collection.List;
import java.util.ServiceLoader;
import net.vanfleteren.jazygit.core.git.model.DiffFilter;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * The diff filters that hide hunks of the diffs (ADR 144, 184), as the feature that keeps them reads them from a model:
 * the kernel hides what they match, without knowing where they are set or how they are chosen.
 *
 * @param enabled the filters enabled that can be used, whether or not they are switched off
 * @param off     whether they are all switched off for this session, so that they hide nothing but are counted
 */
public record HunkFilters(List<DiffFilter> enabled, boolean off) {

    /**
     * No filter.
     */
    public static final HunkFilters NONE = new HunkFilters(List.empty(), false);

    /**
     * The filters of {@code model}, as the {@link Provider} on the classpath reads them, which the app gives. Without
     * the app, as in the tests of the kernel, there are none.
     */
    public static HunkFilters of(Model model) {
        return Found.PROVIDER.filters(model);
    }

    /**
     * The filters that hide hunks now: those enabled, unless they are switched off.
     */
    public List<DiffFilter> active() {
        return off ? List.empty() : enabled;
    }

    /**
     * Reads the diff filters of a model, found as a service ({@code META-INF/services}), so the kernel hides hunks
     * without naming the feature that keeps the filters, as the content panel finds its sources (ADR 154).
     */
    @FunctionalInterface
    public interface Provider {

        /**
         * The diff filters of {@code model}.
         */
        HunkFilters filters(Model model);
    }

    private static final class Found {
        // Looked up once, when the filters are first asked for.
        private static final Provider PROVIDER =
                ServiceLoader.load(Provider.class).findFirst().orElseGet(() -> model -> NONE);
    }
}
