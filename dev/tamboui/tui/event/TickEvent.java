/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.tui.event;

import dev.tamboui.tui.RenderClock;
import dev.tamboui.tui.TuiConfig;

/**
 * Represents an animation timer tick event.
 * <p>
 * Tick events are generated at regular intervals when animation mode is enabled
 * via {@link TuiConfig#tickRate()}. A tick is a pulse that asks for a redraw; it
 * carries no time. To advance an animation by real time, measure it at the render
 * with a {@link RenderClock}; to step once per tick, use {@link #frameCount()}.
 * <p>
 * At most one tick waits in the event queue at a time: when events are handled
 * more slowly than the tick rate, the extra periods are dropped rather than piling up.
 */
public final class TickEvent implements Event {

    private final long frameCount;

    /**
     * Creates a tick event with the given frame count.
     *
     * @param frameCount the total number of frames elapsed
     */
    public TickEvent(long frameCount) {
        this.frameCount = frameCount;
    }

    /**
     * Creates a tick event with the given frame count.
     *
     * @param frameCount the total number of frames elapsed
     * @return a new tick event
     */
    public static TickEvent of(long frameCount) {
        return new TickEvent(frameCount);
    }

    /**
     * Returns the total number of frames elapsed.
     *
     * @return the frame count
     */
    public long frameCount() {
        return frameCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TickEvent)) {
            return false;
        }
        TickEvent tickEvent = (TickEvent) o;
        return frameCount == tickEvent.frameCount;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(frameCount);
    }

    @Override
    public String toString() {
        return String.format("TickEvent[frameCount=%d]", frameCount);
    }
}
