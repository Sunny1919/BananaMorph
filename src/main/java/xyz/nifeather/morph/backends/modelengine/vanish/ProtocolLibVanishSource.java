package xyz.nifeather.morph.backends.modelengine.vanish;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import xyz.nifeather.morph.backends.server.renderer.network.PacketFactory;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.types.PlayerWatcher;
import xyz.nifeather.morph.backends.server.renderer.utilties.WatcherUtils;
import xyz.nifeather.morph.misc.NmsRecord;
import xyz.nifeather.morph.utilities.EntityTypeUtils;

import java.util.List;

public class ProtocolLibVanishSource extends PacketListenerAbstract implements IVanishSource
{
    private final List<Player> vanish = new ObjectArrayList<>();

    public ProtocolLibVanishSource()
    {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    private void sendPacket(Player player, Packet<?> packet)
    {
        var nmsPlayer = NmsRecord.ofPlayer(player);
        if (nmsPlayer.connection != null)
            nmsPlayer.connection.sendPacket(packet);
    }

    @Override
    public void vanishPlayer(Player player)
    {
        vanish.add(player);

        var affected = WatcherUtils.getAffectedPlayers(player);
        var packet = new ClientboundRemoveEntitiesPacket(player.getEntityId());

        for (var affectedPlayer : affected)
            this.sendPacket(affectedPlayer, packet);
    }

    private final PacketFactory packetFactory = new PacketFactory();

    @Override
    public void cancelVanish(Player player)
    {
        vanish.remove(player);

        var watcher = new PlayerWatcher(player);
        watcher.sync();

        var nmsPlayer = NmsRecord.ofPlayer(player);
        var packet = new ClientboundAddEntityPacket(
                player.getEntityId(), player.getUniqueId(),
                player.getX(), player.getY(), player.getZ(),
                player.getPitch(), player.getYaw(),
                EntityTypeUtils.getNmsType(EntityType.PLAYER), 0,
                nmsPlayer.getDeltaMovement(),
                nmsPlayer.getYHeadRot()
        );

        for (Player affected : WatcherUtils.getAffectedPlayers(player))
        {
            this.sendPacket(affected, packet);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent packetEvent)
    {
        if (packetEvent.getNMSPacket() instanceof ClientboundAddEntityPacket packet)
        {
            if (vanish.stream().anyMatch(p -> packet.getId() == p.getEntityId()))
                packetEvent.setCancelled(true);
        }
    }
}
