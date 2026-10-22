package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.ResizeEvent;
import dev.tamboui.tui.pilot.ElementNotFoundException;
import dev.tamboui.tui.pilot.Pilot;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A {@link Pilot} that returns from every action only once the render thread has handled it and drawn the
 * result, so a test can assert right after it.
 * <p>
 * TamboUI's own pilot sleeps 50ms instead, which is not enough when the machine is busy: the render thread may
 * still be drawing the first frame when the test asserts. The render thread takes events in order and draws
 * after each one, so once it runs a task queued behind them, it has handled them all.
 */
public final class SettledPilot implements Pilot {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    // The frame, JazygitApp#redrawIfFocusMoves's check behind it, and the Redraw that check queues. A longer chain
    // of runLater hops, like JazygitApp#report's, needs a frame count to settle on.
    private static final int SETTLE_ROUNDS = 3;
    // A count of frames that never changes: one pass of rounds settles.
    private static final IntSupplier UNCOUNTED = () -> 0;

    private final ToolkitTestRunner testRunner;
    private final Pilot pilot;
    private final IntSupplier framesDrawn;

    private SettledPilot(ToolkitTestRunner testRunner, IntSupplier framesDrawn) {
        this.testRunner = testRunner;
        this.pilot = testRunner.pilot();
        this.framesDrawn = framesDrawn;
    }

    public static Pilot of(ToolkitTestRunner testRunner) {
        return of(testRunner, UNCOUNTED);
    }

    /**
     * A pilot that settles as {@link #settle(ToolkitTestRunner, IntSupplier)} does, on {@code framesDrawn}.
     */
    public static Pilot of(ToolkitTestRunner testRunner, IntSupplier framesDrawn) {
        return new SettledPilot(testRunner, framesDrawn);
    }

    /**
     * Focuses {@code id} on the render thread, so the auto-focus of a frame being drawn cannot undo it, and draws a
     * frame with it, which moving the focus does not by itself.
     */
    public static void focus(ToolkitTestRunner testRunner, String id) {
        focus(testRunner, id, UNCOUNTED);
    }

    /**
     * As {@link #focus(ToolkitTestRunner, String)}, settling on {@code framesDrawn}.
     */
    public static void focus(ToolkitTestRunner testRunner, String id, IntSupplier framesDrawn) {
        testRunner
                .runner()
                .runOnRenderThread(() -> testRunner.runner().focusManager().setFocus(id));
        // TamboUI draws a frame on a resize, in its turn; a tick waits behind every other event, the next key too,
        // which the panes would then handle as drawn without the focus.
        Size size = terminalSize(testRunner);
        testRunner.runner().tuiRunner().dispatch(ResizeEvent.of(size.width(), size.height()));
        settle(testRunner, framesDrawn);
    }

    private static Size terminalSize(ToolkitTestRunner testRunner) {
        try {
            return testRunner.backend().size();
        } catch (IOException failed) {
            throw new AssertionError("the test backend has no size", failed);
        }
    }

    /**
     * Waits until the render thread has handled everything sent to it so far, and drawn the frames those ask for.
     * <p>
     * A frame can ask for the next one: the app checks behind it whether the focus moved, and if so queues a
     * redraw. Each round through the queue gets behind what the round before queued, so the frame, the check and
     * the redraw take three.
     */
    public static void settle(ToolkitTestRunner testRunner) {
        settle(testRunner, UNCOUNTED);
    }

    /**
     * Waits as {@link #settle(ToolkitTestRunner)} does, again and again until a pass draws no frame, as
     * {@code framesDrawn} counts them: what a frame reports reaches the model a round later, and the frame the model
     * then asks for comes after more rounds than one pass takes.
     */
    public static void settle(ToolkitTestRunner testRunner, IntSupplier framesDrawn) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        int before;
        do {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the render thread kept drawing frames for " + TIMEOUT);
            }
            before = framesDrawn.getAsInt();
            settleOnce(testRunner);
        } while (framesDrawn.getAsInt() != before);
    }

    private static void settleOnce(ToolkitTestRunner testRunner) {
        CountDownLatch handled = new CountDownLatch(1);
        queueRounds(testRunner, SETTLE_ROUNDS, handled::countDown);
        try {
            if (!handled.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("the render thread did not catch up within " + TIMEOUT);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the render thread", interrupted);
        }
    }

    /**
     * Runs {@code done} on the render thread after going through its queue {@code rounds} times.
     */
    private static void queueRounds(ToolkitTestRunner testRunner, int rounds, Runnable done) {
        testRunner.runner().tuiRunner().runLater(() -> {
            if (rounds == 1) {
                done.run();
            } else {
                queueRounds(testRunner, rounds - 1, done);
            }
        });
    }

    @Override
    public void press(KeyCode code) {
        press(code, KeyModifiers.NONE);
    }

    @Override
    public void press(KeyCode code, KeyModifiers modifiers) {
        dispatch(KeyEvent.ofKey(code, modifiers));
    }

    @Override
    public void press(char c) {
        press(c, KeyModifiers.NONE);
    }

    @Override
    public void press(char c, KeyModifiers modifiers) {
        dispatch(KeyEvent.ofChar(c, modifiers));
    }

    @Override
    public void press(String... keys) {
        pilot.press(keys);
        settle(testRunner, framesDrawn);
    }

    @Override
    public void mousePress(MouseButton button, int x, int y) {
        pilot.mousePress(button, x, y);
        settle(testRunner, framesDrawn);
    }

    @Override
    public void mouseRelease(MouseButton button, int x, int y) {
        pilot.mouseRelease(button, x, y);
        settle(testRunner, framesDrawn);
    }

    @Override
    public void click(int x, int y) {
        pilot.click(x, y);
        settle(testRunner, framesDrawn);
    }

    @Override
    public void click(String elementId) throws ElementNotFoundException {
        pilot.click(elementId);
        settle(testRunner, framesDrawn);
    }

    @Override
    public void click(String elementId, int x, int y) throws ElementNotFoundException {
        pilot.click(elementId, x, y);
        settle(testRunner, framesDrawn);
    }

    /**
     * Where {@code elementId} was drawn, once the render thread has drawn everything sent to it.
     * <p>
     * Looked up on the render thread: it clears the element registry and fills it again on every frame, ticks
     * included, so read from the test's thread it can be empty halfway through one.
     */
    @Override
    public Rect findElement(String elementId) throws ElementNotFoundException {
        settle(testRunner, framesDrawn);
        Rect area = onRenderThread(() -> testRunner.runner().elementRegistry().getArea(elementId));
        if (area == null) {
            throw new ElementNotFoundException("Element not found: " + elementId);
        }
        return area;
    }

    @Override
    public boolean hasElement(String elementId) {
        settle(testRunner, framesDrawn);
        return onRenderThread(() -> testRunner.runner().elementRegistry().getArea(elementId) != null);
    }

    @Override
    public void mouseMove(int x, int y) {
        pilot.mouseMove(x, y);
        settle(testRunner, framesDrawn);
    }

    @Override
    public void resize(int width, int height) {
        pilot.resize(width, height);
        settle(testRunner, framesDrawn);
    }

    @Override
    public void resize(Size size) {
        pilot.resize(size);
        settle(testRunner, framesDrawn);
    }

    @Override
    public void pause() {
        settle(testRunner, framesDrawn);
    }

    @Override
    public void pause(Duration delay) {
        pilot.pause(delay);
        settle(testRunner, framesDrawn);
    }

    @Override
    public void quit() {
        pilot.quit();
    }

    private <T> T onRenderThread(Supplier<T> read) {
        CompletableFuture<T> value = new CompletableFuture<>();
        testRunner.runner().runOnRenderThread(() -> value.complete(read.get()));
        try {
            return value.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the render thread", interrupted);
        } catch (ExecutionException | TimeoutException failed) {
            throw new AssertionError("the render thread did not answer within " + TIMEOUT, failed);
        }
    }

    private void dispatch(Event event) {
        testRunner.runner().tuiRunner().dispatch(event);
        settle(testRunner, framesDrawn);
    }
}
