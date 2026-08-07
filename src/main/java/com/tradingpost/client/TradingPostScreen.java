package com.tradingpost.client;

import com.tradingpost.TradingPostMod;
import com.tradingpost.market.MarketPricing;
import com.tradingpost.menu.TradingPostMenu;
import com.tradingpost.network.C2SBuyPacket;
import com.tradingpost.network.C2SSellPacket;
import com.tradingpost.network.MarketNetworking;
import com.tradingpost.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The market screen. One scrolling list of every colony's goods with a search box and colony
 * filters; selecting a row opens a trade panel with a quantity slider + number box and a live,
 * exact buy/sell quote. It renders {@link TradingPostMenu}'s snapshots and fires buy/sell packets -
 * it never decides a price on its own; it only mirrors the server's pricing function
 * ({@link MarketPricing}) using the parameters the server sent, so the projected total matches
 * exactly what the server will charge.
 */
public class TradingPostScreen extends AbstractContainerScreen<TradingPostMenu> {

    /** Themed panel art. Drawn into the top-left of a square atlas; see scripts/gen_textures.py. */
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(TradingPostMod.MODID, "textures/gui/trading_post_background.png");
    private static final int BG_TEX_SIZE = 512;

    /**
     * Currency suffix on every price. Kept to a single letter rather than the full word: the
     * row-list price column is right-aligned in a narrow gutter, and the Pay/Earn lines start
     * ~150px into a 326px panel, so "4096 emeralds for 4096" would run off the edge on a large
     * order. The header already reads "Emeralds: N", so the unit is never ambiguous.
     */
    private static final String CURRENCY = "e";

    /**
     * Lot size every listed price is quoted for. 64 keeps the tier ladder in whole emeralds and
     * matches how players actually buy - see {@link #lotPrice} for why per-unit prices were
     * unreadable. Items that stack smaller than this still get a valid quote; it just spans more
     * than one stack of them.
     */
    private static final int PRICE_QUANTITY = 64;

    // Layout, relative to the GUI's top-left corner (leftPos, topPos).
    private static final int LIST_X = 8;
    private static final int FILTERS_Y = 36;
    private static final int LIST_W = 304;
    private static final int ROW_H = 20;
    private static final int VISIBLE_ROWS = 6;
    private static final int LIST_H = ROW_H * VISIBLE_ROWS;
    private static final int SCROLLBAR_X = LIST_X + LIST_W;
    private static final int SCROLLBAR_W = 6;
    private static final int SLIDER_W = 176;
    private static final int SLIDER_H = 16;

    // Trade panel vertical rhythm (relative to panelY): header(0), info(10), slider row(24),
    // breakdown(42), buttons+quote(54). Text is drawn after widgets each frame, so these need real
    // clearance between them or a text line paints directly over the slider/buttons below it.
    private static final int PANEL_INFO_Y = 10;
    private static final int PANEL_SLIDER_Y = 24;
    private static final int PANEL_BREAKDOWN_Y = 42;
    private static final int PANEL_BUTTONS_Y = 54;

    // Computed in buildWidgets() each time, since the colony filter row(s) can wrap depending on
    // how many colonies exist and how wide their names are - everything below the filters shifts
    // down to make room instead of overlapping them.
    private int listY;
    private int panelY;

    private EditBox searchBox;
    private EditBox quantityBox;
    private QuantitySlider slider;
    private Button buyButton;
    private Button sellButton;

    private String colonyFilter = null; // null = All
    private ResourceLocation selectedItemId;
    private String selectedColonyId;
    private int quantity = 1;
    private int scrollOffset = 0;
    private boolean updatingQuantity = false;

    public TradingPostScreen(TradingPostMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 326;
        this.imageHeight = 316;
    }

    @Override
    protected void init() {
        super.init();
        buildWidgets();
        // Select the first item on open so the trade controls are immediately live (the slider and
        // quantity box do nothing until something is selected).
        if (selectedItemId == null) {
            List<TradingPostMenu.Row> rows = menu.getAllRows();
            if (!rows.isEmpty()) {
                TradingPostMenu.Row first = rows.get(0);
                selectedColonyId = first.colonyId();
                selectedItemId = first.entry().itemId();
                setQuantity(Math.min(64, maxTradable()));
            }
        }
    }

    private void buildWidgets() {
        clearWidgets();

        searchBox = new EditBox(font, leftPos + LIST_X, topPos + 18, 150, 14, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search items..."));
        searchBox.setMaxLength(50);
        searchBox.setResponder(s -> scrollOffset = 0);
        addRenderableWidget(searchBox);

        // Colony filter buttons: "All" plus one per colony, wrapping onto additional rows as
        // needed instead of running off the edge of the screen. Everything below (the list, the
        // trade panel) shifts down to whatever height the filters end up using.
        List<String> labels = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        labels.add("All");
        ids.add(null);
        for (MarketNetworking.ColonySnapshot colony : menu.getColonies()) {
            labels.add(colony.displayName());
            ids.add(colony.id());
        }

        int rowLeft = leftPos + LIST_X;
        int rowRight = leftPos + LIST_X + LIST_W;
        int cursorX = rowLeft;
        int cursorY = topPos + FILTERS_Y;
        int rows = 1;
        for (int i = 0; i < labels.size(); i++) {
            int width = filterButtonWidth(labels.get(i));
            if (cursorX != rowLeft && cursorX + width > rowRight) {
                rows++;
                cursorY += 18;
                cursorX = rowLeft;
            }
            cursorX += addFilterButton(cursorX, cursorY, labels.get(i), ids.get(i));
        }

        listY = FILTERS_Y + rows * 18 + 6;
        panelY = listY + LIST_H + 8;

        slider = new QuantitySlider(leftPos + LIST_X, topPos + panelY + PANEL_SLIDER_Y, SLIDER_W, SLIDER_H);
        addRenderableWidget(slider);

        quantityBox = new EditBox(font, leftPos + LIST_X + 182, topPos + panelY + PANEL_SLIDER_Y, 48, 16, Component.literal("Qty"));
        quantityBox.setMaxLength(7);
        quantityBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        quantityBox.setValue(String.valueOf(quantity));
        quantityBox.setResponder(s -> {
            if (updatingQuantity) {
                return;
            }
            int parsed = s.isEmpty() ? 1 : parseIntSafe(s, 1);
            setQuantity(parsed);
        });
        addRenderableWidget(quantityBox);

        buyButton = Button.builder(Component.literal("Buy"), b -> sendTrade(true))
                .bounds(leftPos + LIST_X, topPos + panelY + PANEL_BUTTONS_Y, 56, 18).build();
        addRenderableWidget(buyButton);

        sellButton = Button.builder(Component.literal("Sell"), b -> sendTrade(false))
                .bounds(leftPos + LIST_X + 62, topPos + panelY + PANEL_BUTTONS_Y, 56, 18).build();
        addRenderableWidget(sellButton);

        setQuantity(quantity);
    }

    private int filterButtonWidth(String label) {
        return Math.max(30, font.width(label) + 10);
    }

    /** Adds one filter button and returns its width + gap so callers can advance the cursor. */
    private int addFilterButton(int x, int y, String label, String filterId) {
        int width = filterButtonWidth(label);
        Button button = Button.builder(Component.literal(label), b -> {
            colonyFilter = filterId;
            scrollOffset = 0;
            buildWidgets();
        }).bounds(x, y, width, 16).build();
        button.active = !java.util.Objects.equals(colonyFilter, filterId);
        addRenderableWidget(button);
        return width + 4;
    }

    // --- data helpers ----------------------------------------------------------------------

    /** Rows currently visible given the colony filter and search text. */
    private List<TradingPostMenu.Row> filteredRows() {
        String search = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<TradingPostMenu.Row> out = new ArrayList<>();
        for (TradingPostMenu.Row row : menu.getAllRows()) {
            if (colonyFilter != null && !colonyFilter.equals(row.colonyId())) {
                continue;
            }
            if (!search.isEmpty() && !displayName(row.entry()).toLowerCase(Locale.ROOT).contains(search)) {
                continue;
            }
            out.add(row);
        }
        return out;
    }

    /** The currently selected row resolved against live data, or null. */
    private TradingPostMenu.Row selectedRow() {
        if (selectedItemId == null) {
            return null;
        }
        for (TradingPostMenu.Row row : menu.getAllRows()) {
            if (row.colonyId().equals(selectedColonyId) && row.entry().itemId().equals(selectedItemId)) {
                return row;
            }
        }
        return null;
    }

    private static String displayName(MarketNetworking.EntrySnapshot entry) {
        return entry.item() == null ? entry.itemId().toString() : new ItemStack(entry.item()).getHoverName().getString();
    }

    private int buyCapacity(MarketNetworking.EntrySnapshot e) {
        return Math.max(0, e.currentStock() - e.minStock());
    }

    private int sellCapacity(MarketNetworking.EntrySnapshot e) {
        return Math.max(0, e.maxStock() - e.currentStock());
    }

    /** Largest quantity the slider should offer for the selected item (max of buy/sell capacity). */
    private int maxTradable() {
        TradingPostMenu.Row row = selectedRow();
        if (row == null) {
            return 1;
        }
        int cap = Math.max(buyCapacity(row.entry()), sellCapacity(row.entry()));
        return Mth.clamp(cap, 1, 64 * 64);
    }

    private MarketPricing.Quote buyQuote(MarketNetworking.EntrySnapshot e, int qty) {
        return MarketPricing.quoteBuy(e.minStock(), e.baseStock(), e.maxStock(), e.basePrice(),
                e.currentStock(), qty, menu.getMinPriceFactor(), menu.getMaxPriceFactor());
    }

    private MarketPricing.Quote sellQuote(MarketNetworking.EntrySnapshot e, int qty) {
        return MarketPricing.quoteSell(e.minStock(), e.baseStock(), e.maxStock(), e.basePrice(),
                e.currentStock(), qty, menu.getMinPriceFactor(), menu.getMaxPriceFactor());
    }

    /**
     * Indicative price for {@link #PRICE_QUANTITY} units, which is what the list actually shows.
     *
     * <p>A per-unit figure is useless here: the economy is anchored at 1 emerald per 16 logs, so
     * real unit prices run 0.0625 to 1.5, and rounding those to whole emeralds collapsed every
     * tier from ABUNDANT through PRECIOUS into an identical "1e" - a 16x price range rendered as
     * one number, with no way to tell a log from a diamond block. Quoting a bulk lot instead keeps
     * the numbers whole *and* spreads the tiers into a legible 4/8/16/32/64/96 ladder.
     *
     * <p>Indicative, not exact: buying in bulk walks the price up as stock drains, so the real
     * total comes from {@link #buyQuote}. The Pay/Earn lines are the authoritative figures.
     */
    private int lotPrice(MarketNetworking.EntrySnapshot e) {
        double unit = MarketPricing.unitPriceExact(e.minStock(), e.baseStock(), e.maxStock(),
                e.basePrice(), e.currentStock(), menu.getMinPriceFactor(), menu.getMaxPriceFactor());
        return (int) Math.max(1, Math.round(unit * PRICE_QUANTITY));
    }

    // --- quantity syncing (slider <-> number box) ------------------------------------------

    private void setQuantity(int q) {
        if (updatingQuantity) {
            return;
        }
        updatingQuantity = true;
        int maxT = Math.max(1, maxTradable());
        quantity = Mth.clamp(q, 1, maxT);
        if (quantityBox != null && !quantityBox.getValue().equals(String.valueOf(quantity))) {
            quantityBox.setValue(String.valueOf(quantity));
        }
        if (slider != null) {
            slider.setSliderValue((quantity - 1) / (double) Math.max(1, maxT - 1));
        }
        updatingQuantity = false;
    }

    private void sendTrade(boolean buy) {
        TradingPostMenu.Row row = selectedRow();
        if (row == null) {
            return;
        }
        if (buy) {
            NetworkHandler.CHANNEL.sendToServer(new C2SBuyPacket(row.colonyId(), row.entry().itemId(), quantity));
        } else {
            NetworkHandler.CHANNEL.sendToServer(new C2SSellPacket(row.colonyId(), row.entry().itemId(), quantity));
        }
    }

    // --- input -----------------------------------------------------------------------------

    /**
     * Left-click-drag on the slider goes through the widget itself (see {@link QuantitySlider#applyValue()}),
     * which always snaps to full-stack amounts. Right-click-drag is handled entirely here instead,
     * bypassing the widget's click path so it never snaps - free, exact placement.
     */
    private boolean rightDraggingSlider = false;
    private boolean scrollbarDragging = false;

    private boolean isOverSlider(double mouseX, double mouseY) {
        return slider != null && mouseX >= slider.getX() && mouseX < slider.getX() + SLIDER_W
                && mouseY >= slider.getY() && mouseY < slider.getY() + SLIDER_H;
    }

    private void setQuantityFromMouseXFree(double mouseX) {
        int maxT = Math.max(1, maxTradable());
        double v = Mth.clamp((mouseX - (slider.getX() + 4)) / (double) (SLIDER_W - 8), 0.0, 1.0);
        int q = 1 + (int) Math.round(v * (maxT - 1));
        setQuantity(q);
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        int sbX = leftPos + SCROLLBAR_X;
        int baseY = topPos + listY;
        return mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= baseY && mouseY < baseY + LIST_H;
    }

    /** Jumps scrollOffset to whatever position along the track the mouse currently sits at. */
    private void updateScrollFromMouseY(double mouseY) {
        int maxScroll = Math.max(0, filteredRows().size() - VISIBLE_ROWS);
        if (maxScroll <= 0) {
            scrollOffset = 0;
            return;
        }
        int baseY = topPos + listY;
        double ratio = Mth.clamp((mouseY - baseY) / (double) LIST_H, 0.0, 1.0);
        scrollOffset = (int) Math.round(ratio * maxScroll);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 1 && rightDraggingSlider) {
            setQuantityFromMouseXFree(mouseX);
            return true;
        }
        if (button == 0 && scrollbarDragging) {
            updateScrollFromMouseY(mouseY);
            return true;
        }
        // AbstractContainerScreen's own mouseDragged is for dragging items across slots and never
        // forwards drags to child widgets - so without this, the quantity slider can't be dragged.
        // Forward drags to whatever child is focused/being dragged (the slider).
        if (getFocused() != null && isDragging() && button == 0) {
            return getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) {
            rightDraggingSlider = false;
        }
        if (button == 0) {
            scrollbarDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // All checked before super.mouseClicked(): AbstractContainerScreen's own click handling
        // (slot interactions, quick-crafting drag state) runs there and can consume a click before
        // it ever reaches our code, even though this menu has no slots at all. This bit us for the
        // slider and scrollbar already - row selection needs the same treatment, since it's the
        // same "empty space with no real widget" situation.
        if (button == 1 && isOverSlider(mouseX, mouseY)) {
            rightDraggingSlider = true;
            setQuantityFromMouseXFree(mouseX);
            return true;
        }
        if (button == 0 && isOverScrollbar(mouseX, mouseY)) {
            scrollbarDragging = true;
            updateScrollFromMouseY(mouseY);
            return true;
        }
        if (button == 0) {
            int relX = (int) mouseX - leftPos;
            int relY = (int) mouseY - topPos;
            if (relX >= LIST_X && relX <= LIST_X + LIST_W && relY >= listY && relY < listY + LIST_H) {
                int index = scrollOffset + (relY - listY) / ROW_H;
                List<TradingPostMenu.Row> rows = filteredRows();
                if (index >= 0 && index < rows.size()) {
                    TradingPostMenu.Row row = rows.get(index);
                    selectedColonyId = row.colonyId();
                    selectedItemId = row.entry().itemId();
                    setQuantity(Math.min(quantity, maxTradable()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int relX = (int) mouseX - leftPos;
        int relY = (int) mouseY - topPos;
        if (relX >= LIST_X && relX <= SCROLLBAR_X + SCROLLBAR_W && relY >= listY && relY < listY + LIST_H) {
            int maxScroll = Math.max(0, filteredRows().size() - VISIBLE_ROWS);
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (searchBox != null) {
            searchBox.tick();
        }
        if (quantityBox != null) {
            quantityBox.tick();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // While a text field is focused, it must get every key - including plain letters. EditBox
        // only consumes those via charTyped (a separate callback for actual text insertion), not
        // keyPressed, so a raw keyPressed for a letter that happens to match a hotbar/inventory
        // keybind (like the default "E") would otherwise fall through to super.keyPressed(), which
        // is where the vanilla "close this screen" check lives - closing the GUI while typing.
        EditBox focusedBox = searchBox != null && searchBox.isFocused() ? searchBox
                : quantityBox != null && quantityBox.isFocused() ? quantityBox : null;
        if (focusedBox != null) {
            if (keyCode == 256) { // Escape: unfocus the field rather than closing the screen
                setFocused(null);
                return true;
            }
            focusedBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // --- rendering -------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Keep the slider/buttons in step with the live selection before drawing.
        refreshTradeWidgets();

        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick); // panels (renderBg) + widgets
        renderList(guiGraphics, mouseX, mouseY);
        renderPanel(guiGraphics);
        renderTooltips(guiGraphics, mouseX, mouseY);
    }

    /** Enable/disable Buy/Sell based on live capacity and the player's inventory. */
    private void refreshTradeWidgets() {
        TradingPostMenu.Row row = selectedRow();
        if (row == null) {
            if (buyButton != null) buyButton.active = false;
            if (sellButton != null) sellButton.active = false;
            if (slider != null) slider.active = false;
            return;
        }
        if (slider != null) slider.active = true;
        MarketNetworking.EntrySnapshot e = row.entry();

        MarketPricing.Quote buy = buyQuote(e, quantity);
        boolean canAfford = buy.total() <= countEmeralds();
        if (buyButton != null) buyButton.active = buy.filledQty() > 0 && canAfford;

        int owned = countItem(e);
        MarketPricing.Quote sell = sellQuote(e, Math.min(quantity, owned));
        if (sellButton != null) sellButton.active = sell.filledQty() > 0;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // NOTE: renderBg is called WITHOUT the (leftPos, topPos) translation, so draw in absolute
        // coordinates. (The Phase 1 screen drew at 0,0 here - that was the misplaced-panel bug.)
        int x = leftPos;
        int y = topPos;

        // Themed panel: brass frame, header band and compass watermark, baked into one texture.
        g.blit(BACKGROUND, x, y, 0.0f, 0.0f, imageWidth, imageHeight, BG_TEX_SIZE, BG_TEX_SIZE);

        // The list viewport and trade divider stay procedural rather than baked into the texture:
        // listY/panelY shift down whenever the colony filter buttons wrap to another row, so their
        // position isn't known until buildWidgets() runs.
        g.fill(x + LIST_X - 1, y + listY - 1, x + LIST_X + LIST_W + 1, y + listY + LIST_H + 1, 0xFF3A2E14);
        g.fill(x + LIST_X, y + listY, x + LIST_X + LIST_W, y + listY + LIST_H, 0xFF0C1018);
        g.fill(x + 6, y + panelY - 3, x + imageWidth - 6, y + panelY - 2, 0xFFC69D4A);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Drawn WITH the (leftPos, topPos) translation - use relative coordinates here.
        g.drawString(font, title, 8, 6, 0xFFFFFF, false);
        g.drawString(font, "Emeralds: " + countEmeralds(), imageWidth - 8 - font.width("Emeralds: " + countEmeralds()), 6, 0x55FF55, false);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        List<TradingPostMenu.Row> rows = filteredRows();
        int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        int baseX = leftPos + LIST_X;
        int baseY = topPos + listY;
        g.enableScissor(baseX, baseY, baseX + LIST_W, baseY + LIST_H);
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollOffset + i;
            if (index >= rows.size()) {
                break;
            }
            TradingPostMenu.Row row = rows.get(index);
            MarketNetworking.EntrySnapshot e = row.entry();
            int ry = baseY + i * ROW_H;

            boolean selected = e.itemId().equals(selectedItemId) && row.colonyId().equals(selectedColonyId);
            boolean hovered = mouseX >= baseX && mouseX < baseX + LIST_W && mouseY >= ry && mouseY < ry + ROW_H;
            if (selected) {
                g.fill(baseX, ry, baseX + LIST_W, ry + ROW_H, 0xFF2E4368);
            } else if (hovered) {
                g.fill(baseX, ry, baseX + LIST_W, ry + ROW_H, 0xFF243247);
            }

            ItemStack icon = e.item() == null ? ItemStack.EMPTY : new ItemStack(e.item());
            g.renderItem(icon, baseX + 2, ry + 2);
            g.drawString(font, displayName(e), baseX + 22, ry + 2, 0xFFFFFF, false);
            g.drawString(font, row.colonyDisplayName(), baseX + 22, ry + 11, 0xFF8A93A8, false);

            String priceStr = lotPrice(e) + CURRENCY + "/" + PRICE_QUANTITY;
            g.drawString(font, priceStr, baseX + LIST_W - 6 - font.width(priceStr), ry + 2, 0xFFD54A, false);
            String stockStr = "x" + e.currentStock();
            g.drawString(font, stockStr, baseX + LIST_W - 6 - font.width(stockStr), ry + 11, 0xFF8A93A8, false);

            // Stock bar: min |======cur----| max
            drawStockBar(g, baseX + 150, ry + 13, 90, e);
        }
        g.disableScissor();

        // Scrollbar.
        int sbX = leftPos + SCROLLBAR_X;
        g.fill(sbX, baseY, sbX + SCROLLBAR_W, baseY + LIST_H, 0xFF0C1018);
        if (rows.size() > VISIBLE_ROWS) {
            int thumbH = Math.max(12, LIST_H * VISIBLE_ROWS / rows.size());
            int track = LIST_H - thumbH;
            int thumbY = baseY + (maxScroll == 0 ? 0 : track * scrollOffset / maxScroll);
            g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, 0xFF44557A);
        }
    }

    private void drawStockBar(GuiGraphics g, int x, int y, int w, MarketNetworking.EntrySnapshot e) {
        int span = Math.max(1, e.maxStock() - e.minStock());
        int fill = Mth.clamp((e.currentStock() - e.minStock()) * w / span, 0, w);
        g.fill(x, y, x + w, y + 3, 0xFF0C1018);
        g.fill(x, y, x + fill, y + 3, 0xFF4A8CFF);
    }

    private void renderPanel(GuiGraphics g) {
        int x = leftPos + LIST_X;
        int y = topPos + panelY;
        TradingPostMenu.Row row = selectedRow();
        if (row == null) {
            g.drawString(font, "Select an item to trade", x, y, 0xFF8A93A8, false);
            return;
        }
        MarketNetworking.EntrySnapshot e = row.entry();

        String header = displayName(e) + "  -  " + row.colonyDisplayName();
        g.drawString(font, header, x, y, 0xFFFFFF, false);
        g.drawString(font, PRICE_QUANTITY + " for " + lotPrice(e) + CURRENCY + "   Stock " + e.currentStock()
                + " (floor " + e.minStock() + " / cap " + e.maxStock() + ")", x, y + PANEL_INFO_Y, 0xFF8A93A8, false);

        // Quantity readout: "N = S stacks + R".
        int stackSize = e.item() == null ? 64 : Math.max(1, e.item().getMaxStackSize());
        String breakdown = stackSize > 1
                ? quantity + " = " + (quantity / stackSize) + " stacks + " + (quantity % stackSize)
                : String.valueOf(quantity);
        g.drawString(font, breakdown, x, y + PANEL_BREAKDOWN_Y, 0xFFFFFF, false);

        MarketPricing.Quote buy = buyQuote(e, quantity);
        int owned = countItem(e);
        MarketPricing.Quote sell = sellQuote(e, Math.min(quantity, owned));

        int textX = x + 150;
        g.drawString(font, "Pay " + buy.total() + CURRENCY + " for " + buy.filledQty(), textX, y + PANEL_BUTTONS_Y + 1, 0xFFD54A, false);
        g.drawString(font, "Earn " + sell.total() + CURRENCY + " for " + sell.filledQty(), textX, y + PANEL_BUTTONS_Y + 11, 0x55FF55, false);
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int baseX = leftPos + LIST_X;
        int baseY = topPos + listY;
        if (mouseX < baseX || mouseX >= baseX + LIST_W || mouseY < baseY || mouseY >= baseY + LIST_H) {
            return;
        }
        List<TradingPostMenu.Row> rows = filteredRows();
        int index = scrollOffset + (mouseY - baseY) / ROW_H;
        if (index < 0 || index >= rows.size()) {
            return;
        }
        MarketNetworking.EntrySnapshot e = rows.get(index).entry();
        if (e.item() != null) {
            g.renderTooltip(font, new ItemStack(e.item()), mouseX, mouseY);
        }
    }

    // --- inventory counting ----------------------------------------------------------------

    private int countEmeralds() {
        return countItem(Items.EMERALD);
    }

    private int countItem(MarketNetworking.EntrySnapshot e) {
        return e.item() == null ? 0 : countItem(e.item());
    }

    private int countItem(net.minecraft.world.item.Item item) {
        if (minecraft == null || minecraft.player == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : minecraft.player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** The selected item's stack size (64 for most items, 1 for tools), or 1 if nothing's selected. */
    private int currentStackSize() {
        TradingPostMenu.Row row = selectedRow();
        if (row == null || row.entry().item() == null) {
            return 1;
        }
        return Math.max(1, row.entry().item().getMaxStackSize());
    }

    /**
     * Slider whose 0..1 value maps to quantity 1..maxTradable for the selected item. Dragging is
     * continuous, but landing within a few pixels of a full-stack multiple "snaps" to it exactly -
     * fast stack-precise selection without losing the ability to stop on any in-between amount.
     */
    private class QuantitySlider extends AbstractSliderButton {
        QuantitySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), 0.0);
            updateMessage();
        }

        void setSliderValue(double v) {
            this.value = Mth.clamp(v, 0.0, 1.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Qty: " + quantity));
        }

        @Override
        protected void applyValue() {
            int maxT = Math.max(1, maxTradable());
            int q = 1 + (int) Math.round(this.value * (maxT - 1));

            int stackSize = currentStackSize();
            if (stackSize > 1 && maxT > stackSize) {
                // A window of ~4 pixels' worth of quantity, expressed in slider units so it scales
                // sensibly whether maxT is 100 or 100,000.
                int window = Math.max(1, Math.round(4f / Math.max(1, this.width) * (maxT - 1)));
                int nearestStack = Mth.clamp(Math.round((float) q / stackSize) * stackSize, 1, maxT);
                if (Math.abs(q - nearestStack) <= window) {
                    q = nearestStack;
                }
            }
            setQuantity(q);
        }
    }
}
