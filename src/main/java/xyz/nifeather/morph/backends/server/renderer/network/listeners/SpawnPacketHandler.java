package xyz.nifeather.morph.backends.server.renderer.network.listeners;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Exceptions.NullDependencyException;
import xyz.nifeather.morph.backends.server.renderer.network.DisplayParameters;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.SingleWatcher;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.types.PlayerWatcher;
import xyz.nifeather.morph.backends.server.renderer.network.registries.CustomEntries;
import xyz.nifeather.morph.backends.server.renderer.network.registries.RenderRegistry;
import xyz.nifeather.morph.backends.server.renderer.utilties.WatcherUtils;
import xyz.nifeather.morph.misc.NmsRecord;
import xyz.nifeather.morph.misc.skins.PlayerSkinProvider;

import java.util.List;
import java.util.UUID;

public class SpawnPacketHandler extends ProtocolListener
{
    @Resolved(shouldSolveImmediately = true)
    private RenderRegistry registry;

    @Override
    public String getIdentifier()
    {
        return "spawn_listener";
    }

    public SpawnPacketHandler()
    {
        registry.onRegister(this, ep ->
                refreshStateForPlayer(ep.player(), getAffectedPlayers(ep.player())));

        registry.onUnRegister(this, ep ->
                unDisguiseForPlayer(ep.player(), ep.watcher()));
    }

    private List<Player> getAffectedPlayers(Player sourcePlayer)
    {
        return WatcherUtils.getAffectedPlayers(sourcePlayer);
    }

    private void sendPacket(Player player, Packet<?> packet)
    {
        var nmsPlayer = NmsRecord.ofPlayer(player);
        if (nmsPlayer.connection != null)
            nmsPlayer.connection.sendPacket(packet);
    }

    private void unDisguiseForPlayer(@Nullable Player player, SingleWatcher disguiseWatcher)
    {
        if (player == null) return;

        var affectedPlayers = getAffectedPlayers(player);
        var watcher = new PlayerWatcher(player);
        watcher.markSilent(this);

        watcher.writeEntry(CustomEntries.PROFILE, ((CraftPlayer) player).getProfile());
        watcher.writeEntry(CustomEntries.SPAWN_UUID, player.getUniqueId());
        watcher.writeEntry(CustomEntries.SPAWN_ID, player.getEntityId());
        watcher.writeEntry(CustomEntries.PROFILE_LISTED, true);

        var packets = getFactory().buildSpawnPackets(new DisplayParameters(watcher));

        var removePacket = new ClientboundRemoveEntitiesPacket(player.getEntityId());

        if (disguiseWatcher.getEntityType() == org.bukkit.entity.EntityType.PLAYER
                && !disguiseWatcher.readEntryOrDefault(CustomEntries.PROFILE_LISTED, false))
        {
            var disguiseUUID = disguiseWatcher.readEntryOrThrow(CustomEntries.SPAWN_UUID);

            var packetRemoveInfo = new ClientboundPlayerInfoRemovePacket(List.of(disguiseUUID));

            Bukkit.getOnlinePlayers().forEach(p -> this.sendPacket(p, packetRemoveInfo));
        }

        watcher.dispose();

        affectedPlayers.forEach(p ->
        {
            this.sendPacket(p, removePacket);

            for (Packet<?> packet : packets)
                this.sendPacket(p, packet);
        });
    }

    private void refreshStateForPlayer(@Nullable Player player, List<Player> affectedPlayers)
    {
        if (player == null) return;

        var watcher = registry.getWatcher(player.getUniqueId());
        if (watcher == null)
            throw new NullDependencyException("Null Watcher for a existing player?!");

        refreshStateForPlayer(player,
                new DisplayParameters(watcher),
                affectedPlayers);
    }

    private void refreshStateForPlayer(@Nullable Player player, @NotNull DisplayParameters displayParameters, List<Player> affectedPlayers)
    {
        if (affectedPlayers.isEmpty()) return;

        if (player == null) return;
        var watcher = displayParameters.getWatcher();

        //先发包移除当前实体
        var packetRemove = new ClientboundRemoveEntitiesPacket(player.getEntityId());

        //然后发包创建实体
        if (watcher.getEntityType() == org.bukkit.entity.EntityType.PLAYER && watcher.readEntry(CustomEntries.PROFILE) == null)
        {
            var disguiseName = watcher.readEntry(CustomEntries.DISGUISE_NAME);

            if (disguiseName == null || disguiseName.isBlank())
            {
                logger.error("Parameter 'disguiseName' cannot be null or blank!");
                Thread.dumpStack();
                return;
            }

            var targetPlayer = Bukkit.getPlayerExact(disguiseName);

            GameProfile targetProfile = watcher.readEntryOrDefault(CustomEntries.PROFILE, null);

            if (targetProfile == null)
            {
                targetProfile = targetPlayer == null
                        ? PlayerSkinProvider.getInstance().getCachedProfile(disguiseName)
                        : NmsRecord.ofPlayer(targetPlayer).gameProfile;
            }

            watcher.writeEntry(CustomEntries.PROFILE, targetProfile == null ? new GameProfile(UUID.randomUUID(), disguiseName) : targetProfile);
        }

        var parametersFinal = new DisplayParameters(watcher);
        var spawnPackets = getFactory().buildSpawnPackets(parametersFinal);

        affectedPlayers.forEach(p ->
        {
            this.sendPacket(p, packetRemove);

            spawnPackets.forEach(packet -> this.sendPacket(p, packet));
        });
    }

    private void onEntityAddPacket(ClientboundAddEntityPacket packet, PacketSendEvent packetEvent)
    {
        var bindingWatcher = registry.getWatcher(packet.getUUID());
        if (bindingWatcher == null)
            return;

        //不要二次处理来自我们自己的包
        if (!getFactory().isPacketOurs(packet))
        {
            packetEvent.setCancelled(true);
            Player targetPlayer = (Player) packetEvent.getPlayer();
            if (targetPlayer != null)
                refreshStateForPlayer(Bukkit.getPlayer(packet.getUUID()), List.of(targetPlayer));
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event)
    {
        if (event.getNMSPacket() instanceof ClientboundAddEntityPacket originalPacket
                && originalPacket.getType() == EntityType.PLAYER)
        {
            onEntityAddPacket(originalPacket, event);
        }
    }
}
