package net.fabricmc.fabric.api.client.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Stand-in for Fabric's client command callback, backed by NeoForge's RegisterClientCommandsEvent. */
public interface ClientCommandRegistrationCallback {
    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context);
    Holder EVENT = new Holder();
    final class Holder {
        private final List<ClientCommandRegistrationCallback> list = new ArrayList<>();
        private boolean hooked;
        public void register(ClientCommandRegistrationCallback cb) {
            if (!hooked) { hooked = true; NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> { for (ClientCommandRegistrationCallback c : list) c.register(e.getDispatcher(), e.getBuildContext()); }); }
            list.add(cb);
        }
    }
}
