package com.feed_the_beast.ftbl.client;

import com.feed_the_beast.ftbl.api.EventHandler;
import com.feed_the_beast.ftbl.api.INotification;
import com.feed_the_beast.ftbl.api_impl.SharedClientData;
import com.feed_the_beast.ftbl.client.teamsgui.MyTeamData;
import com.feed_the_beast.ftbl.lib.Color4I;
import com.feed_the_beast.ftbl.lib.MouseButton;
import com.feed_the_beast.ftbl.lib.Notification;
import com.feed_the_beast.ftbl.lib.SidebarButton;
import com.feed_the_beast.ftbl.lib.client.FTBLibClient;
import com.feed_the_beast.ftbl.lib.client.ImageProvider;
import com.feed_the_beast.ftbl.lib.gui.GuiHelper;
import com.feed_the_beast.ftbl.lib.item.ODItems;
import com.feed_the_beast.ftbl.lib.util.LMUtils;
import com.feed_the_beast.ftbl.lib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.chat.IChatListener;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ChatType;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author LatvianModder
 */
@EventHandler(Side.CLIENT)
public class FTBLibClientEventHandler
{
	private static Temp currentNotification;
	private static final Notification MINECRAFT_NOTIFICATION = new Notification(new ResourceLocation("minecraft", "status"));

	private static final IChatListener CHAT_LISTENER = (type, component) ->
	{
		if (type == ChatType.GAME_INFO)
		{
			if (component instanceof INotification)
			{
				addNotification((INotification) component);
			}
			else if (FTBLibClientConfig.REPLACE_STATUS_MESSAGE_WITH_NOTIFICATION.getBoolean())
			{
				MINECRAFT_NOTIFICATION.setDefaults();
				MINECRAFT_NOTIFICATION.addLine(component);
				addNotification(MINECRAFT_NOTIFICATION);
			}
			else
			{
				FTBLibClient.MC.ingameGUI.setOverlayMessage(component.getFormattedText(), false);
			}
		}
	};

	public static class NotificationWidget
	{
		public final INotification notification;
		public final List<String> text;
		public final int height;
		public int width;
		public final FontRenderer font;

		public NotificationWidget(INotification n, FontRenderer f)
		{
			notification = n;
			width = 0;
			font = f;

			if (notification.getText() == null)
			{
				text = Collections.emptyList();
				width = 20;
				height = 20;
			}
			else
			{
				text = new ArrayList<>();
				width = 0;

				for (String s : font.listFormattedStringToWidth(notification.getText().getFormattedText(), 250))
				{
					for (String line : s.split("\n"))
					{
						text.add(line);
						width = Math.max(width, font.getStringWidth(line));
					}
				}

				width += 4;
				height = text.size() * 12;

				if (notification.getIcon() != ImageProvider.NULL)
				{
					width += 10;
				}
			}
		}
	}

	private static class Temp
	{
		private static final LinkedHashMap<ResourceLocation, INotification> MAP = new LinkedHashMap<>();

		private long time;
		private NotificationWidget widget;

		private Temp(INotification n)
		{
			widget = new NotificationWidget(n, FTBLibClient.MC.fontRenderer);
			time = -1L;
		}

		public boolean render(ScaledResolution screen)
		{
			if (time == -1L)
			{
				time = System.currentTimeMillis();
			}
			else if (time <= 0L)
			{
				return true;
			}

			int timeExisted = (int) (System.currentTimeMillis() - time);
			int timer = widget.notification.getTimer() & 0xFFFF;
			int alpha = 255;

			if (timeExisted >= timer)
			{
				time = 0L;
				return true;
			}
			else if (timeExisted >= timer - 255)
			{
				alpha = timer - timeExisted;
			}

			if (alpha <= 1)
			{
				return true;
			}

			GlStateManager.pushMatrix();
			GlStateManager.translate((int) (screen.getScaledWidth() / 2F), (int) (screen.getScaledHeight() - 68F), 0F);
			GlStateManager.disableDepth();
			GlStateManager.depthMask(false);
			GlStateManager.disableLighting();
			GlStateManager.enableBlend();
			GlStateManager.color(1F, 1F, 1F, 1F);

			if (widget.notification.getIcon() != ImageProvider.NULL)
			{
				int s = widget.text.isEmpty() ? 16 : 8;
				widget.notification.getIcon().draw(0, (widget.height - s) / 2, s, s, alpha == 255 ? Color4I.NONE : new Color4I(false, Color4I.WHITE, alpha));
			}

			int offy = -(widget.text.size() * 11) / 2;

			for (int i = 0; i < widget.text.size(); i++)
			{
				String string = widget.text.get(i);
				widget.font.drawStringWithShadow(string, (widget.notification.getIcon() != ImageProvider.NULL ? 10 : 0) - widget.font.getStringWidth(string) / 2, offy + i * 11, 0xFFFFFF | (alpha << 24));
			}

			GlStateManager.depthMask(true);
			GlStateManager.color(1F, 1F, 1F, 1F);
			GlStateManager.enableLighting();
			GlStateManager.popMatrix();

			return false;
		}
	}

	@SubscribeEvent
	public static void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event)
	{
		SharedClientData.INSTANCE.reset();
		currentNotification = null;
		Temp.MAP.clear();
		FTBLibClient.MC.ingameGUI.chatListeners.get(ChatType.GAME_INFO).clear();
		FTBLibClient.MC.ingameGUI.chatListeners.get(ChatType.GAME_INFO).add(CHAT_LISTENER);
	}

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event)
	{
		if (FTBLibClientConfig.ITEM_ORE_NAMES.getBoolean())
		{
			Collection<String> ores = ODItems.getOreNames(null, event.getItemStack());

			if (!ores.isEmpty())
			{
				event.getToolTip().add(StringUtils.translate("client_config.ftbl.item_ore_names.tooltip"));

				for (String or : ores)
				{
					event.getToolTip().add("> " + or);
				}
			}
		}
	}

	@SubscribeEvent
	public static void guiInitEvent(final GuiScreenEvent.InitGuiEvent.Post event)
	{
		if (!(event.getGui() instanceof InventoryEffectRenderer))
		{
			return;
		}

		List<SidebarButton> buttons = FTBLibModClient.getSidebarButtons(false);

		if (!buttons.isEmpty())
		{
			GuiButtonSidebarGroup renderer = new GuiButtonSidebarGroup();
			event.getButtonList().add(renderer);

			for (int i = 0; i < buttons.size(); i++)
			{
				GuiButtonSidebar b = new GuiButtonSidebar(i, buttons.get(i));
				event.getButtonList().add(b);
				renderer.buttons.add(b);
			}

			renderer.updateButtonPositions();
		}
	}

	@SubscribeEvent
	public static void guiActionEvent(GuiScreenEvent.ActionPerformedEvent.Post event)
	{
		if (event.getButton() instanceof GuiButtonSidebar)
		{
			GuiHelper.playClickSound();
			(((GuiButtonSidebar) event.getButton()).button).onClicked(MouseButton.LEFT);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void renderGameOverlayEvent(RenderGameOverlayEvent.Pre event)
	{
		if ((currentNotification != null || !Temp.MAP.isEmpty()) && event.getType() == RenderGameOverlayEvent.ElementType.TEXT)
		{
			if (currentNotification != null)
			{
				if (currentNotification.render(event.getResolution()))
				{
					currentNotification = null;
				}

				GlStateManager.color(1F, 1F, 1F, 1F);
				GlStateManager.disableLighting();
				GlStateManager.enableBlend();
				GlStateManager.enableTexture2D();
			}
			else if (!Temp.MAP.isEmpty())
			{
				currentNotification = new Temp(Temp.MAP.values().iterator().next());
				Temp.MAP.remove(currentNotification.widget.notification.getId());
			}
		}
	}

	public static void addNotification(INotification n)
	{
		ResourceLocation id = n.getId();
		Temp.MAP.remove(id);

		if (currentNotification != null && currentNotification.widget.notification.getId().equals(n.getId()))
		{
			currentNotification = null;
		}

		Temp.MAP.put(id, n);
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void renderWorld(RenderWorldLastEvent event)
	{
		FTBLibClient.updateRenderInfo();
	}

	private static class GuiButtonSidebar extends GuiButton
	{
		public final int index;
		public final SidebarButton button;
		public final String title;
		public final boolean renderMessages;

		public GuiButtonSidebar(int id, SidebarButton b)
		{
			super(495830 + id, -16, -16, 16, 16, "");
			index = id;
			button = b;
			title = StringUtils.translate("sidebar_button." + b.getName());
			renderMessages = b.getName().equals("ftbl.teams_gui");
		}

		@Override
		public void drawButton(Minecraft mc, int mx, int my, float partialTicks)
		{
		}
	}

	private static class GuiButtonSidebarGroup extends GuiButton
	{
		public final List<GuiButtonSidebar> buttons;
		private int prevGuiLeft = -1, prevGuiTop = -1;

		public GuiButtonSidebarGroup()
		{
			super(495829, -1000, -1000, 0, 0, "");
			buttons = new ArrayList<>();
		}

		public void updateButtonPositions()
		{
			if (!(FTBLibClient.MC.currentScreen instanceof InventoryEffectRenderer))
			{
				return;
			}

			InventoryEffectRenderer gui = (InventoryEffectRenderer) FTBLibClient.MC.currentScreen;
			int guiLeft = GuiHelper.getGuiX(gui);
			int guiTop = GuiHelper.getGuiY(gui);

			if (prevGuiLeft != guiLeft || prevGuiTop != guiTop)
			{
				prevGuiLeft = guiLeft;
				prevGuiTop = guiTop;
			}

			boolean hasPotions = !gui.mc.player.getActivePotionEffects().isEmpty() || (gui instanceof GuiInventory && ((GuiInventory) gui).recipeBookGui.isVisible());

			if (!LMUtils.isNEILoaded() && FTBLibClientConfig.ACTION_BUTTONS_ON_TOP.getBoolean())
			{
				int x = 0;
				int y = 0;

				for (GuiButtonSidebar button : buttons)
				{
					button.x = 4 + x * 18;
					button.y = 4 + y * 18;

					if (hasPotions)
					{
						x++;

						if (x >= 15 || 4 + x * 18 >= gui.height)
						{
							x = 0;
							y++;
						}
					}
					else
					{
						x++;

						if (x == 4)
						{
							x = 0;
							y++;
						}
					}
				}
			}
			else
			{
				int buttonX = -17;
				int buttonY = 8;

				if (gui instanceof GuiContainerCreative)
				{
					buttonY = 6;
				}

				if (hasPotions)
				{
					buttonX -= 4;
					buttonY -= 26;
				}

				for (GuiButtonSidebar button : buttons)
				{
					if (hasPotions)
					{
						button.x = guiLeft + buttonX - (button.index % 8) * 18;
						button.y = guiTop + buttonY - (button.index / 8) * 18;
					}
					else
					{
						button.x = guiLeft + buttonX - (button.index / 8) * 18;
						button.y = guiTop + buttonY + (button.index % 8) * 18;
					}

				}
			}
		}

		@Override
		public void drawButton(Minecraft mc, int mx, int my, float partialTicks)
		{
			//if(creativeContainer != null && creativeContainer.getSelectedTabIndex() != CreativeTabs.tabInventory.getTabIndex())
			//	return;

			updateButtonPositions();

			zLevel = 0F;
			FontRenderer font = mc.fontRenderer;

			GlStateManager.enableBlend();
			GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GlStateManager.color(1F, 1F, 1F, 1F);

			for (GuiButtonSidebar b : buttons)
			{
				b.button.icon.draw(b.x, b.y, b.width, b.height, Color4I.NONE);

				if (mx >= b.x && my >= b.y && mx < b.x + b.width && my < b.y + b.height)
				{
					GuiHelper.drawBlankRect(b.x, b.y, b.width, b.height, Color4I.WHITE_A33);
				}
			}

			for (GuiButtonSidebar b : buttons)
			{
				if (b.renderMessages && MyTeamData.unreadMessages > 0)
				{
					String n = String.valueOf(MyTeamData.unreadMessages);
					int nw = font.getStringWidth(n);
					int width = 16;
					GuiHelper.drawBlankRect(b.x + width - nw, b.y - 4, nw + 1, 9, Color4I.LIGHT_RED);

					font.drawString(n, b.x + width - nw + 1, b.y - 3, 0xFFFFFFFF);
					GlStateManager.color(1F, 1F, 1F, 1F);
				}

				if (mx >= b.x && my >= b.y && mx < b.x + b.width && my < b.y + b.height)
				{
					GlStateManager.pushMatrix();
					double mx1 = mx - 4D;
					double my1 = my - 12D;

					int tw = font.getStringWidth(b.title);

					if (LMUtils.isNEILoaded() || !FTBLibClientConfig.ACTION_BUTTONS_ON_TOP.getBoolean())
					{
						mx1 -= tw + 8;
						my1 += 4;
					}

					if (mx1 < 4D)
					{
						mx1 = 4D;
					}
					if (my1 < 4D)
					{
						my1 = 4D;
					}

					GlStateManager.translate(mx1, my1, zLevel);

					GlStateManager.enableBlend();
					GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
					GuiHelper.drawBlankRect(-3, -2, tw + 6, 12, Color4I.DARK_GRAY);
					font.drawString(b.title, 0, 0, 0xFFFFFFFF);
					GlStateManager.color(1F, 1F, 1F, 1F);
					GlStateManager.popMatrix();
				}
			}

			GlStateManager.color(1F, 1F, 1F, 1F);
		}
	}
}