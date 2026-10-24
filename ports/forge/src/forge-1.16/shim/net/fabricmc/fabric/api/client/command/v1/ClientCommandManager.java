package net.fabricmc.fabric.api.client.command.v1;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Stand-in for Fabric's v1 client commands: commands are built here, then handed to Forge. */
public final class ClientCommandManager {
    private ClientCommandManager() {}
    public static final CommandDispatcher<CommandSourceStack> DISPATCHER = new CommandDispatcher<>();
    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) { return Commands.literal(name); }
    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) { return Commands.argument(name, type); }
    static { /* Forge 1.16 has no client commands: the Page Up menu and keys do everything. */ }
}
