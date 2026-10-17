package dev.fighttracker.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Main menu. Stage 1 only has the Lobby tab; the other tabs are shown greyed out as placeholders
 * and get switched on in later stages by setting ready = true and rendering their content here.
 */
public class FightTrackerScreen extends Screen {
    private enum Tab {
        LOBBY("Lobby", true),
        SEARCH("Search", false),
        GRAPHS("Graphs", false),
        NAMES("Name History", false),
        OVERVIEW("Overview", false);

        final String label;
        final boolean ready;

        Tab(String label, boolean ready) {
            this.label = label;
            this.ready = ready;
        }
    }

    private static final int MARGIN = 10;
    private static final int TAB_BAR_H = 20;

    // Static so reopening the menu keeps your last tab and the Lobby sort order.
    private static Tab currentTab = Tab.LOBBY;
    private static final LobbyTab lobby = new LobbyTab();

    private int contentX, contentY, contentW, contentH;

    public FightTrackerScreen() {
        super(Text.literal("Fight Tracker"));
    }

    @Override
    protected void init() {
        Tab[] tabs = Tab.values();
        int tabW = Math.min(100, (width - MARGIN * 2) / tabs.length);
        for (int i = 0; i < tabs.length; i++) {
            final Tab tab = tabs[i];
            ButtonWidget button = ButtonWidget.builder(
                            Text.literal(tab.ready ? tab.label : tab.label + " (soon)"),
                            b -> currentTab = tab)
                    .dimensions(MARGIN + i * tabW, MARGIN, tabW - 2, TAB_BAR_H)
                    .build();
            button.active = tab.ready;
            addDrawableChild(button);
        }

        contentX = MARGIN;
        contentY = MARGIN + TAB_BAR_H + 10;
        contentW = width - MARGIN * 2;
        contentH = height - contentY - MARGIN;

        if (currentTab == Tab.LOBBY) {
            lobby.init(contentX, contentY, contentW, contentH, b -> addDrawableChild(b));
        }
    }

    /** Own background instead of the default blur, so we never trigger the one-blur-per-frame rule. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0101012);
        context.fill(contentX - 4, contentY - 6, contentX + contentW + 4, contentY + contentH + 4, 0xE0181A1F);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // background + buttons
        if (currentTab == Tab.LOBBY) {
            lobby.render(context, textRenderer, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTab == Tab.LOBBY) {
            lobby.scroll(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false; // keep the world running so the lobby list stays live
    }
}
