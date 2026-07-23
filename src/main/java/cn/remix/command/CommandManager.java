package cn.remix.command;

import cn.remix.command.impl.AiCommand;
import cn.remix.command.impl.BindCommand;
import cn.remix.command.impl.ChatCommand;
import cn.remix.command.impl.ConfigCommand;
import cn.remix.command.impl.HelpCommand;
import cn.remix.command.impl.ModuleCommand;
import cn.remix.command.impl.ModulesCommand;
import cn.remix.command.impl.ToggleCommand;
import cn.remix.command.impl.UsernameCommand;
import cn.remix.command.impl.VClipCommand;
import cn.remix.command.impl.VisibilityCommand;
import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.PacketEvent;
import cn.remix.util.IMinecraft;
import cn.remix.util.Util;
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
