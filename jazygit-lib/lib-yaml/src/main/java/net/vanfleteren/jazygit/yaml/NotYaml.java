package net.vanfleteren.jazygit.yaml;

/**
 * Why text could not be read as {@link YamlTree} documents (ADR 123), told as data, for whoever shows it to write in
 * words.
 */
public sealed interface NotYaml {

    /**
     * The text holds no document: nothing, or nothing but comments.
     */
    record Nothing() implements NotYaml {}

    /**
     * Reading stopped at a place that is not YAML.
     *
     * @param line    the line SnakeYAML stopped on, counted from 1; often after the one at fault
     * @param column  the column it stopped at, counted from 1
     * @param problem what it missed or found there, in its words, in English
     */
    record Broken(int line, int column, String problem) implements NotYaml {}

    /**
     * Its nodes are nested deeper than they are read.
     *
     * @param depth how deep they are read
     */
    record TooDeep(int depth) implements NotYaml {}
}
