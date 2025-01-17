package rootenginear.sortchest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiContainer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTooltip;
import net.minecraft.client.player.controller.PlayerController;
import net.minecraft.core.InventoryAction;
import net.minecraft.core.entity.player.EntityPlayer;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.lang.I18n;
import net.minecraft.core.player.inventory.Container;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rootenginear.sortchest.gui.GuiSortChestButton;
import rootenginear.sortchest.interfaces.ISortChestSettings;
import rootenginear.sortchest.utils.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(value = {GuiContainer.class}, remap = false)
public class GuiContainerMixin {
	@Shadow
	public Container inventorySlots;

	@Shadow
	public int xSize;

	@Shadow
	public int ySize;

	@Shadow
	GuiTooltip guiTooltip;

	@Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
	private void doSort(char c, int _i, int mouseX, int mouseY, CallbackInfo ci) {
		if (Utils.isNotChest(this)) return;

		ISortChestSettings gameSettings = ((ISortChestSettings) Minecraft.getMinecraft(Minecraft.class).gameSettings);
		char keySort = gameSettings.bta_rootenginear_mods$getKeySort().getKeyName().charAt(0);
		char keyRefill = gameSettings.bta_rootenginear_mods$getKeyRefill().getKeyName().charAt(0);
		char keyFill = gameSettings.bta_rootenginear_mods$getKeyFill().getKeyName().charAt(0);
		char keyDump = gameSettings.bta_rootenginear_mods$getKeyDump().getKeyName().charAt(0);

		char key = Character.toUpperCase(c);
		if (key != keySort && key != keyRefill && key != keyFill && key != keyDump) return;

		Minecraft mc = Minecraft.getMinecraft(Minecraft.class);
		PlayerController playerController = mc.playerController;
		EntityPlayer entityPlayer = mc.thePlayer;
		int windowId = inventorySlots.windowId;

		if (key == keyDump) {
			dumpItemFromChest(playerController, entityPlayer, windowId);

			ci.cancel();
			return;
		}

		int countInvSlots = this.inventorySlots.inventorySlots.size() - 36;

		if (key == keyFill) {
			dumpItemToChest(playerController, entityPlayer, windowId, countInvSlots);

			ci.cancel();
			return;
		}

		if (key == keyRefill) {
			refillChest(playerController, entityPlayer, windowId, countInvSlots, inventorySlots);

			ci.cancel();
			return;
		}

		mergeItemsInChest(playerController, entityPlayer, windowId, countInvSlots, inventorySlots);
		sortItemsInChest(playerController, entityPlayer, windowId, countInvSlots, inventorySlots);

		ci.cancel();
	}

	@Unique
	private void sortItemsInChest(PlayerController playerController, EntityPlayer entityPlayer, int windowId, int countInvSlots, Container inventorySlots) {
		List<ItemStack> inv = new ArrayList<>(countInvSlots);
		for (int i = 0; i < countInvSlots; i++) {
			inv.add(inventorySlots.getSlot(i).getStack());
		}

		List<ItemStack> sorted = inv.stream().sorted(compareItemStacks()).collect(Collectors.toList());

		for (int i = 0; i < countInvSlots; i++) {
			ItemStack sortedItem = sorted.get(i);
			if (sortedItem == null) break;

			ItemStack invItem = inv.get(i);
			if (sortedItem == invItem) continue;

			int x = inv.indexOf(sortedItem);
			inv.set(i, inv.get(x));
			inv.set(x, invItem);

			swap(playerController, entityPlayer, windowId, x, i);
		}
	}

	@Unique
	@NotNull
	private static Comparator<ItemStack> compareItemStacks() {
		return (a, z) -> {
			if (a == null && z == null) return 0;
			if (a == null) return 1;
			if (z == null) return -1;

			int aId = a.itemID;
			int zId = z.itemID;
			if (aId != zId) return aId - zId;

			int aMeta = a.getMetadata();
			int zMeta = z.getMetadata();
			if (aMeta != zMeta) return aMeta - zMeta;

			return z.stackSize - a.stackSize;
		};
	}

	@Unique
	private static void swap(PlayerController playerController, EntityPlayer entityPlayer, int windowId, int x, int i) {
		playerController.handleInventoryMouseClick(windowId, InventoryAction.CLICK_LEFT, new int[]{x, 0}, entityPlayer);
		playerController.handleInventoryMouseClick(windowId, InventoryAction.CLICK_LEFT, new int[]{i, 0}, entityPlayer);
		playerController.handleInventoryMouseClick(windowId, InventoryAction.CLICK_LEFT, new int[]{x, 0}, entityPlayer);
	}

	@Unique
	private void mergeItemsInChest(PlayerController playerController, EntityPlayer entityPlayer, int windowId, int countInvSlots, Container inventorySlots) {
		List<String> invData = new ArrayList<>(countInvSlots);
		for (int i = 0; i < countInvSlots; i++) {
			ItemStack item = inventorySlots.getSlot(i).getStack();
			invData.add(item != null ? item.itemID + ":" + item.getMetadata() : null);
		}

		for (int firstSlotIndex = 0; firstSlotIndex < countInvSlots; firstSlotIndex++) {
			if (invData.get(firstSlotIndex) == null) continue;

			String itemData = invData.get(firstSlotIndex);
			invData.set(firstSlotIndex, null);

			int nextSlot = invData.indexOf(itemData);
			if (nextSlot == -1) continue;

			playerController.handleInventoryMouseClick(windowId, InventoryAction.CLICK_LEFT, new int[]{firstSlotIndex, 0}, entityPlayer);

			do {
				playerController.handleInventoryMouseClick(windowId, InventoryAction.CLICK_LEFT, new int[]{nextSlot, 0}, entityPlayer);
				playerController.handleInventoryMouseClick(windowId, InventoryAction.CLICK_LEFT, new int[]{nextSlot, 0}, entityPlayer);
				invData.set(nextSlot, null);
			} while ((nextSlot = invData.indexOf(itemData)) != -1);

			playerController.handleInventoryMouseClick(windowId, InventoryAction.CLICK_LEFT, new int[]{firstSlotIndex, 0}, entityPlayer);
		}
	}

	@Unique
	private void dumpItemFromChest(PlayerController playerController, EntityPlayer entityPlayer, int windowId) {
		playerController.handleInventoryMouseClick(windowId, InventoryAction.MOVE_ALL, new int[]{0, 0}, entityPlayer);
	}

	@Unique
	private void dumpItemToChest(PlayerController playerController, EntityPlayer entityPlayer, int windowId, int countInvSlots) {
		// Dump inventory (not hotbar) content
		playerController.handleInventoryMouseClick(windowId, InventoryAction.MOVE_ALL, new int[]{countInvSlots, 0}, entityPlayer);

		// Dump hotbar content
		playerController.handleInventoryMouseClick(windowId, InventoryAction.MOVE_ALL, new int[]{countInvSlots + (9 * 3), 0}, entityPlayer);
	}

	@Unique
	private void refillChest(PlayerController playerController, EntityPlayer entityPlayer, int windowId, int countInvSlots, Container inventorySlots) {
		List<String> chestItems = new ArrayList<>(countInvSlots);
		for (int i = 0; i < countInvSlots; i++) {
			ItemStack item = inventorySlots.getSlot(i).getStack();
			if (item == null) continue;

			String itemStr = item.itemID + ":" + item.getMetadata();
			if (!chestItems.contains(itemStr)) chestItems.add(itemStr);
		}
		for (int i = countInvSlots; i < countInvSlots + (9 * 4); i++) {
			ItemStack invItem = inventorySlots.getSlot(i).getStack();
			if (invItem == null) continue;

			String invItemStr = invItem.itemID + ":" + invItem.getMetadata();
			if (!chestItems.contains(invItemStr)) continue;

			playerController.handleInventoryMouseClick(windowId, InventoryAction.MOVE_SIMILAR, new int[]{i, 0}, entityPlayer);
			chestItems.remove(invItemStr);
		}
	}

	@SuppressWarnings("UnreachableCode")
	@Inject(method = "init", at = @At("TAIL"))
	private void addChestButtons(CallbackInfo ci) {
		if (Utils.isNotChest(this)) return;

		GuiScreen screenThis = (GuiScreen) (Object) this;

		int centerX = (screenThis.width - xSize) / 2;
		int centerY = (screenThis.height - ySize) / 2;

		ISortChestSettings gameSettings = ((ISortChestSettings) Minecraft.getMinecraft(Minecraft.class).gameSettings);
		String keySort = gameSettings.bta_rootenginear_mods$getKeySort().getKeyName();
		String keyRefill = gameSettings.bta_rootenginear_mods$getKeyRefill().getKeyName();
		String keyFill = gameSettings.bta_rootenginear_mods$getKeyFill().getKeyName();
		String keyDump = gameSettings.bta_rootenginear_mods$getKeyDump().getKeyName();

		I18n i18n = I18n.getInstance();

		screenThis.controlList.clear();

		// Sort Btn
		screenThis.controlList.add(new GuiSortChestButton(0, centerX + xSize - 8 - 12 - 12 - 2, centerY + 4, 12, 12, "⇵", 6, i18n.translateKey("sortchest.sort") + " [" + keySort + "]"));
		// Refill Btn
		screenThis.controlList.add(new GuiSortChestButton(1, centerX + xSize - 8 - 12, centerY + 4, 12, 12, "∑", 6, i18n.translateKey("sortchest.refill") + " [" + keyRefill + "]"));
		// Fill Btn
		screenThis.controlList.add(new GuiSortChestButton(2, centerX + xSize - 8 - 12 - 12 - 2, centerY + ySize - 96 - 1, 12, 12, "⊼", 6, i18n.translateKey("sortchest.fill") + " [" + keyFill + "]"));
		// Dump Btn
		screenThis.controlList.add(new GuiSortChestButton(3, centerX + xSize - 8 - 12, centerY + ySize - 96 - 1, 12, 12, "⊻", 6, i18n.translateKey("sortchest.dump") + " [" + keyDump + "]"));
	}

	@SuppressWarnings("UnreachableCode")
	@Inject(method = "drawScreen", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V"))
	private void renderChestButtonTooltip(int x, int y, float renderPartialTicks, CallbackInfo ci) {
		if (Utils.isNotChest(this)) return;

		GuiScreen screenThis = (GuiScreen) (Object) this;
		for (int i = 0; i < screenThis.controlList.size(); ++i) {
			GuiSortChestButton button = (GuiSortChestButton) screenThis.controlList.get(i);
			if (!button.isHovered(x, y)) continue;
			guiTooltip.render(button.getTooltipText(), x, y, 8, -8);
		}
	}
}
