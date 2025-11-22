package toomda.oneslot.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
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

    @Shadow
    protected abstract boolean insertItem(ItemStack stack, int startIndex, int endIndex, boolean fromLast);

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void oneSlot$lockInventory(int slotIndex, int button, SlotActionType actionType,
                                       PlayerEntity player, CallbackInfo ci) {
        if (slotIndex == ScreenHandler.EMPTY_SPACE_SLOT_INDEX) {
            return;
        }
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return;
        }

        Slot slot = this.slots.get(slotIndex);
        if (slot.inventory instanceof PlayerInventory) {
            int invIndex = slot.getIndex();

            if (invIndex == 4) {
                if (actionType == SlotActionType.QUICK_MOVE) {
                    ItemStack stack = slot.getStack();
                    if (stack.isEmpty()) {
                        ci.cancel();
                        return;
                    }

                    if ((Object) this instanceof PlayerScreenHandler) {
                        boolean moved = this.insertItem(
                                stack,
                                PlayerScreenHandler.CRAFTING_INPUT_START,
                                PlayerScreenHandler.CRAFTING_INPUT_END,
                                false
                        );

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

                    int containerStart = 0;
                    int containerEnd = 0;
                    boolean foundPlayerStart = false;

                    for (int i = 0; i < this.slots.size(); i++) {
                        Slot s = this.slots.get(i);
                        if (s.inventory instanceof PlayerInventory) {
                            containerEnd = i;
                            foundPlayerStart = true;
                            break;
                        }
                    }

                    if (!foundPlayerStart) {
                        ci.cancel();
                        return;
                    }
                    boolean moved = this.insertItem(
                            stack,
                            containerStart,
                            containerEnd,
                            false
                    );

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

                return;
            }

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
            return;
        }

        List<Slot> slots = this.slots;

        Slot targetSlot = null;

        for (int i = startIndex; i < endIndex && i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.inventory instanceof PlayerInventory && slot.getIndex() == 4) {
                targetSlot = slot;
                break;
            }
        }

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
        cir.cancel();
    }

    @Inject(
            method = "dropInventory(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/inventory/Inventory;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void oneSlot$handleDropToSingleSlot(PlayerEntity player,
                                                Inventory inventory,
                                                CallbackInfo ci) {
        PlayerInventory playerInv = player.getInventory();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;

            ItemStack remaining = stack.copy();
            inventory.setStack(i, ItemStack.EMPTY);

            ItemStack mainStack = playerInv.getStack(4);

            if (mainStack.isEmpty()) {
                playerInv.setStack(4, remaining);
                remaining = ItemStack.EMPTY;
            } else if (ItemStack.areItemsAndComponentsEqual(mainStack, remaining)
                    && mainStack.isStackable()) {

                int maxCount = Math.min(mainStack.getMaxCount(), playerInv.getMaxCount(mainStack));
                int canMove = maxCount - mainStack.getCount();
                if (canMove > 0) {
                    int move = Math.min(canMove, remaining.getCount());
                    mainStack.increment(move);
                    remaining.decrement(move);
                }
            }

            if (!remaining.isEmpty()) {
                player.dropItem(remaining, false, false);
            }
        }

        ci.cancel();
    }

}
