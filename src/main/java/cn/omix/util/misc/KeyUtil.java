package cn.omix.util.misc;

import lombok.experimental.UtilityClass;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.function.IntPredicate;

@UtilityClass
public final class KeyUtil {

    // Keep 0 (unbound), -1 (legacy unbound), and positive GLFW keyboard codes intact.
    public int mouseKeyCode(int button) {
        if (button < 0 || button > GLFW.GLFW_MOUSE_BUTTON_LAST) return 0;
        return button - 100;
    }

    public int mouseButton(int keyCode) {
        return keyCode >= -100 && keyCode <= GLFW.GLFW_MOUSE_BUTTON_LAST - 100 ? keyCode + 100 : -1;
    }

    public boolean isPressed(int keyCode, IntPredicate keyboardPressed, IntPredicate mousePressed) {
        int button = mouseButton(keyCode);
        return button >= 0 ? mousePressed.test(button)
                : keyCode > 0 && keyCode <= GLFW.GLFW_KEY_LAST && keyboardPressed.test(keyCode);
    }

    public int getKeyCode(String keyName) {
        try {
            String name = keyName.toUpperCase(Locale.ROOT);
            if (name.equals("NONE")) {
                return 0;
            }

            Field field = GLFW.class.getField("GLFW_KEY_" + name);
            return field.getInt(null);
        } catch (Exception exception) {
            return 0;
        }
    }

    public String getKeyName(int keyCode) {
        int button = mouseButton(keyCode);
        if (button >= 0) {
            return button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE ? "MOUSE MIDDLE" : "MOUSE " + (button + 1);
        }
        if (keyCode <= 0) {
            return "None";
        }

        int scancode = GLFW.glfwGetKeyScancode(keyCode);
        String name = GLFW.glfwGetKeyName(keyCode, scancode);

        if (name == null) {
            try {
                for (Field field : GLFW.class.getFields()) {
                    if (field.getName().startsWith("GLFW_KEY_") && field.getInt(null) == keyCode) {
                        return field.getName().substring(9);
                    }
                }
            } catch (Exception exception) {
                return "UNKNOWN";
            }
        }

        return name != null ? name.toUpperCase(Locale.ROOT) : "UNKNOWN";
    }
}
