package net.vanfleteren.jazygit.core.process;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;
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
     * What {@code first} and {@code second} give, run at the same time, once both are done; it throws nothing. It
     * fails with what one of them threw, or with an {@link InterruptedException} when the thread that waits for them
     * is interrupted, which it then stays. An {@link Either}, not a {@code Try}: Vavr's {@code Try} throws an
     * {@link InterruptedException} again rather than hold it.
     */
    public static <A, B> Either<Throwable, Tuple2<A, B>> both(Supplier<A> first, Supplier<B> second) {
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<A> a = threads.submit(first::get);
            Future<B> b = threads.submit(second::get);
            try {
                return Either.right(Tuple.of(a.get(), b.get()));
            } catch (InterruptedException e) {
                // Interrupted again before the executor closes, which then stops both tasks rather than wait for them,
                // and leaves the thread interrupted.
                Thread.currentThread().interrupt();
                return Either.left(e);
            } catch (ExecutionException e) {
                return Either.left(e.getCause());
            }
        }
    }
}
