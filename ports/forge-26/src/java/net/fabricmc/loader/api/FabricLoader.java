package net.fabricmc.loader.api;

import java.nio.file.Path;
import java.util.Optional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

/** Stand-in for the bits of Fabric Loader the mod uses: the config folder and its own version. */
public final class FabricLoader {
    private static final FabricLoader I = new FabricLoader();
    public static FabricLoader getInstance() { return I; }
    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    public Optional<Container> getModContainer(String id) {
        return ModList.getModContainerById(id).map(c -> new Container(c.getModInfo().getVersion().toString()));
    }
    public static final class Container {
        private final String v; Container(String v) { this.v = v; }
        public Container getMetadata() { return this; }
        public Container getVersion() { return this; }
        public String getFriendlyString() { return v; }
    }
}
