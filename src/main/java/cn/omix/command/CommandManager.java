package cn.omix.command;

import cn.omix.command.impl.AiCommand;
import cn.omix.command.impl.BindCommand;
import cn.omix.command.impl.ChatCommand;
import cn.omix.command.impl.ConfigCommand;
import cn.omix.command.impl.HelpCommand;
import cn.omix.command.impl.ModuleCommand;
import cn.omix.command.impl.ModulesCommand;
import cn.omix.command.impl.ToggleCommand;
import cn.omix.command.impl.UsernameCommand;
import cn.omix.command.impl.VClipCommand;
import cn.omix.command.impl.VisibilityCommand;
import cn.omix.event.base.annotation.EventTarget;
import cn.omix.event.impl.PacketEvent;
import cn.omix.util.IMinecraft;
import cn.omix.util.Util;
import lombok.Getter;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Getter
public final class CommandManager implements IMinecraft {
    private final List<Command> commands = new ArrayList<>();
    private final ModuleCommand moduleCommand = new ModuleCommand();

    public CommandManager() {
        instance.getEventManager().register(this);

        addCommands(
                new HelpCommand(),
                new AiCommand(),
                new ChatCommand(),
                new ToggleCommand(),
                new BindCommand(),
                new ConfigCommand(),
                new ModulesCommand(),
                new VisibilityCommand(false, ".show <module>", "show", "s", "unhide"),
                new VisibilityCommand(true, ".hide <module>", "hide", "h"),
                new UsernameCommand(),
                new VClipCommand()
        );
    }

    public void addCommands(Command... commandsArray) {
        this.commands.addAll(Arrays.asList(commandsArray));
    }

    public List<String> getAiToolCommandNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Command command : commands) {
            for (String alias : command.getAliases()) {
                if (!alias.equalsIgnoreCase("ai") && !alias.equalsIgnoreCase("chat")) {
                    names.add("." + alias);
                }
            }
        }
        for (String module : moduleCommand.getModuleCompletions()) {
            names.add("." + module);
        }
        return names.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public void executeClientCommand(String message) {
        if (message == null || !message.startsWith(".")) {
            throw new IllegalArgumentException("Client commands must start with '.'.");
        }

        String input = message.substring(".".length()).trim();
        if (input.isEmpty()) {
            Util.log("No command entered.");
            return;
        }

        String[] arguments = input.split("\\s+");
        String label = arguments[0];

        for (Command command : commands) {
            for (String alias : command.getAliases()) {
                if (alias.equalsIgnoreCase(label)) {
                    try {
                        command.execute(arguments);
                    } catch (Exception exception) {
                        Util.log("Execution error: " + exception.getMessage());
                    }
                    return;
                }
            }
        }
        try {
            if (moduleCommand.execute(arguments)) {
                return;
            }
        } catch (Exception exception) {
            Util.log("Execution error: " + exception.getMessage());
            return;
        }
        Util.log(String.format(Locale.ROOT, "'%s' is not a command.", label));
    }

    public List<String> getCompletions(String input) {
        List<String> suggestions = new ArrayList<>();
        String[] split = input.substring(".".length()).split(" ", -1);
        String label = split[0];

        if (split.length == 1) {
            for (Command command : commands) {
                for (String alias : command.getAliases()) {
                    if (alias.toLowerCase().startsWith(label.toLowerCase())) {
                        suggestions.add(alias);
                    }
                }
            }
            for (String module : moduleCommand.getModuleCompletions()) {
                if (module.toLowerCase().startsWith(label.toLowerCase())) {
                    suggestions.add(module);
                }
            }
        } else {
            for (Command command : commands) {
                for (String alias : command.getAliases()) {
                    if (alias.equalsIgnoreCase(label)) {
                        List<String> commandCompletions = command.getCompletions(split);
                        String currentArg = split[split.length - 1].toLowerCase();
                        if (commandCompletions != null) {
                            for (String s : commandCompletions) {
                                if (s.toLowerCase().startsWith(currentArg)) {
                                    suggestions.add(s);
                                }
                            }
                        }
                        return suggestions;
                    }
                }
            }

            List<String> moduleCompletions = moduleCommand.getCompletions(split);
            String currentArg = split[split.length - 1].toLowerCase();
            for (String completion : moduleCompletions) {
                if (completion.toLowerCase().startsWith(currentArg)) {
                    suggestions.add(completion);
                }
            }
        }
        return suggestions;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == PacketEvent.Type.Send && event.getPacket() instanceof ChatMessageC2SPacket packet) {
            String message = packet.chatMessage();

            if (message.startsWith(".")) {
                event.setCancelled(true);
                executeClientCommand(message);
            }
        }
    }
}
