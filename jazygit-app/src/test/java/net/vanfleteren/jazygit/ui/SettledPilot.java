package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.pilot.ElementNotFoundException;
import dev.tamboui.tui.pilot.Pilot;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    private final ToolkitTestRunner testRunner;
    private final Pilot pilot;

    private SettledPilot(ToolkitTestRunner testRunner) {
        this.testRunner = testRunner;
        this.pilot = testRunner.pilot();
    }

    public static Pilot of(ToolkitTestRunner testRunner) {
        return new SettledPilot(testRunner);
    }

    /**
     * Focuses {@code id} on the render thread, so the auto-focus of a frame being drawn cannot undo it.
     */
    public static void focus(ToolkitTestRunner testRunner, String id) {
        testRunner
                .runner()
                .runOnRenderThread(() -> testRunner.runner().focusManager().setFocus(id));
        settle(testRunner);
    }

    /**
     * Waits until the render thread has handled everything sent to it so far.
     */
    public static void settle(ToolkitTestRunner testRunner) {
        CountDownLatch handled = new CountDownLatch(1);
        testRunner.runner().tuiRunner().runLater(handled::countDown);
        try {
            if (!handled.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("the render thread did not catch up within " + TIMEOUT);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the render thread", interrupted);
        }
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
        settle(testRunner);
    }

    @Override
    public void mousePress(MouseButton button, int x, int y) {
        pilot.mousePress(button, x, y);
        settle(testRunner);
    }

    @Override
    public void mouseRelease(MouseButton button, int x, int y) {
        pilot.mouseRelease(button, x, y);
        settle(testRunner);
    }

    @Override
    public void click(int x, int y) {
        pilot.click(x, y);
        settle(testRunner);
    }

    @Override
    public void click(String elementId) throws ElementNotFoundException {
        pilot.click(elementId);
        settle(testRunner);
    }

    @Override
    public void click(String elementId, int x, int y) throws ElementNotFoundException {
        pilot.click(elementId, x, y);
        settle(testRunner);
    }

    /**
     * Where {@code elementId} was drawn, once the render thread has drawn everything sent to it.
     * <p>
     * Looked up on the render thread: it clears the element registry and fills it again on every frame, ticks
     * included, so read from the test's thread it can be empty halfway through one.
     */
    @Override
    public Rect findElement(String elementId) throws ElementNotFoundException {
        settle(testRunner);
        Rect area = onRenderThread(() -> testRunner.runner().elementRegistry().getArea(elementId));
        if (area == null) {
            throw new ElementNotFoundException("Element not found: " + elementId);
        }
        return area;
    }

    @Override
    public boolean hasElement(String elementId) {
        settle(testRunner);
        return onRenderThread(() -> testRunner.runner().elementRegistry().getArea(elementId) != null);
    }

    @Override
    public void mouseMove(int x, int y) {
        pilot.mouseMove(x, y);
        settle(testRunner);
    }

    @Override
    public void resize(int width, int height) {
        pilot.resize(width, height);
        settle(testRunner);
    }

    @Override
    public void resize(Size size) {
        pilot.resize(size);
        settle(testRunner);
    }

    @Override
    public void pause() {
        settle(testRunner);
    }

    @Override
    public void pause(Duration delay) {
        pilot.pause(delay);
        settle(testRunner);
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
        settle(testRunner);
    }
}
