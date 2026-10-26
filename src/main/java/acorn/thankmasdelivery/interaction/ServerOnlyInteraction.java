package acorn.thankmasdelivery.interaction;

import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

abstract class ServerOnlyInteraction extends SimpleInstantInteraction {
    @Override
    public final WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }
}
