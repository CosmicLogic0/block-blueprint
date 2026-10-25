package net.fabricmc.fabric.api.client.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

/** Stand-in for Fabric's client command callback (Forge 1.21.6+). */
public interface ClientCommandRegistrationCallback {
    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context);
    Holder EVENT = new Holder();
    final class Holder {
        private final List<ClientCommandRegistrationCallback> list = new ArrayList<>();
        private boolean hooked;
        public void register(ClientCommandRegistrationCallback cb) {
            if (!hooked) { hooked = true; RegisterClientCommandsEvent.BUS.addListener(e -> { for (ClientCommandRegistrationCallback c : list) c.register(e.getDispatcher(), e.getBuildContext()); }); }
            list.add(cb);
        }
    }
}
