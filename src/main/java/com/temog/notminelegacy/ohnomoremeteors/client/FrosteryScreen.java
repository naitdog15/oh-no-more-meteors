package com.temog.notminelegacy.ohnomoremeteors.client;

import com.temog.notminelegacy.ohnomoremeteors.frostery.FrosteryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Frostery screen. PLACEHOLDER art: it draws the game's own furnace GUI texture and tints the fuel gauge icy blue,
 * until a Frostery GUI texture is painted. No recipe book.
 */
public class FrosteryScreen extends AbstractContainerScreen<FrosteryMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/gui/container/furnace.png");

    public FrosteryScreen(FrosteryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
        if (this.menu.isLit()) {
            int lit = this.menu.getLitProgress();
            graphics.setColor(0.55F, 0.85F, 1.0F, 1.0F); // icy tint on the fuel gauge
            graphics.blit(TEXTURE, x + 56, y + 36 + 12 - lit, 176, 12 - lit, 14, lit + 1);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        int progress = this.menu.getBurnProgress();
        graphics.blit(TEXTURE, x + 79, y + 34, 176, 14, progress + 1, 16);
    }
}
