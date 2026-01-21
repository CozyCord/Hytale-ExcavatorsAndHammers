package net.cozystudios.excavatorsandhammers;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public class ExcavatorsAndHammers extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public ExcavatorsAndHammers(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    public void setup() {
        LOGGER.atInfo().log("ExcavatorsAndHammers is setting up...");

        // Load configuration from mods config folder
        Path configDir = Path.of("mods", "ExcavatorsAndHammers");
        ModConfig.load(configDir);

        if (ModConfig.getInstance().isLargeAreaMiningEnabled()) {
            LOGGER.atInfo().log("Large area mining (5x5) for Mithril/Adamantite tools is ENABLED");
        } else {
            LOGGER.atInfo().log("Large area mining (5x5) for Mithril/Adamantite tools is DISABLED");
        }

        try {
            this.getEntityStoreRegistry().registerSystem(new AreaMiningSystem.BreakAreaSystem());
            this.getEntityStoreRegistry().registerSystem(new AreaMiningSystem.DamageAreaSystem());
            LOGGER.atInfo().log("Registered AreaMiningSystem successfully");
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to register AreaMiningSystem");
        }
    }

    @Override
    public void start() {
        LOGGER.atInfo().log("ExcavatorsAndHammers has started!");
        LOGGER.atInfo().log("Hammer and Excavator tools now mine in a 3x3 area!");
    }

    @Override
    public void shutdown() {
        LOGGER.atInfo().log("ExcavatorsAndHammers has been disabled!");
    }
}
