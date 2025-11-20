package toomda.oneslot.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    @Shadow
    public DefaultedList<Slot> slots;

    // Vanilla-Methode aus ScreenHandler, die wir wiederverwenden
    @Shadow
    protected abstract boolean insertItem(ItemStack stack, int startIndex, int endIndex, boolean fromLast);

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void oneSlot$lockInventory(int slotIndex, int button, SlotActionType actionType,
                                       PlayerEntity player, CallbackInfo ci) {

        // Klick außerhalb des Inventars (z.B. Drop außerhalb) erlauben
        if (slotIndex == ScreenHandler.EMPTY_SPACE_SLOT_INDEX) {
            return;
        }

        // Sicherheitscheck: gültiger Slot?
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return;
        }

        Slot slot = this.slots.get(slotIndex);

        // Nur PlayerInventory-Slots beschränken
        if (slot.inventory instanceof PlayerInventory) {
            int invIndex = slot.getIndex();

            // *** Unser EINZIG erlaubter Slot: Inventar-Index 4 ***
            if (invIndex == 4) {

                // Spezialbehandlung nur für Shift-Klick (= QUICK_MOVE)
                if (actionType == SlotActionType.QUICK_MOVE) {
                    ItemStack stack = slot.getStack();
                    if (stack.isEmpty()) {
                        ci.cancel();
                        return;
                    }

                    // 1) PlayerScreenHandler: nur in 2x2-Crafting
                    if ((Object) this instanceof PlayerScreenHandler) {
                        boolean moved = this.insertItem(
                                stack,
                                PlayerScreenHandler.CRAFTING_INPUT_START,
                                PlayerScreenHandler.CRAFTING_INPUT_END,
                                false
                        );

                        // Wenn nix ins Crafting passt → gar nichts machen
                        if (!moved) {
                            ci.cancel();
                            return;
                        }

                        if (stack.isEmpty()) {
                            slot.setStack(ItemStack.EMPTY);
                        } else {
                            slot.markDirty();
                        }

                        ci.cancel();
                        return;
                    }

                    // 2) Alle anderen ScreenHandler (Furnace, Stonecutter, Kisten, …):
                    //    Nur in Container-Slots (alles, was NICHT PlayerInventory ist)

                    int containerStart = 0;
                    int containerEnd = 0;
                    boolean foundPlayerStart = false;

                    for (int i = 0; i < this.slots.size(); i++) {
                        Slot s = this.slots.get(i);
                        if (s.inventory instanceof PlayerInventory) {
                            containerEnd = i;      // erster Player-Slot → Ende der Container-Slots
                            foundPlayerStart = true;
                            break;
                        }
                    }

                    if (!foundPlayerStart) {
                        // Kein Container-Bereich gefunden (sollte eigentlich nie vorkommen),
                        // aber zur Sicherheit: einfach nichts tun.
                        ci.cancel();
                        return;
                    }

                    // Nur Container-Bereich [0, containerEnd) verwenden
                    boolean moved = this.insertItem(
                            stack,
                            containerStart,
                            containerEnd,
                            false
                    );

                    if (!moved) {
                        // Nix passt → gar nichts machen
                        ci.cancel();
                        return;
                    }

                    if (stack.isEmpty()) {
                        slot.setStack(ItemStack.EMPTY);
                    } else {
                        slot.markDirty();
                    }

                    ci.cancel();
                    return;
                }

                // alle anderen Aktionen (normaler Klick, Rechtsklick, etc.) auf Slot 4
                // → Vanilla verarbeiten lassen
                return;
            }

            // Alle anderen PlayerInventory-Slots komplett blocken
            ci.cancel();
        }
    }

    @Inject(
            method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void oneSlot$redirectInsertToSinglePlayerSlot(ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) {
            return; // Vanilla kann mit leerem Stack selbst umgehen
        }

        List<Slot> slots = this.slots;

        // Finde den Slot in diesem ScreenHandler, der:
        //  - zu einem PlayerInventory gehört
        //  - Inventar-Index 4 (Hotbar-Mitte) hat
        Slot targetSlot = null;

        for (int i = startIndex; i < endIndex && i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.inventory instanceof PlayerInventory && slot.getIndex() == 4) {
                targetSlot = slot;
                break;
            }
        }

        // Wenn in diesem insertItem-Aufruf gar kein PlayerInventory-Slot steckt,
        // dann ist das vermutlich "Container-Bereich" → Vanilla machen lassen.
        if (targetSlot == null) {
            return;
        }

        ItemStack existing = targetSlot.getStack();

        if (!targetSlot.canInsert(stack)) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        int maxCount = Math.min(stack.getMaxCount(), targetSlot.getMaxItemCount(stack));
        boolean changed = false;

        if (existing.isEmpty()) {
            int move = Math.min(maxCount, stack.getCount());
            if (move > 0) {
                ItemStack newStack = stack.copyWithCount(move);
                targetSlot.setStack(newStack);
                stack.decrement(move);
                targetSlot.markDirty();
                changed = true;
            }
        } else {
            if (!ItemStack.areItemsAndComponentsEqual(existing, stack) || !existing.isStackable()) {
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }
            int canMove = maxCount - existing.getCount();
            if (canMove > 0) {
                int move = Math.min(canMove, stack.getCount());
                if (move > 0) {
                    existing.increment(move);
                    stack.decrement(move);
                    targetSlot.markDirty();
                    changed = true;
                }
            }
        }

        cir.setReturnValue(changed);
        cir.cancel(); // Vanilla insertItem nicht mehr ausführen
    }
}
