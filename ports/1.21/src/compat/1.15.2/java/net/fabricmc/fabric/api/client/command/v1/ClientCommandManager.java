package net.fabricmc.fabric.api.client.command.v1;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

/** Minecraft 1.15: Fabric API has no client commands; commands go into a dispatcher nothing reads (keys and menus work). */
public final class ClientCommandManager {
    private ClientCommandManager() {}
    public static final CommandDispatcher<FabricClientCommandSource> DISPATCHER = new CommandDispatcher<>();
    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) { return LiteralArgumentBuilder.literal(name); }
    public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) { return RequiredArgumentBuilder.argument(name, type); }
}
