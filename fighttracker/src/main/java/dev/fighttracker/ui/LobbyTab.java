package dev.fighttracker.ui;

import dev.fighttracker.data.DataStore;
import dev.fighttracker.data.Stats;
import dev.fighttracker.util.Players;
import dev.fighttracker.util.TimeFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Everyone in the tab list with your kills / deaths / K/D against them. Sortable, scrollable. */
public class LobbyTab {
    private enum SortKey {
        NAME("Player"), KILLS("Kills"), DEATHS("Deaths"), RATIO("K/D"), LAST("Last fight");
        final String label;
        SortKey(String label) { this.label = label; }
    }

    private record Row(String name, UUID id, int kills, int deaths, long last) {
        double ratio() { return deaths == 0 ? kills : (double) kills / deaths; }
    }

    private static final int HEADER_H = 20;
    private static final int ROW_H = 14;
    private static final int FOOTER_H = 14;

    private static final int COLOR_GREEN = 0xFF55FF55;
    private static final int COLOR_RED = 0xFFFF5555;
    private static final int COLOR_YELLOW = 0xFFFFFF55;
    private static final int COLOR_GRAY = 0xFFAAAAAA;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    // Sort state survives the screen being resized / reopened within one session.
    private SortKey sortKey = SortKey.LAST;
    private boolean ascending = false;

    private final List<Row> rows = new ArrayList<>();
    private final Map<SortKey, ButtonWidget> headerButtons = new EnumMap<>(SortKey.class);
    private int x, y, w, h;
    private double scroll;
    private int seenRevision = -1;
    private long lastRefreshMs;

    public void init(int x, int y, int w, int h, Consumer<ButtonWidget> addWidget) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        headerButtons.clear();
        int[] cols = columns();
        SortKey[] keys = SortKey.values();
        for (int i = 0; i < keys.length; i++) {
            final SortKey key = keys[i];
            ButtonWidget button = ButtonWidget.builder(Text.literal(label(key)), b -> toggleSort(key))
                    .dimensions(x + cols[i], y, cols[i + 1] - cols[i] - 2, HEADER_H)
                    .build();
            headerButtons.put(key, button);
            addWidget.accept(button);
        }
        refresh(true);
    }

    /** Column left edges relative to the tab's x, plus the right edge as the last element. */
    private int[] columns() {
        return new int[]{0, (int) (w * 0.38), (int) (w * 0.50), (int) (w * 0.62), (int) (w * 0.74), w};
    }

    private String label(SortKey key) {
        return key == sortKey ? key.label + (ascending ? " ^" : " v") : key.label;
    }

    private void toggleSort(SortKey key) {
        if (key == sortKey) {
            ascending = !ascending;
        } else {
            sortKey = key;
            ascending = key == SortKey.NAME; // names read best A-Z, numbers highest first
        }
        for (Map.Entry<SortKey, ButtonWidget> e : headerButtons.entrySet()) {
            e.getValue().setMessage(Text.literal(label(e.getKey())));
        }
        sortRows();
    }

    private void refresh(boolean force) {
        long now = System.currentTimeMillis();
        int revision = DataStore.revision();
        if (!force && revision == seenRevision && now - lastRefreshMs < 1000) return;
        seenRevision = revision;
        lastRefreshMs = now;

        rows.clear();
        Map<String, Stats> stats = DataStore.statsByOpponent();
        MinecraftClient mc = MinecraftClient.getInstance();
        UUID me = mc.player != null ? mc.player.getUuid() : null;
        for (Players.Entry e : Players.online()) {
            if (e.id().equals(me)) continue;
            Stats s = stats.get(e.id().toString());
            rows.add(new Row(e.name(), e.id(),
                    s == null ? 0 : s.kills,
                    s == null ? 0 : s.deaths,
                    s == null ? 0 : s.lastFought));
        }
        sortRows();
    }

    private void sortRows() {
        Comparator<Row> c = switch (sortKey) {
            case NAME -> Comparator.comparing((Row r) -> r.name().toLowerCase(Locale.ROOT));
            case KILLS -> Comparator.comparingInt(Row::kills);
            case DEATHS -> Comparator.comparingInt(Row::deaths);
            case RATIO -> Comparator.comparingDouble(Row::ratio);
            case LAST -> Comparator.comparingLong(Row::last);
        };
        if (!ascending) c = c.reversed();
        rows.sort(c.thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER));
    }

    public void scroll(double verticalAmount) {
        scroll -= verticalAmount * ROW_H * 2;
    }

    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        refresh(false);

        int listTop = y + HEADER_H + 4;
        int listBottom = y + h - FOOTER_H;
        int listH = listBottom - listTop;
        double maxScroll = Math.max(0, rows.size() * ROW_H - listH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        if (rows.isEmpty()) {
            ctx.drawCenteredTextWithShadow(tr, "No other players in the tab list", x + w / 2, listTop + 12, COLOR_GRAY);
        }

        int[] cols = columns();
        long now = System.currentTimeMillis();
        ctx.enableScissor(x, listTop, x + w, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            int ry = listTop + i * ROW_H - (int) scroll;
            if (ry + ROW_H < listTop || ry > listBottom) continue;

            Row r = rows.get(i);
            boolean hovered = mouseX >= x && mouseX < x + w
                    && mouseY >= Math.max(ry, listTop) && mouseY < Math.min(ry + ROW_H, listBottom)
                    && mouseY >= ry && mouseY < ry + ROW_H;
            if (hovered) {
                ctx.fill(x, ry, x + w, ry + ROW_H, 0x30FFFFFF);
            } else if (i % 2 == 0) {
                ctx.fill(x, ry, x + w, ry + ROW_H, 0x14FFFFFF);
            }

            int ty = ry + 3;
            boolean fought = r.kills() > 0 || r.deaths() > 0;
            ctx.drawTextWithShadow(tr, tr.trimToWidth(r.name(), cols[1] - cols[0] - 8), x + cols[0] + 4, ty, COLOR_WHITE);
            ctx.drawTextWithShadow(tr, String.valueOf(r.kills()), x + cols[1] + 4, ty, r.kills() > 0 ? COLOR_GREEN : COLOR_GRAY);
            ctx.drawTextWithShadow(tr, String.valueOf(r.deaths()), x + cols[2] + 4, ty, r.deaths() > 0 ? COLOR_RED : COLOR_GRAY);

            String ratioText = fought ? String.format(Locale.ROOT, "%.2f", r.ratio()) : "-";
            int ratioColor = !fought ? COLOR_GRAY : r.ratio() > 1 ? COLOR_GREEN : r.ratio() < 1 ? COLOR_RED : COLOR_YELLOW;
            ctx.drawTextWithShadow(tr, ratioText, x + cols[3] + 4, ty, ratioColor);

            ctx.drawTextWithShadow(tr, TimeFormat.ago(r.last(), now), x + cols[4] + 4, ty, COLOR_GRAY);
        }
        ctx.disableScissor();

        // scrollbar
        if (maxScroll > 0) {
            int trackX = x + w - 3;
            int barH = Math.max(12, (int) (listH * (listH / (double) (rows.size() * ROW_H))));
            int barY = listTop + (int) ((listH - barH) * (scroll / maxScroll));
            ctx.fill(trackX, listTop, trackX + 3, listBottom, 0x30FFFFFF);
            ctx.fill(trackX, barY, trackX + 3, barY + barH, 0xA0FFFFFF);
        }

        ctx.drawTextWithShadow(tr, rows.size() + " other player" + (rows.size() == 1 ? "" : "s") + " in lobby",
                x + 4, y + h - FOOTER_H + 3, COLOR_GRAY);
    }
}
