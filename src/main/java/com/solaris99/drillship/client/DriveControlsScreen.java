package com.solaris99.drillship.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * The drillship's controls panel: one row per driving action with its key, which can be any key or mouse button.
 * Click a key, then press the new key or click the new mouse button (Escape cancels). Two driving actions on the same
 * key show in red; sharing a key with something vanilla does on foot is fine, it's held back while driving.
 */
public class DriveControlsScreen extends Screen {
    private static final int ROW_HEIGHT = 21;
    // Title, rows, the drill mode button, reset/done and the hint, centred together so they fit short windows.
    private static final int TITLE = 14;
    private static final int FOOTER = 4 + 20 + 4 + 20 + 14;
    private static final int LABEL_WIDTH = 120;

    private final Screen parent;
    private final List<Button> keyButtons = new ArrayList<>();
    private KeyMapping selected;
    private Button modeButton;

    public DriveControlsScreen(Screen parent) {
        super(Component.translatable("screen.drillships.controls"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        keyButtons.clear();
        int left = width / 2 - 140, top = top();
        for (int i = 0; i < DriveControls.ALL.size(); i++) {
            KeyMapping mapping = DriveControls.ALL.get(i);
            int y = top + i * ROW_HEIGHT;
            keyButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> {
                selected = mapping;
                refresh();
            }).bounds(left + LABEL_WIDTH + 10, y, 100, 20).build()));
            addRenderableWidget(Button.builder(Component.translatable("controls.reset"), b -> bind(mapping, mapping.getDefaultKeyModifier(), mapping.getDefaultKey()))
                    .bounds(left + LABEL_WIDTH + 114, y, 46, 20).build());
        }
        int bottom = top + DriveControls.ALL.size() * ROW_HEIGHT + 4;
        modeButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            ClientConfig.DRILL_MODE.set(ClientConfig.DRILL_MODE.get() == ClientConfig.DrillMode.TOGGLE
                    ? ClientConfig.DrillMode.HOLD : ClientConfig.DrillMode.TOGGLE);
            ClientConfig.SPEC.save();
            refresh();
        }).bounds(left, bottom, 280, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("controls.resetAll"), b -> {
            for (KeyMapping mapping : DriveControls.ALL) {
                mapping.setKeyModifierAndCode(mapping.getDefaultKeyModifier(), mapping.getDefaultKey());
            }
            saveBindings();
        }).bounds(left, bottom + 24, 137, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 143, bottom + 24, 137, 20).build());
        refresh();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 140, top = top();
        graphics.drawCenteredString(font, title, width / 2, top - TITLE, 0xFFFFFF);
        for (int i = 0; i < DriveControls.ALL.size(); i++) {
            graphics.drawString(font, Component.translatable(DriveControls.ALL.get(i).getName()), left, top + i * ROW_HEIGHT + 6, 0xFFFFFF);
        }
        graphics.drawCenteredString(font, Component.translatable("screen.drillships.controls.hint").withStyle(ChatFormatting.GRAY),
                width / 2, top + DriveControls.ALL.size() * ROW_HEIGHT + 52, 0xAAAAAA);
    }

    // Where the first row goes.
    private int top() {
        return Math.max(TITLE + 2, (height - (TITLE + DriveControls.ALL.size() * ROW_HEIGHT + FOOTER)) / 2 + TITLE);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selected == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            selected = null;
            refresh();
        } else {
            bind(selected, KeyModifier.NONE, InputConstants.getKey(keyCode, scanCode));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selected == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        bind(selected, KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(button));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void bind(KeyMapping mapping, KeyModifier modifier, InputConstants.Key key) {
        mapping.setKeyModifierAndCode(modifier, key);
        saveBindings();
    }

    private void saveBindings() {
        selected = null;
        KeyMapping.resetMapping();
        minecraft.options.save();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < keyButtons.size(); i++) {
            KeyMapping mapping = DriveControls.ALL.get(i);
            Component label = mapping.getTranslatedKeyMessage();
            if (mapping == selected) {
                label = Component.literal("> ").append(label.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                        .append(" <").withStyle(ChatFormatting.YELLOW);
            } else if (clashes(mapping)) {
                label = label.copy().withStyle(ChatFormatting.RED);
            }
            keyButtons.get(i).setMessage(label);
        }
        modeButton.setMessage(Component.translatable("screen.drillships.controls.drill_mode",
                Component.translatable("screen.drillships.controls.drill_mode." + ClientConfig.DRILL_MODE.get().name().toLowerCase(java.util.Locale.ROOT))));
    }

    // Another driving action on the same key: both would fire.
    private static boolean clashes(KeyMapping mapping) {
        if (mapping.isUnbound()) {
            return false;
        }
        for (KeyMapping other : DriveControls.ALL) {
            if (other != mapping && other.same(mapping) && other.getKeyModifier() == mapping.getKeyModifier()) {
                return true;
            }
        }
        return false;
    }
}
