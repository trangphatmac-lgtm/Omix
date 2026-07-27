package cn.omix.util;

import cn.omix.Client;
import net.minecraft.client.MinecraftClient;

public interface IMinecraft {
    MinecraftClient mc = MinecraftClient.getInstance();
    Client instance = Client.instance;
}
