package toomda.oneslot.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    @Nullable
    protected Slot focusedSlot;

    @Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
    private void oneSlot$hideOtherSlots(DrawContext context, Slot slot, CallbackInfo ci) {
        ScreenHandler handler = ((HandledScreen<?>) (Object) this).getScreenHandler();

        if (!(handler instanceof PlayerScreenHandler)) {
            return;
        }

        if (slot.inventory instanceof PlayerInventory) {
            if (slot.getIndex() != 4) {
                ci.cancel();
            }
        }
    }


    @Inject(
            method = "renderMain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawSlotHighlightBack(Lnet/minecraft/client/gui/DrawContext;)V"
            )
    )
    private void oneSlot$fixHighlight(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (this.focusedSlot == null) {
            return;
        }

        if (this.focusedSlot.inventory instanceof PlayerInventory) {
            if (this.focusedSlot.getIndex() != 4) {
                this.focusedSlot = null;
            }

        }
    }
}
