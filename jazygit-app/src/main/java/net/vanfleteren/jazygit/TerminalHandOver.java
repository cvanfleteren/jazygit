package net.vanfleteren.jazygit;

import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.TuiConfig;
import io.vavr.collection.Traversable;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.image.Sharp;
import net.vanfleteren.jazygit.state.Terminal;
import net.vanfleteren.jazygit.ui.ImagesSent;
import net.vanfleteren.jazygit.ui.KittyGraphics;

/**
 * The terminal the app is shown on, which it hands over to a program the user works with, like an editor, through a
 * {@link HandOverBackend}.
 */
final class TerminalHandOver implements Terminal {

    private final Supplier<ToolkitRunner> runner;
    private final Runnable requestRedraw;
    private final Consumer<Boolean> focus;
    // How the terminal is set up, and the backend that hands it over, once the app is configured.
    private TuiConfig config = TuiConfig.defaults();
    private Option<HandOverBackend> backend = Option.none();
    // The title of the terminal's window, once the app gave it one; only touched on the render thread.
    private Option<String> title = Option.none();
    // The images sent to the terminal, with the Kitty graphics protocol (ADR 86); only touched on the render thread.
    private final KittyGraphics graphics = new KittyGraphics();

    /**
     * @param runner        the runner of the app, once it is started
     * @param requestRedraw asks for a frame, on the render thread
     * @param focus         told on the render thread when the terminal gets the focus (true) or loses it (false), as
     *                      a terminal that reports it says
     */
    TerminalHandOver(Supplier<ToolkitRunner> runner, Runnable requestRedraw, Consumer<Boolean> focus) {
        this.runner = runner;
        this.requestRedraw = requestRedraw;
        this.focus = focus;
    }

    /**
     * How the app sets the terminal up: with the mouse, without ticks, and on a backend that can hand it over.
     */
    TuiConfig configure() {
        // Without a backend of its own, the app still runs, but cannot hand the terminal over, nor tell the focus.
        backend = Try.of(() ->
                        HandOverBackend.system(focused -> runner.get().runOnRenderThread(() -> focus.accept(focused))))
                .onFailure(BugLog::log)
                .toOption();
        // Frames are drawn when something changed, not on ticks.
        TuiConfig.Builder builder = TuiConfig.builder().mouseCapture(true).noTick();
        config = backend.map(builder::backend).getOrElse(builder).build();
        return config;
    }

    /**
     * Runs {@code program} with the terminal handed over to it; called on the thread of a command. The app keeps
     * running meanwhile, but draws nothing and reads no keys, and is drawn anew afterwards.
     */
    @Override
    public <T> T handOver(Supplier<T> program) {
        return backend.fold(program, handing -> {
            onRenderThread(() -> {
                // The program gets a terminal without the images of the app, which are sent again once it is back.
                deleteImages();
                Try.run(() -> handing.leave(config)).get();
            });
            try {
                return program.get();
            } finally {
                onRenderThread(() -> {
                    Try.run(() -> handing.comeBack(config)).get();
                    // What frames drawn meanwhile sent was dropped.
                    graphics.forget();
                    // The program may have titled the window too.
                    title.forEach(this::showTitle);
                    runner.get().tuiRunner().terminal().clear();
                    requestRedraw.run();
                });
            }
        });
    }

    /**
     * {@code screen}, with the images of {@code shown} it has cells of sent to the terminal first, when it draws them
     * itself (ADR 86).
     */
    Element withImages(Element screen, Traversable<Sharp> shown) {
        return KittyGraphics.ENABLED ? new ImagesSent(screen, shown, graphics, this::writeRaw) : screen;
    }

    /**
     * Has the terminal delete the images the app sent it, to send them again when they are drawn; called on the render
     * thread, or once the app stopped.
     */
    void deleteImages() {
        if (KittyGraphics.ENABLED) {
            writeRaw(KittyGraphics.DELETE_ALL);
            backend.forEach(handing -> Try.run(handing::flush).onFailure(BugLog::log));
            graphics.forget();
        }
    }

    /**
     * Writes {@code sequence} to the terminal as it is; one that cannot be written to is left as it is.
     */
    private void writeRaw(String sequence) {
        backend.forEach(handing -> Try.run(() -> handing.writeRaw(sequence)).onFailure(BugLog::log));
    }

    /**
     * Titles the terminal's window {@code text}, also after the terminal was handed over; called on the render
     * thread. TamboUI gives the window back the title it had before once the app stops.
     */
    void title(String text) {
        title = Option.of(text);
        showTitle(text);
    }

    /**
     * Titles the window {@code text}; a terminal that cannot be written to only keeps the title it has.
     */
    private void showTitle(String text) {
        Try.run(() -> runner.get().tuiRunner().setWindowTitle(text)).onFailure(BugLog::log);
    }

    /**
     * Runs {@code action} on the render thread, between two frames, and waits for it.
     */
    private void onRenderThread(Runnable action) {
        CompletableFuture.runAsync(action, runner.get()::runOnRenderThread).join();
    }
}
