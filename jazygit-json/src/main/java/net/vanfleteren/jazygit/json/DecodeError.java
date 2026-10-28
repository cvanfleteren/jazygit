package net.vanfleteren.jazygit.json;

import io.vavr.collection.List;

/**
 * Why a {@link Json} could not be decoded, or text not parsed: where, and what was wrong there (ADR 42).
 *
 * @param path the fields and indexes from the top to where it failed, an index as {@code [0]}; empty at the top
 * @param message what was wrong there, like {@code expected a string, got null}
 */
public record DecodeError(List<String> path, String message) {

    /**
     * {@code message}, at the top.
     */
    public static DecodeError of(String message) {
        return new DecodeError(List.empty(), message);
    }

    /**
     * This error, inside the field {@code name}.
     */
    public DecodeError inField(String name) {
        return new DecodeError(path.prepend(name), message);
    }

    /**
     * This error, inside the element at {@code index}.
     */
    public DecodeError atIndex(int index) {
        return new DecodeError(path.prepend("[" + index + "]"), message);
    }

    /**
     * Where it failed and what was wrong there, like
     * {@code data.repository.pullRequests.nodes[0].number: expected a number, got null}; only what was wrong when it
     * failed at the top.
     */
    public String text() {
        return path.isEmpty() ? message : where() + ": " + message;
    }

    private String where() {
        return path.tail().map(step -> step.startsWith("[") ? step : "." + step).mkString(path.head(), "", "");
    }
}
