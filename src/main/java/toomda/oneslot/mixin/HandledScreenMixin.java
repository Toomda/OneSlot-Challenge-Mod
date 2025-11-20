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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow protected ScreenHandler handler;

    @Shadow
    @Nullable
    protected Slot focusedSlot;

    @Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
    private void oneSlot$hideOtherSlots(DrawContext context, Slot slot, CallbackInfo ci) {
        ScreenHandler handler = ((HandledScreen<?>) (Object) this).getScreenHandler();

        // Nur im Player-Inventar-Screen filtern
        if (!(handler instanceof PlayerScreenHandler)) {
            return;
        }

        // Nur Slots, deren Inventory das PlayerInventory ist, interessieren uns
        if (slot.inventory instanceof PlayerInventory) {
            // Nur Inventar-Index 4 (Hotbar-Mitte) sichtbar lassen
            if (slot.getIndex() != 4) {
                ci.cancel(); // nicht zeichnen
            }
        }
        // Crafting-Grid, Result, etc. = NICHT PlayerInventory → bleiben sichtbar
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

        // Für ALLE ScreenHandler: sobald es ein PlayerInventory-Slot ist ...
        if (this.focusedSlot.inventory instanceof PlayerInventory) {
            // ... darf nur Inventar-Index 4 überhaupt fokussierbar sein
            if (this.focusedSlot.getIndex() != 4) {
                this.focusedSlot = null; // -> kein Highlight, keine Tooltipps
            }

        }
    }
}
