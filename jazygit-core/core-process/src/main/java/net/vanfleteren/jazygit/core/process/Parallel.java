package net.vanfleteren.jazygit.core.process;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Runs what does not depend on each other at the same time, each on a virtual thread of its own, and waits for all
 * of it before it returns: nothing it starts outlives the call (ADR 188). Until {@code StructuredTaskScope} is final,
 * this is where the app runs its own work in parallel.
 */
public final class Parallel {

    private Parallel() {}

    /**
     * What {@code first} and {@code second} give, run at the same time; none when the thread that waits for them is
     * interrupted, which it then stays. Each should say how it failed as a value: what one throws is thrown again here,
     * once both are done, as a bug.
     */
    public static <A, B> Option<Tuple2<A, B>> both(Supplier<A> first, Supplier<B> second) {
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<A> a = threads.submit(first::get);
            Future<B> b = threads.submit(second::get);
            return Option.of(Tuple.of(a.get(), b.get()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Option.none();
        } catch (ExecutionException e) {
            throw thrown(e.getCause());
        }
    }

    private static RuntimeException thrown(Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        return cause instanceof RuntimeException runtime ? runtime : new IllegalStateException(cause);
    }
}
