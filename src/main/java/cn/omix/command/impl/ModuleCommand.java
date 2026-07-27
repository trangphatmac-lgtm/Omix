package cn.omix.command.impl;

import cn.omix.Client;
import cn.omix.module.Module;
import cn.omix.module.value.Value;
import cn.omix.module.value.impl.BoolValue;
import cn.omix.module.value.impl.ModeValue;
import cn.omix.module.value.impl.MultiBoolValue;
import cn.omix.module.value.impl.NumberValue;
import cn.omix.util.Util;
import cn.omix.util.misc.KeyUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ModuleCommand {

    public boolean execute(String[] arguments) {
        Module module = findModule(arguments[0]);
        if (module == null) {
            return false;
        }

        if (arguments.length == 1) {
            listSettings(module);
            return true;
        }

        Value setting = findSetting(module, arguments[1]);
        if (setting == null) {
            Util.logToChat("&cSetting not found for " + module.getName() + ".");
            return true;
        }

        switch (setting) {
            case BoolValue bool -> setBool(module, bool, arguments);
            case ModeValue mode -> setMode(module, mode, arguments);
            case NumberValue number -> setNumber(module, number, arguments);
            default -> Util.logToChat("&cThis setting type cannot be changed from chat.");
        }
        return true;
    }

    public List<String> getModuleCompletions() {
        return Client.instance.getModuleManager().getModuleMap().values().stream()
                .map(module -> commandName(module.getName()))
                .toList();
    }

    public List<String> getCompletions(String[] arguments) {
        Module module = findModule(arguments[0]);
        if (module == null) {
            return List.of();
        }

        if (arguments.length == 2) {
            return getSettings(module, true).stream()
                    .map(value -> commandName(value.getName()))
                    .toList();
        }

        if (arguments.length == 3) {
            Value setting = findSetting(module, arguments[1]);
            return switch (setting) {
                case BoolValue ignored -> List.of("true", "false");
                case ModeValue mode -> Arrays.stream(mode.getModes()).map(ModuleCommand::commandName).toList();
                case NumberValue number -> List.of(formatNumber(number.getMin()), formatNumber(number.getMax()));
                case null, default -> List.of();
            };
        }

        return List.of();
    }

    private void listSettings(Module module) {
        String key = module.getKey() > 0
                ? "&7[&f" + KeyUtil.getKeyName(module.getKey()) + "&7] "
                : "";
        String state = module.isEnabled() ? "&aON" : "&cOFF";
        Util.logToChat(key + "&f" + module.getName() + " &7(" + state + "&7):");

        List<Value> settings = getSettings(module, true);
        if (settings.isEmpty()) {
            Util.logRaw("&8» &7No configurable NumberValue, BoolValue, or ModeValue settings.");
            return;
        }

        for (Value setting : settings) {
            String value = switch (setting) {
                case BoolValue bool -> bool.getValue() ? "&atrue" : "&cfalse";
                case ModeValue mode -> "&9" + mode.getValue();
                case NumberValue number -> "&b" + formatNumber(number.getValue());
                default -> "";
            };
            Util.logRaw("&8» &7" + commandName(setting.getName()).toLowerCase(Locale.ROOT) + ": " + value);
        }
    }

    private void setBool(Module module, BoolValue setting, String[] arguments) {
        if (arguments.length == 2) {
            setting.toggle();
        } else {
            String requested = joinValue(arguments);
            switch (normalize(requested)) {
                case "true", "on", "1" -> setting.setValue(true);
                case "false", "off", "0" -> setting.setValue(false);
                case "toggle" -> setting.toggle();
                default -> {
                    Util.logToChat("&cExpected true, false, on, off, or toggle.");
                    return;
                }
            }
        }

        Util.logToChat(module.getName() + ": " + setting.getName() + " has been set to "
                + (setting.getValue() ? "&atrue" : "&cfalse") + ".");
    }

    private void setMode(Module module, ModeValue setting, String[] arguments) {
        String selected;
        if (arguments.length == 2) {
            int current = 0;
            for (int i = 0; i < setting.getModes().length; i++) {
                if (setting.getModes()[i].equalsIgnoreCase(setting.getValue())) {
                    current = i;
                    break;
                }
            }
            selected = setting.getModes()[(current + 1) % setting.getModes().length];
        } else {
            String requested = joinValue(arguments);
            selected = Arrays.stream(setting.getModes())
                    .filter(mode -> normalize(mode).equals(normalize(requested)))
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                Util.logToChat("&cInvalid mode. Available: &f" + String.join(", ", setting.getModes()));
                return;
            }
        }

        setting.setValue(selected);
        Util.logToChat(module.getName() + ": " + setting.getName() + " has been set to &9"
                + setting.getValue() + " &7(&f" + String.join("&7, &f", setting.getModes()) + "&7)");
    }

    private void setNumber(Module module, NumberValue setting, String[] arguments) {
        if (arguments.length == 2) {
            Util.logToChat(module.getName() + ": " + setting.getName() + " is &b"
                    + formatNumber(setting.getValue()) + " &7(range &f" + formatNumber(setting.getMin())
                    + "&7-&f" + formatNumber(setting.getMax()) + "&7).");
            return;
        }

        final float requested;
        try {
            requested = Float.parseFloat(joinValue(arguments));
        } catch (NumberFormatException exception) {
            Util.logToChat("&cExpected a number between " + formatNumber(setting.getMin())
                    + " and " + formatNumber(setting.getMax()) + ".");
            return;
        }

        if (!Float.isFinite(requested) || requested < setting.getMin() || requested > setting.getMax()) {
            Util.logToChat("&cValue must be between " + formatNumber(setting.getMin())
                    + " and " + formatNumber(setting.getMax()) + ".");
            return;
        }

        float adjusted = setting.getInc() > 0
                ? Math.round(requested / setting.getInc()) * setting.getInc()
                : requested;
        setting.setValue(adjusted);
        Util.logToChat(module.getName() + ": " + setting.getName() + " has been set to &b"
                + formatNumber(setting.getValue()) + ".");
    }

    private Module findModule(String name) {
        String normalized = normalize(name);
        return Client.instance.getModuleManager().getModuleMap().values().stream()
                .filter(module -> normalize(module.getName()).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private Value findSetting(Module module, String name) {
        String normalized = normalize(name);
        return getSettings(module, false).stream()
                .filter(setting -> normalize(setting.getName()).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private List<Value> getSettings(Module module, boolean visibleOnly) {
        List<Value> settings = new ArrayList<>();
        for (Value value : module.getValues()) {
            if (visibleOnly && !value.isVisible()) {
                continue;
            }
            if (value instanceof BoolValue || value instanceof ModeValue || value instanceof NumberValue) {
                settings.add(value);
            } else if (value instanceof MultiBoolValue multi) {
                for (BoolValue bool : multi.getValues()) {
                    if (!visibleOnly || bool.isVisible()) {
                        settings.add(bool);
                    }
                }
            }
        }
        return settings;
    }

    private static String joinValue(String[] arguments) {
        return String.join(" ", Arrays.copyOfRange(arguments, 2, arguments.length));
    }

    private static String commandName(String name) {
        return name.trim().replaceAll("\\s+", "-");
    }

    private static String normalize(String name) {
        StringBuilder normalized = new StringBuilder();
        name.codePoints()
                .filter(Character::isLetterOrDigit)
                .map(Character::toLowerCase)
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }

    private static String formatNumber(Number number) {
        float value = number.floatValue();
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.4f", value).replaceFirst("0+$", "").replaceFirst("\\.$", "");
    }
}
