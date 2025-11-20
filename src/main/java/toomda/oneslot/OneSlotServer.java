package toomda.oneslot;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;

public final class OneSlotServer {

    private static final int ONE_ALLOWED_MAIN_SLOT = 4; // mittlerer Hotbar-Slot

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

        // 1) Nur main[4] behalten
        DefaultedList<ItemStack> main = inv.getMainStacks();
        for (int i = 0; i < main.size(); i++) {
            if (i == ONE_ALLOWED_MAIN_SLOT) continue;
            ItemStack stack = main.get(i);
            if (!stack.isEmpty()) {
                player.dropItem(stack, true, false);
                main.set(i, ItemStack.EMPTY);
            }
        }

        // 2) Offhand "reparieren" statt wegwerfen
        int offhandSlotIndex = PlayerInventory.OFF_HAND_SLOT; // = 40
        ItemStack offhand = inv.getStack(offhandSlotIndex);
        if (!offhand.isEmpty()) {
            ItemStack mainSlot = main.get(ONE_ALLOWED_MAIN_SLOT);

            // a) Wenn Slot 4 leer ist → Item einfach zurück nach Slot 4 packen
            if (mainSlot.isEmpty()) {
                main.set(ONE_ALLOWED_MAIN_SLOT, offhand);
            } else if (ItemStack.areItemsAndComponentsEqual(mainSlot, offhand) && mainSlot.isStackable()) {
                // b) Wenn gleicher Stacktyp → in Slot 4 reinstacken
                int maxCount = Math.min(mainSlot.getMaxCount(), inv.getMaxCount(mainSlot));
                int canMove = maxCount - mainSlot.getCount();
                if (canMove > 0) {
                    int move = Math.min(canMove, offhand.getCount());
                    mainSlot.increment(move);
                    offhand.decrement(move);
                }

                // Falls noch Rest übrig bleibt (sehr unwahrscheinlich in deinem Setup)
                if (!offhand.isEmpty()) {
                    player.dropItem(offhand, true, false);
                }
            } else {
                // c) Irgendwas Komisches (anderes Item) → zur Sicherheit droppen
                player.dropItem(offhand, true, false);
            }

            // Offhand immer leeren
            inv.setStack(offhandSlotIndex, ItemStack.EMPTY);
        }

        // 3) Rüstung leeren
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack armor = player.getEquippedStack(slot);
                if (!armor.isEmpty()) {
                    player.dropItem(armor, true, false);
                    player.equipStack(slot, ItemStack.EMPTY);
                }
            }
        }

        // 4) Ausgewählten Slot immer auf 4 locken
        if (inv.getSelectedSlot() != ONE_ALLOWED_MAIN_SLOT) {
            inv.setSelectedSlot(ONE_ALLOWED_MAIN_SLOT);
        }
    }
}
