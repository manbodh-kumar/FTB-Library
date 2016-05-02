package ftb.lib.mod.net;

import ftb.lib.EnumScreen;
import ftb.lib.api.client.FTBLibClient;
import ftb.lib.api.net.LMNetworkWrapper;
import ftb.lib.api.net.MessageLM;
import ftb.lib.api.notification.ClientNotifications;
import ftb.lib.api.notification.Notification;
import ftb.lib.mod.client.FTBLibModClient;
import io.netty.buffer.ByteBuf;
import latmod.lib.LMJsonUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class MessageNotifyPlayer extends MessageLM<MessageNotifyPlayer>
{
	public String json;
	
	public MessageNotifyPlayer() { }
	
	public MessageNotifyPlayer(Notification n)
	{
		json = LMJsonUtils.toJson(n.getSerializableElement());
	}
	
	@Override
	public LMNetworkWrapper getWrapper()
	{ return FTBLibNetHandler.NET_INFO; }
	
	@Override
	public void fromBytes(ByteBuf io)
	{
		json = readString(io);
	}
	
	@Override
	public void toBytes(ByteBuf io)
	{
		writeString(io, json);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public IMessage onMessage(MessageNotifyPlayer m, MessageContext ctx)
	{
		if(FTBLibModClient.notifications.get() != EnumScreen.OFF)
		{
			Notification n = Notification.deserialize(LMJsonUtils.fromJson(m.json));
			
			if(FTBLibModClient.notifications.get() == EnumScreen.SCREEN) { ClientNotifications.add(n); }
			else
			{
				FTBLibClient.mc.thePlayer.addChatMessage(n.title);
				if(n.desc != null) { FTBLibClient.mc.thePlayer.addChatMessage(n.desc); }
			}
		}
		
		return null;
	}
}