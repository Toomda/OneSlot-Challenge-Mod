package toomda.oneslot;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;

public final class OneSlotServer {

    private OneSlotServer() {}

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            for (ServerPlayerEntity player : world.getPlayers()) {
                enforceOneSlot(player);
            }
        });
    }

    private static void enforceOneSlot(ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();

        DefaultedList<ItemStack> main = inv.getMainStacks();
        for (int i = 0; i < main.size(); i++) {
            if (i == 4) continue;
            ItemStack stack = main.get(i);
            if (!stack.isEmpty()) {
                player.dropItem(stack, true, false);
                main.set(i, ItemStack.EMPTY);
            }
        }

        int offhandSlotIndex = PlayerInventory.OFF_HAND_SLOT;
        ItemStack offhand = inv.getStack(offhandSlotIndex);
        if (!offhand.isEmpty()) {
            ItemStack mainSlot = main.get(4);
            if (mainSlot.isEmpty()) {
                main.set(4, offhand);
            } else if (ItemStack.areItemsAndComponentsEqual(mainSlot, offhand) && mainSlot.isStackable()) {
                int maxCount = Math.min(mainSlot.getMaxCount(), inv.getMaxCount(mainSlot));
                int canMove = maxCount - mainSlot.getCount();
                if (canMove > 0) {
                    int move = Math.min(canMove, offhand.getCount());
                    mainSlot.increment(move);
                    offhand.decrement(move);
                }

                if (!offhand.isEmpty()) {
                    player.dropItem(offhand, true, false);
                }
            } else {
                player.dropItem(offhand, true, false);
            }

            inv.setStack(offhandSlotIndex, ItemStack.EMPTY);
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack armor = player.getEquippedStack(slot);
                if (!armor.isEmpty()) {
                    player.dropItem(armor, true, false);
                    player.equipStack(slot, ItemStack.EMPTY);
                }
            }
        }

        if (inv.getSelectedSlot() != 4) {
            inv.setSelectedSlot(4);
        }
    }
}
