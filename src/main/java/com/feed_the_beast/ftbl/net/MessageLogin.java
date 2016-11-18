package com.feed_the_beast.ftbl.net;

import com.feed_the_beast.ftbl.FTBLibConfig;
import com.feed_the_beast.ftbl.FTBLibMod;
import com.feed_the_beast.ftbl.api.EnumReloadType;
import com.feed_the_beast.ftbl.api.IFTBLibPlugin;
import com.feed_the_beast.ftbl.api.IForgePlayer;
import com.feed_the_beast.ftbl.api.INotification;
import com.feed_the_beast.ftbl.api.ISyncData;
import com.feed_the_beast.ftbl.api_impl.PackMode;
import com.feed_the_beast.ftbl.api_impl.SharedClientData;
import com.feed_the_beast.ftbl.api_impl.SharedServerData;
import com.feed_the_beast.ftbl.lib.internal.FTBLibFinals;
import com.feed_the_beast.ftbl.lib.internal.FTBLibIntegrationInternal;
import com.feed_the_beast.ftbl.lib.io.Bits;
import com.feed_the_beast.ftbl.lib.net.LMNetworkWrapper;
import com.feed_the_beast.ftbl.lib.net.MessageToClient;
import com.feed_the_beast.ftbl.lib.util.LMNetUtils;
import com.feed_the_beast.ftbl.lib.util.LMServerUtils;
import gnu.trove.map.hash.TShortObjectHashMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

public class MessageLogin extends MessageToClient<MessageLogin>
{
    private static final byte IS_OP = 1;
    private static final byte OPTIONAL_SERVER_MODS = 2;
    private static final byte USE_FTB_PREFIX = 4;

    private byte flags;
    private String currentMode;
    private UUID universeID;
    private TShortObjectHashMap<INotification> notificationIDs;
    private NBTTagCompound syncData;
    private Collection<String> optionalServerMods;

    public MessageLogin()
    {
    }

    public MessageLogin(EntityPlayerMP player, IForgePlayer forgePlayer)
    {
        flags = 0;
        flags = Bits.setFlag(flags, IS_OP, LMServerUtils.isOP(player.getGameProfile()));
        flags = Bits.setFlag(flags, USE_FTB_PREFIX, FTBLibConfig.USE_FTB_COMMAND_PREFIX.getBoolean());
        currentMode = SharedServerData.INSTANCE.getPackMode().getID();
        universeID = SharedServerData.INSTANCE.getUniverseID();
        notificationIDs = SharedServerData.INSTANCE.cachedNotifications;
        syncData = new NBTTagCompound();
        FTBLibMod.PROXY.SYNCED_DATA.forEach((key, value) -> syncData.setTag(key, value.writeSyncData(player, forgePlayer)));
        optionalServerMods = SharedServerData.INSTANCE.optionalServerMods;
        flags = Bits.setFlag(flags, OPTIONAL_SERVER_MODS, !optionalServerMods.isEmpty());
    }

    @Override
    public LMNetworkWrapper getWrapper()
    {
        return FTBLibNetHandler.NET;
    }

    @Override
    public void toBytes(ByteBuf io)
    {
        io.writeByte(flags);
        LMNetUtils.writeString(io, currentMode);
        LMNetUtils.writeUUID(io, universeID);

        io.writeShort(notificationIDs.size());

        notificationIDs.forEachEntry((key, value) ->
        {
            io.writeShort(key);
            MessageNotifyPlayer.write(io, value);
            return true;
        });

        LMNetUtils.writeTag(io, syncData);

        if(!optionalServerMods.isEmpty())
        {
            io.writeShort(optionalServerMods.size());

            for(String s : optionalServerMods)
            {
                LMNetUtils.writeString(io, s);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf io)
    {
        flags = io.readByte();
        currentMode = LMNetUtils.readString(io);
        universeID = LMNetUtils.readUUID(io);

        int s = io.readUnsignedShort();
        notificationIDs = new TShortObjectHashMap<>(s);

        while(--s >= 0)
        {
            short id = io.readShort();
            INotification n = MessageNotifyPlayer.read(io);
            notificationIDs.put(id, n);
        }

        syncData = LMNetUtils.readTag(io);

        if(Bits.getFlag(flags, OPTIONAL_SERVER_MODS))
        {
            s = io.readUnsignedShort();
            optionalServerMods = new HashSet<>(s);

            while(--s >= 0)
            {
                optionalServerMods.add(LMNetUtils.readString(io));
            }
        }
    }

    @Override
    public void onMessage(MessageLogin m)
    {
        SharedClientData.INSTANCE.reset();
        SharedClientData.INSTANCE.hasServer = true;
        SharedClientData.INSTANCE.isClientPlayerOP = Bits.getFlag(m.flags, IS_OP);
        SharedClientData.INSTANCE.useFTBPrefix = Bits.getFlag(m.flags, USE_FTB_PREFIX);
        SharedClientData.INSTANCE.universeID = m.universeID;
        SharedClientData.INSTANCE.currentMode = new PackMode(m.currentMode);

        if(m.optionalServerMods != null && !m.optionalServerMods.isEmpty())
        {
            SharedClientData.INSTANCE.optionalServerMods.addAll(m.optionalServerMods);
        }

        SharedClientData.INSTANCE.cachedNotifications.putAll(m.notificationIDs);

        for(String key : m.syncData.getKeySet())
        {
            ISyncData nbt = FTBLibMod.PROXY.SYNCED_DATA.get(key);

            if(nbt != null)
            {
                nbt.readSyncData(m.syncData.getCompoundTag(key));
            }
        }

        //TODO: new EventFTBWorldClient(ForgeWorldSP.inst).post();

        ICommandSender sender = Minecraft.getMinecraft().thePlayer;

        for(IFTBLibPlugin plugin : FTBLibIntegrationInternal.API.getAllPlugins())
        {
            plugin.onReload(Side.CLIENT, sender, EnumReloadType.LOGIN);
        }

        FTBLibFinals.LOGGER.info("Current Mode: " + m.currentMode);
    }
}