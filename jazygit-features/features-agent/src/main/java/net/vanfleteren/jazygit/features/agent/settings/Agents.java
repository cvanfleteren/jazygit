package net.vanfleteren.jazygit.features.agent.settings;

import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Codec;
import net.vanfleteren.jazygit.kernel.settings.Decoded;
import net.vanfleteren.jazygit.kernel.settings.Entries;
import net.vanfleteren.jazygit.kernel.settings.Programs;
import net.vanfleteren.jazygit.kernel.settings.SettingKey;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.TableCodec;

/**
 * The agents the user set, as the {@code [[agents]]} tables of the settings file list them, in their order, which a
 * failed build is handed to (ADR 161). The settings panel edits them (ADR 164), and so does the user, in the file.
 *
 * @param all the agents, in the order of the file, each shortcut once
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

    static final String COMMAND = "command";
    static final String PROMPT = "prompt";

    /**
     * How an agent is read from its table of {@code [[agents]]} and kept in one. A table without a name, without a
     * command that is a list of words, with a {@code terminal} that is not a boolean, or with a {@code prompt} that is
     * not text holds no agent, and is kept as written; a shortcut that is not one character, or is {@code /}, is
     * left out of the agent, and kept in its table.
     */
    public static final TableCodec<AgentProgram> TABLE = new TableCodec<>() {
        @Override
        public List<String> fields() {
            return List.of(Programs.NAME, COMMAND, Programs.TERMINAL, Programs.SHORTCUT, PROMPT);
        }

        @Override
        public Decoded<AgentProgram> decode(String at, LinkedHashMap<String, Object> table) {
            Option<String> name = Programs.text(field(table, Programs.NAME));
            Option<List<String>> command = Programs.command(field(table, COMMAND));
            Object terminal = field(table, Programs.TERMINAL);
            Object prompt = field(table, PROMPT);
            Option<String> validPrompt = Programs.text(prompt);
            List<String> wrong = Option.when(name.isEmpty(), at + "." + Programs.NAME)
                    .toList()
                    .appendAll(Option.when(command.isEmpty(), at + "." + COMMAND))
                    .appendAll(Option.when(!Programs.validTerminal(terminal), at + "." + Programs.TERMINAL))
                    .appendAll(Option.when(prompt != null && validPrompt.isEmpty(), at + "." + PROMPT));
            if (wrong.nonEmpty()) {
                return new Decoded<>(Option.none(), wrong);
            }
            Object shortcut = field(table, Programs.SHORTCUT);
            Option<Character> validShortcut = Programs.shortcut(shortcut, HashSet.empty());
            return new Decoded<>(
                    Option.some(new AgentProgram(
                            name.get(), command.get(), Boolean.TRUE.equals(terminal), validShortcut, validPrompt)),
                    Option.when(shortcut != null && validShortcut.isEmpty(), at + "." + Programs.SHORTCUT)
                            .toList());
        }

        @Override
        public LinkedHashMap<String, Object> encode(AgentProgram agent) {
            return LinkedHashMap.<String, Object>of(
                            Programs.NAME,
                            agent.name(),
                            COMMAND,
                            agent.command().toJavaList())
                    .merge(agent.terminal() ? LinkedHashMap.of(Programs.TERMINAL, true) : LinkedHashMap.empty())
                    .merge(agent.shortcut()
                            .map(letter -> LinkedHashMap.<String, Object>of(Programs.SHORTCUT, String.valueOf(letter)))
                            .getOrElse(LinkedHashMap.empty()))
                    .merge(agent.prompt()
                            .map(text -> LinkedHashMap.<String, Object>of(PROMPT, text))
                            .getOrElse(LinkedHashMap.empty()));
        }
    };

    /**
     * The agents as a setting, which this feature keeps (ADR 163) and the settings panel edits (ADR 164). An entry
     * that is wrong is reported and kept as written, the others still apply; so is a shortcut an earlier agent has.
     */
    public static final SettingKey<Entries<AgentProgram>> SETTING =
            SettingKey.of(KEY, Entries.none(), withTakenShortcuts(Codec.tables(TABLE)));

    /**
     * The agents {@code settings} set: see {@link #of(Entries)}.
     */
    public static Agents in(Settings settings) {
        return of(settings.get(SETTING));
    }

    /**
     * The agents of {@code entries} the app could read, in their order, without the shortcut of an agent that an
     * earlier one has.
     */
    public static Agents of(Entries<AgentProgram> entries) {
        return new Agents(entries.values()
                .foldLeft(
                        List.empty(),
                        (agents, agent) -> agents.append(
                                agents.exists(earlier -> earlier.shortcut().isDefined()
                                                && earlier.shortcut().equals(agent.shortcut()))
                                        ? agent.withShortcut(Option.none())
                                        : agent)));
    }

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
     * {@code tables}, which also reports the shortcut of an agent that an earlier agent has, at its key, such as
     * {@code agents[2].shortcut}.
     */
    private static Codec<Entries<AgentProgram>> withTakenShortcuts(Codec<Entries<AgentProgram>> tables) {
        return new Codec<>() {
            @Override
            public Decoded<Entries<AgentProgram>> decode(String key, Object value) {
                Decoded<Entries<AgentProgram>> read = tables.decode(key, value);
                return new Decoded<>(
                        read.value(),
                        read.invalid().appendAll(read.value().toList().flatMap(entries -> taken(key, entries))));
            }

            @Override
            public Map<List<String>, Object> encode(String key, Entries<AgentProgram> value) {
                return tables.encode(key, value);
            }

            @Override
            public boolean guarded() {
                return tables.guarded();
            }
        };
    }

    /**
     * The keys of the shortcuts of {@code entries} that an earlier entry has.
     */
    private static List<String> taken(String key, Entries<AgentProgram> entries) {
        return entries.all()
                .zipWithIndex()
                .foldLeft(
                        new Taken(HashSet.empty(), List.empty()),
                        (taken, entry) -> entry._1
                                .value()
                                .flatMap(AgentProgram::shortcut)
                                .map(letter -> taken.letters().contains(letter)
                                        ? new Taken(
                                                taken.letters(),
                                                taken.keys()
                                                        .append(key + "[" + (entry._2 + 1) + "]." + Programs.SHORTCUT))
                                        : new Taken(taken.letters().add(letter), taken.keys()))
                                .getOrElse(taken))
                .keys();
    }

    private static Object field(LinkedHashMap<String, Object> table, String name) {
        return table.get(name).getOrNull();
    }

    private record Taken(Set<Character> letters, List<String> keys) {}
}
