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

    private RenderedText() {
    }

    /**
     * Renders the element into a fresh buffer on the render thread, which TamboUI requires.
     */
    static String of(ToolkitTestRunner testRunner, Supplier<? extends Element> element) throws Exception {
        CompletableFuture<String> screen = new CompletableFuture<>();
        testRunner.runner().runOnRenderThread(() -> {
            Buffer buffer = Buffer.empty(AREA);
            element.get().render(Frame.forTesting(buffer), AREA, RenderContext.empty());
            screen.complete(text(buffer));
        });
        return screen.get(5, TimeUnit.SECONDS);
    }

    private static String text(Buffer buffer) {
        StringBuilder text = new StringBuilder();
        for (int y = AREA.y(); y < AREA.y() + AREA.height(); y++) {
            for (int x = AREA.x(); x < AREA.x() + AREA.width(); x++) {
                text.append(buffer.get(x, y).symbol());
            }
            text.append('\n');
        }
        return text.toString();
    }
}
