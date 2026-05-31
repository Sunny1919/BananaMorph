package xyz.nifeather.morph.backends.server.renderer.network.listeners;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import org.bukkit.entity.Player;
import xiamomc.pluginbase.Annotations.Resolved;
import xyz.nifeather.morph.backends.server.renderer.network.registries.RenderRegistry;

public class AnimationPacketListener extends ProtocolListener
{
    @Override
    public String getIdentifier()
    {
        return "animation_listener";
    }

    @Override
    public void onPacketSend(PacketSendEvent event)
    {
        if (event.getNMSPacket() instanceof ClientboundAnimatePacket clientboundAnimatePacket)
        {
            onAnimationPacket(event, clientboundAnimatePacket);
        }
    }

    @Resolved(shouldSolveImmediately = true)
    private RenderRegistry registry;

    private void onAnimationPacket(PacketSendEvent event, ClientboundAnimatePacket clientboundAnimatePacket)
    {
        if (clientboundAnimatePacket.getAction() != ClientboundAnimatePacket.WAKE_UP)
            return;

        var sourceEntityId = clientboundAnimatePacket.getId();
        var nmsPlayer = this.getNmsPlayerFrom(sourceEntityId);

        if (nmsPlayer == null) return;

        if (!(nmsPlayer.getBukkitEntity() instanceof Player sourcePlayer)) return;

        var watcher = registry.getWatcher(sourcePlayer.getUniqueId());

        if (watcher == null)
            return;

        Player targetPlayer = (Player) event.getPlayer();
        if (targetPlayer == null) return;

        // Don't cancel for the source
        if (targetPlayer.equals(sourcePlayer)) return;

        event.setCancelled(true);
    }
}
