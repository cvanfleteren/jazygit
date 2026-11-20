package net.vanfleteren.jazygit.features.agent.settings;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Codec;
import net.vanfleteren.jazygit.kernel.settings.Decoded;
import net.vanfleteren.jazygit.kernel.settings.Programs;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;

/**
 * The agents the user set in the settings file, as its {@code [[agents]]} tables list them, in their order, which a
 * failed build is handed to (ADR 161). Only read: the app never writes them.
 *
 * @param all the agents, in the order of the file
 */
public record Agents(List<AgentProgram> all) {

    /**
     * No agent set.
     */
    public static final Agents NONE = new Agents(List.empty());

    /**
     * The key of the agents in the settings file: an array of tables.
     */
    public static final String KEY = "agents";

    /**
     * The agents as a setting, which this feature keeps (ADR 163). Only the user writes them, as the editors: an
     * entry that is wrong is left out and reported, the others still apply.
     */
    public static final SettingKey<Agents> SETTING = SettingKey.userOwned(KEY, NONE, Codec.readOnly(value -> {
        Read read = read(value);
        return new Decoded<>(Option.some(read.agents()), read.invalid());
    }));

    private static final String COMMAND = "command";
    private static final String PROMPT = "prompt";

    /**
     * Whether no agent is set.
     */
    public boolean isEmpty() {
        return all.isEmpty();
    }

    /**
     * Each agent with the letter that chooses it in the menu: its shortcut, or else the first free one, in the order
     * of the file.
     */
    public List<Tuple2<Character, AgentProgram>> lettered() {
        return Programs.lettered(all);
    }

    /**
     * The agents {@code value} holds, an array of tables as the settings file is read, with the keys of what is wrong
     * in it, such as {@code agents[2].command}, counted from 1. An entry without a name, without a command that is a
     * list of words, with a {@code terminal} that is not a boolean, or with a {@code prompt} that is not text is left
     * out; a shortcut that is not one character, is {@code /}, or is an earlier entry's is dropped. When {@code value}
     * is no array, there are none, and {@code agents} itself is wrong.
     */
    static Read read(Object value) {
        return Programs.tables(value).map(Agents::entries).getOrElse(() -> new Read(NONE, List.of(KEY)));
    }

    private static Read entries(List<?> tables) {
        return tables.zipWithIndex()
                .foldLeft(new Read(NONE, List.empty()), (read, table) -> read.and(entry(table._1, table._2 + 1, read)));
    }

    private static Read entry(Object value, int number, Read before) {
        String at = KEY + "[" + number + "].";
        if (!(value instanceof java.util.Map<?, ?> table)) {
            return new Read(NONE, List.of(KEY + "[" + number + "]"));
        }
        Option<String> name = Programs.text(table.get(Programs.NAME));
        Option<List<String>> command = Programs.command(table.get(COMMAND));
        Object terminal = table.get(Programs.TERMINAL);
        Object prompt = table.get(PROMPT);
        Option<String> validPrompt = Programs.text(prompt);
        List<String> wrong = Option.when(name.isEmpty(), at + Programs.NAME)
                .toList()
                .appendAll(Option.when(command.isEmpty(), at + COMMAND))
                .appendAll(Option.when(!Programs.validTerminal(terminal), at + Programs.TERMINAL))
                .appendAll(Option.when(prompt != null && validPrompt.isEmpty(), at + PROMPT));
        if (wrong.nonEmpty()) {
            return new Read(NONE, wrong);
        }
        Object shortcut = table.get(Programs.SHORTCUT);
        Set<Character> taken =
                before.agents().all().flatMap(AgentProgram::shortcut).toSet();
        Option<Character> validShortcut = Programs.shortcut(shortcut, taken);
        AgentProgram agent =
                new AgentProgram(name.get(), command.get(), Boolean.TRUE.equals(terminal), validShortcut, validPrompt);
        return new Read(
                new Agents(List.of(agent)),
                Option.when(shortcut != null && validShortcut.isEmpty(), at + Programs.SHORTCUT)
                        .toList());
    }

    /**
     * Agents as read, with the keys of what was wrong in them.
     */
    record Read(Agents agents, List<String> invalid) {

        Read and(Read next) {
            return new Read(new Agents(agents.all().appendAll(next.agents().all())), invalid.appendAll(next.invalid()));
        }
    }
}
