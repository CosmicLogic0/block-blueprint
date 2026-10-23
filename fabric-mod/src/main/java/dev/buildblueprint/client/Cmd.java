package dev.buildblueprint.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The ONLY file that touches Fabric's client-command classes. If the build says one of the three
 * imports above doesn't exist, the older names are:
 *   ClientCommands  ->  ClientCommandManager      (same package)
 * (Client commands run on your PC only. They are never sent to the server.)
 */
final class Cmd {
    private Cmd() {}

    static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) { return ClientCommands.literal(name); }

    static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
        return ClientCommands.argument(name, type);
    }

    static void register(Consumer<CommandDispatcher<FabricClientCommandSource>> body) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> body.accept(dispatcher));
    }
}
