package com.mozi1924.yefira.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mozi1924.yefira.Yefira;
import com.mozi1924.yefira.client.gui.IGuiDrawer;
import com.mozi1924.yefira.client.gui.YefiraScreen;
import com.mozi1924.yefira.client.render.IRenderContext;
import com.mozi1924.yefira.compat.IClientCompat;
import com.mozi1924.yefira.selection.SelectionStyle;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ClientCompat implements IClientCompat {

    public static final ClientCompat INSTANCE = new ClientCompat();
    public static final KeyMapping.Category YEFIRA_CATEGORY = KeyMapping.Category.register(Yefira.id("selection"));

    public static final IRenderContext RENDER_CONTEXT = new IRenderContext() {
        @Override
        public void drawBox(AABB worldBox, int fillColor, int strokeColor, float strokeWidth, boolean alwaysOnTop) {
            GizmoProperties props = Gizmos.cuboid(worldBox, GizmoStyle.strokeAndFill(strokeColor, strokeWidth, fillColor));
            if (alwaysOnTop) {
                props.setAlwaysOnTop();
            }
        }

        @Override
        public void drawPoint(Vec3 worldPos, int argbColor, float pointSize, boolean alwaysOnTop) {
            GizmoProperties props = Gizmos.point(worldPos, argbColor, pointSize);
            if (alwaysOnTop) {
                props.setAlwaysOnTop();
            }
        }

        @Override
        public void drawArrow(Vec3 startWorld, Vec3 endWorld, Vec3 dir, Vec3 u, Vec3 v, int argbColor, float width) {
            Gizmos.arrow(startWorld, endWorld, argbColor, width).setAlwaysOnTop();
        }
    };

    private ClientCompat() {}

    public static IGuiDrawer wrapGui(GuiGraphicsExtractor graphics) {
        return new IGuiDrawer() {
            @Override
            public void fill(int x1, int y1, int x2, int y2, int argbColor) {
                graphics.fill(x1, y1, x2, y2, argbColor);
            }

            @Override
            public void drawText(Font font, Component text, int x, int y, int color, boolean shadow) {
                graphics.text(font, text, x, y, color, shadow);
            }

            @Override
            public void drawCenteredText(Font font, Component text, int centerX, int y, int color) {
                graphics.centeredText(font, text, centerX, y, color);
            }
        };
    }

    @Override
    public boolean isNoScreenOpen(Minecraft mc) {
        return mc.gui == null || mc.gui.screen() == null;
    }

    @Override
    public boolean isKeyDown(Minecraft mc, int glfwKeyCode) {
        return InputConstants.isKeyDown(mc.getWindow(), glfwKeyCode);
    }

    @Override
    public void sendClientMessage(Minecraft mc, Component message) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(message);
        }
    }

    @Override
    public boolean hasOpPermission(Minecraft mc) {
        return mc.player != null
                && mc.player.permissions() != null
                && mc.player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public float getCameraFov(Minecraft mc) {
        if (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            return mc.gameRenderer.mainCamera().getFov();
        }
        return 70.0f;
    }

    @Override
    public void openScreen(Minecraft mc, Screen screen) {
        mc.setScreenAndShow(screen);
    }

    @Override
    public Screen createConfigScreen() {
        return new YefiraScreen();
    }

    @Override
    public KeyMapping createKeyMapping(String translationKey, int glfwKeyCode) {
        return new KeyMapping(translationKey, InputConstants.Type.KEYSYM, glfwKeyCode, YEFIRA_CATEGORY);
    }

    @Override
    public Checkbox createCheckbox(int x, int y, int width, int height, Font font,
                                   Component message, boolean initialValue,
                                   Consumer<Boolean> onValueChange) {
        return Checkbox.builder(message, font)
                .pos(x, y)
                .selected(initialValue)
                .onValueChange((cb, val) -> {
                    if (onValueChange != null) {
                        onValueChange.accept(val);
                    }
                })
                .build();
    }

    @Override
    public void copyToClipboard(Minecraft mc, String text) {
        if (mc != null && mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(text != null ? text : "");
        }
    }

    @Override
    public void attachPasswordMask(EditBox editBox, BooleanSupplier isMasked) {
        if (editBox != null) {
            editBox.addFormatter((text, offset) ->
                    isMasked != null && isMasked.getAsBoolean()
                            ? FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY)
                            : FormattedCharSequence.forward(text, Style.EMPTY));
        }
    }
}
