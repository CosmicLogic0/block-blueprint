package net.fabricmc.fabric.api.client.command.v1;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;

/** Stand-in for Fabric's v1 client commands: commands are built here, then handed to Forge. */
public final class ClientCommandManager {
    private ClientCommandManager() {}
    public static final CommandDispatcher<CommandSourceStack> DISPATCHER = new CommandDispatcher<>();
    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) { return Commands.literal(name); }
    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) { return Commands.argument(name, type); }
    static { MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> { for (CommandNode<CommandSourceStack> n : DISPATCHER.getRoot().getChildren()) e.getDispatcher().getRoot().addChild(n); }); }
}
