package xyz.nifeather.morph.backends.server.renderer.network.listeners;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import xiamomc.pluginbase.Annotations.Resolved;
import xyz.nifeather.morph.backends.server.renderer.network.registries.RenderRegistry;
import xyz.nifeather.morph.utilities.ReflectionUtils;

public class PlayerLookPacketListener extends ProtocolListener
{
    @Override
    public String getIdentifier()
    {
        return "look_move_listener";
    }

    @Override
    public void onPacketSend(PacketSendEvent event)
    {
        var packet = event.getNMSPacket();
        if (packet == null) return;

        //不要处理来自我们自己的包
        if (getFactory().isPacketOurs((net.minecraft.network.protocol.Packet<?>) packet))
        {
            return;
        }

        if (packet instanceof ClientboundMoveEntityPacket clientboundMoveEntityPacket)
        {
            onLookPacket(clientboundMoveEntityPacket, event);
        }
        else if (packet instanceof ClientboundRotateHeadPacket clientboundRotateHeadPacket)
        {
            onHeadRotation(clientboundRotateHeadPacket, event);
        }
        else if (packet instanceof ClientboundTeleportEntityPacket clientboundTeleportEntityPacket)
        {
            onTeleport(clientboundTeleportEntityPacket, event);
        }
    }

    @Resolved(shouldSolveImmediately = true)
    private RenderRegistry registry;

    private void onTeleport(ClientboundTeleportEntityPacket packet, PacketSendEvent event)
    {
        //获取此包的来源实体
        var sourceNmsEntity = getNmsPlayerFrom(packet.getId());
        if (sourceNmsEntity == null)
            return;

        if (!(sourceNmsEntity.getBukkitEntity() instanceof Player sourcePlayer)) return;

        var watcher = registry.getWatcher(sourcePlayer.getUniqueId());

        if (watcher == null)
            return;

        var isDragon = watcher.getEntityType() == EntityType.ENDER_DRAGON;
        var isPhantom = watcher.getEntityType() == EntityType.PHANTOM;

        if (!isDragon && !isPhantom)
            return;

        var yaw = packet.getyRot();
        var pitch = packet.getxRot();

        var playerYaw = isDragon ? (sourcePlayer.getYaw() + 180f) : sourcePlayer.getYaw();
        var finalYaw = (playerYaw / 360f) * 256f;
        yaw = (byte)finalYaw;

        var playerPitch = isPhantom ? -sourcePlayer.getPitch() : sourcePlayer.getPitch();

        var finalPitch = (playerPitch / 360f) * 256f;
        pitch = (byte)finalPitch;

        ReflectionUtils.setValue(packet, "yRot", yaw);
        ReflectionUtils.setValue(packet, "xRot", pitch);
    }

    private void onHeadRotation(ClientboundRotateHeadPacket packet, PacketSendEvent event)
    {
        //获取此包的来源实体
        var sourceNmsEntity = this.getNmsPlayerEntityFromUnreadablePacket(packet);
        if (sourceNmsEntity == null) return;

        if (!(sourceNmsEntity.getBukkitEntity() instanceof Player sourcePlayer)) return;

        var watcher = registry.getWatcher(sourcePlayer.getUniqueId());

        if (watcher == null || watcher.getEntityType() != EntityType.ENDER_DRAGON)
            return;

        var newHeadYaw = (byte)(((sourcePlayer.getYaw() + 180f) / 360f) * 256f);

        var newPacket = new ClientboundRotateHeadPacket(sourceNmsEntity, newHeadYaw);
        getFactory().markPacketOurs(newPacket);

        event.setNMSPacket(newPacket);
    }

    private void onLookPacket(ClientboundMoveEntityPacket packet, PacketSendEvent event)
    {
        //获取此包的来源实体
        var sourceNmsEntity = this.getNmsPlayerEntityFromUnreadablePacket(packet);

        if (sourceNmsEntity == null) return;

        if (!(sourceNmsEntity.getBukkitEntity() instanceof Player sourcePlayer)) return;

        var watcher = registry.getWatcher(sourcePlayer.getUniqueId());

        if (watcher == null)
            return;

        var isDragon = watcher.getEntityType() == EntityType.ENDER_DRAGON;
        var isPhantom = watcher.getEntityType() == EntityType.PHANTOM;

        if (!isDragon && !isPhantom)
            return;

        var yaw = packet.getyRot();
        var pitch = packet.getxRot();

        var playerYaw = isDragon ? (sourcePlayer.getYaw() + 180f) : sourcePlayer.getYaw();
        var finalYaw = (playerYaw / 360f) * 256f;
        yaw = (byte)finalYaw;

        var playerPitch = isPhantom ? -sourcePlayer.getPitch() : sourcePlayer.getPitch();

        var finalPitch = (playerPitch / 360f) * 256f;
        pitch = (byte)finalPitch;

        ClientboundMoveEntityPacket newPacket;

        if (packet instanceof ClientboundMoveEntityPacket.Rot)
        {
            newPacket = new ClientboundMoveEntityPacket.Rot(
                    sourcePlayer.getEntityId(),
                    yaw, pitch,
                    packet.isOnGround()
            );
        }
        else if (packet instanceof ClientboundMoveEntityPacket.Pos)
        {
            newPacket = new ClientboundMoveEntityPacket.Pos(
                    sourcePlayer.getEntityId(),
                    packet.getXa(), packet.getYa(), packet.getZa(),
                    packet.isOnGround()
            );
        }
        else if (packet instanceof ClientboundMoveEntityPacket.PosRot)
        {
            newPacket = new ClientboundMoveEntityPacket.PosRot(
                    sourcePlayer.getEntityId(),
                    packet.getXa(), packet.getYa(), packet.getZa(),
                    yaw, pitch,
                    packet.isOnGround()
            );
        }
        else
        {
            return;
        }

        getFactory().markPacketOurs(newPacket);
        event.setNMSPacket(newPacket);
    }
}
