package cn.omix.util.opai.bridge;
import cn.omix.Client;
import cn.omix.config.Config;
import java.io.File;
/** Routes both imported UIs through Omix's native (including encrypted) profiles. */
public final class ConfigManager {
 private static cn.omix.config.ConfigManager manager(){return Client.instance.getConfigManager();}
 public static File getConfigDir(){return Config.getDirectory();}
 public static String validName(String raw){String name=raw.strip();if(!cn.omix.util.skeet.SkeetProfiles.validName(name))throw new IllegalArgumentException("Invalid configuration name");return name;}
 public static String[] getConfigNames(){return manager().getConfigs().stream().filter(c->c.getFile().isFile()).map(Config::getName).toArray(String[]::new);}
 public static void saveState(){try{manager().saveConfigChecked("Default");}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
 public static void saveQuietly(){try{saveState();}catch(RuntimeException error){Client.logger.warn("Could not save Opai layout",error);}}
 public static void m32(String name){try{manager().saveConfigChecked(validName(name));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
 public static void m33(String name){try{manager().loadConfigChecked(validName(name));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
 public static boolean m36(String name){return manager().deleteConfig(validName(name));}
 public static boolean m38(String name){var config=manager().getConfig(validName(name));return config!=null&&config.getFile().isFile();}
}
