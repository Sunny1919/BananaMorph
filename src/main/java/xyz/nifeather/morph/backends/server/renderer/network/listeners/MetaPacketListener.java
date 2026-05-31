package xyz.nifeather.morph.backends.server.renderer.network.listeners;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import xiamomc.pluginbase.Annotations.Resolved;
import xyz.nifeather.morph.backends.server.renderer.network.registries.RenderRegistry;
import xyz.nifeather.morph.backends.server.renderer.network.registries.ValueIndex;

public class MetaPacketListener extends ProtocolListener
{
    @Resolved(shouldSolveImmediately = true)
    private RenderRegistry registry;

    @Override
    public String getIdentifier()
    {
        return "meta_listener";
    }

    @Override
    public void onPacketSend(PacketSendEvent event)
    {
        if (event.getNMSPacket() instanceof ClientboundSetEntityDataPacket clientboundSetEntityDataPacket)
        {
            //不要处理来自我们自己的包
            if (getFactory().isPacketOurs(clientboundSetEntityDataPacket))
                return;

            onMetaPacket(clientboundSetEntityDataPacket, event);
        }
    }

    private void onMetaPacket(ClientboundSetEntityDataPacket packet, PacketSendEvent packetEvent)
    {
        //获取此包의来源实体
        var sourceNmsEntity = getNmsPlayerFrom(packet.id());

        // How could this be?!
        if (sourceNmsEntity == null)
            return;

        if (!(sourceNmsEntity.getBukkitEntity() instanceof Player sourcePlayer)) return;

        Player targetPlayer = (Player) packetEvent.getPlayer();
        if (targetPlayer == null) return;

        if (sourcePlayer.equals(targetPlayer)) return;

        var watcher = registry.getWatcher(sourcePlayer.getUniqueId());

        if (watcher == null)
            return;

        //只拦截其他人的Meta
        if (targetPlayer == sourcePlayer)
            return;

        //取得来源玩家的伪装后的Meta，发送给目标玩家
        //从包里移除玩家meta中不属于BASE_LIVING的部分
        var isPlayerDisguise = watcher.getEntityType() == EntityType.PLAYER;
        var finalPacket = getFactory().rebuildServerMetaPacket(
                isPlayerDisguise ? ValueIndex.PLAYER : ValueIndex.BASE_LIVING,
                watcher,
                packet);

        if (finalPacket.packedItems() == null || finalPacket.packedItems().isEmpty())
            packetEvent.setCancelled(true);

        packetEvent.setNMSPacket(finalPacket);
    }
}
