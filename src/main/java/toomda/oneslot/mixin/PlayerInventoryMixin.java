package toomda.oneslot.mixin;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {

    @Shadow
    private int selectedSlot;

    @Inject(method = "setSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void oneSlot$lockSelectedSlot(int slot, CallbackInfo ci) {
        this.selectedSlot = 4;
        ci.cancel();
    }

    @Inject(
            method = "insertStack(ILnet/minecraft/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void oneSlot$forceInsertIntoMiddleSlot(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }

        PlayerInventory inv = (PlayerInventory) (Object) this;
        int targetIndex = 4;

        ItemStack existing = inv.getStack(targetIndex);
        int maxCount = Math.min(stack.getMaxCount(), inv.getMaxCount(stack));

        boolean moved = false;

        if (existing.isEmpty()) {
            int move = Math.min(maxCount, stack.getCount());
            if (move > 0) {
                ItemStack newStack = stack.copyWithCount(move);
                inv.setStack(targetIndex, newStack);
                stack.decrement(move);
                newStack.setBobbingAnimationTime(5);
                moved = true;
            }
        } else if (ItemStack.areItemsAndComponentsEqual(existing, stack) && existing.isStackable()) {
            int canMove = maxCount - existing.getCount();
            if (canMove > 0) {
                int move = Math.min(canMove, stack.getCount());
                if (move > 0) {
                    existing.increment(move);
                    stack.decrement(move);
                    existing.setBobbingAnimationTime(5);
                    moved = true;
                }
            }
        }

        cir.setReturnValue(moved);
        cir.cancel();
    }
}
