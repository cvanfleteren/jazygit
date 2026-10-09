package net.vanfleteren.jazygit.ui;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Renders a single element to plain text, for asserting on what a panel shows.
 */
final class RenderedText {

    private static final Rect AREA = new Rect(0, 0, 40, 5);
    private static final Rect LARGE = new Rect(0, 0, 100, 30);

    private RenderedText() {
    }

    /**
     * Renders the element into a fresh buffer on the render thread, which TamboUI requires.
     */
    static String of(ToolkitTestRunner testRunner, Supplier<? extends Element> element) throws Exception {
        return of(testRunner, element, AREA);
    }

    /**
     * Like {@link #of(ToolkitTestRunner, Supplier)}, on a 100x30 screen, for popups.
     */
    static String ofLarge(ToolkitTestRunner testRunner, Supplier<? extends Element> element) throws Exception {
        return of(testRunner, element, LARGE);
    }

    private static String of(ToolkitTestRunner testRunner, Supplier<? extends Element> element, Rect area)
            throws Exception {
        CompletableFuture<String> screen = new CompletableFuture<>();
        testRunner.runner().runOnRenderThread(() -> {
            Buffer buffer = Buffer.empty(area);
            element.get().render(Frame.forTesting(buffer), area, RenderContext.empty());
            screen.complete(text(buffer, area));
        });
        return screen.get(5, TimeUnit.SECONDS);
    }

    private static String text(Buffer buffer, Rect area) {
        StringBuilder text = new StringBuilder();
        for (int y = area.y(); y < area.y() + area.height(); y++) {
            for (int x = area.x(); x < area.x() + area.width(); x++) {
                text.append(buffer.get(x, y).symbol());
            }
            text.append('\n');
        }
        return text.toString();
    }
}
