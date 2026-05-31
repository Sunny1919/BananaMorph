package xyz.nifeather.morph.backends.server.renderer.network.listeners;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;
import xiamomc.pluginbase.Annotations.Initializer;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Bindables.Bindable;
import xyz.nifeather.morph.MorphPluginObject;
import xyz.nifeather.morph.backends.server.renderer.network.PacketFactory;
import xyz.nifeather.morph.config.ConfigOption;
import xyz.nifeather.morph.config.MorphConfigManager;
import xyz.nifeather.morph.utilities.NmsUtils;
import xyz.nifeather.morph.utilities.ReflectionUtils;

import java.lang.reflect.Field;

public abstract class ProtocolListener extends MorphPluginObject
{
    @Resolved(shouldSolveImmediately = true)
    private PacketFactory packetFactory;

    public abstract String getIdentifier();

    protected PacketFactory getFactory() { return packetFactory; }

    public abstract void onPacketSend(PacketSendEvent event);

    private final Bindable<Boolean> debugOutput = new Bindable<>(false);

    protected boolean isDebugEnabled()
    {
        return debugOutput.get();
    }

    @Initializer
    private void load(MorphConfigManager configManager)
    {
        configManager.bind(debugOutput, ConfigOption.DEBUG_OUTPUT);
    }

    protected Player getNmsPlayerEntityFromUnreadablePacket(Object packet)
    {
        int entityId;

        try
        {
            entityId = ReflectionUtils.getValue(packet, "entityId", int.class, false);
        }
        catch (Throwable t)
        {
            if (isDebugEnabled())
            {
                logger.error("No field 'entityId' in packet " + packet + "! Skipping: " + t.getMessage());

                logger.info("Valid fields: ");
                for (Field declaredField : packet.getClass().getDeclaredFields())
                {
                    logger.info("  \\--" + declaredField.getName());
                }
            }

            return null;
        }

        return this.getNmsPlayerFrom(entityId);
    }

    @Nullable
    protected Player getNmsPlayerFrom(int id)
    {
        for (var world : Bukkit.getWorlds())
        {
            var nmsWorld = NmsUtils.getNmsLevel(world);
            var worldPlayers = nmsWorld.players();

            var match = worldPlayers.stream()
                    .filter(p -> p.getId() == id)
                    .findFirst()
                    .orElse(null);

            if (match != null)
                return match;
        }

        return null;
    }

    @Nullable
    protected Entity getNmsEntityFrom(PacketSendEvent event, int id)
    {
        org.bukkit.entity.Player packetTarget = (org.bukkit.entity.Player) event.getPlayer();
        if (packetTarget == null) return null;

        var sourceNmsEntity = NmsUtils.getNmsLevel(packetTarget.getWorld()).getEntity(id);
        if (sourceNmsEntity == null)
        {
            if (debugOutput.get())
            {
                logger.warn("A packet from a player that doesn't exist in its world?!");
            }

            return null;
        }

        return sourceNmsEntity;
    }
}
