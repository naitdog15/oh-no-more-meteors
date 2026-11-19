package com.temog.notminelegacy.ohnomoremeteors.client;

import com.temog.notminelegacy.ohnomoremeteors.frostery.FrosteryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Frostery screen: draws the painted Frostery GUI texture (fuel gauge and progress arrow included). No recipe book.
 */
public class FrosteryScreen extends AbstractContainerScreen<FrosteryMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("ohnomoremeteors", "textures/gui/container/frostery.png");

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
            graphics.blit(TEXTURE, x + 56, y + 36 + 12 - lit, 176, 12 - lit, 14, lit + 1);
        }
        int progress = Mth.clamp(this.menu.getBurnProgress(), 0, 24);
        graphics.blit(TEXTURE, x + 79, y + 34, 176, 14, progress + 1, 16);
    }
}
