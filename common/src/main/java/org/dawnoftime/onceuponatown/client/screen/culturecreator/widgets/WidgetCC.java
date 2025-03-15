package org.dawnoftime.onceuponatown.client.screen.culturecreator.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.dawnoftime.onceuponatown.client.screen.culturecreator.BaseCCScreen.*;

public abstract class WidgetCC {

    public WidgetCC() {}

    @NotNull
    public String get() {
        return "";
    }

    public abstract AbstractWidget[] getWidgets();

    @FunctionalInterface
    public interface WidgetAction {
        void execute(WidgetCC widget);
    }

    public static class ButtonCC extends WidgetCC{

        private final LeftAlignTextButton button;

        /**
         * Adds a row to the screen that contains only a button.
         *
         * @param posX X position of the GUI.
         * @param onPress OnPress effect of the button.
         */
        public ButtonCC(int posX, Component text, WidgetAction onPress){
            this.button = new LeftAlignTextButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH, WIDGET_HEIGHT, text, btn -> onPress.execute(this));
        }

        @Override
        public AbstractWidget[] getWidgets() {
            return new AbstractWidget[]{button};
        }
    }

    public static class EditBoxAndConfirm extends WidgetCC {

        private final IconButton button;
        private final EditBox editBox;
        private final boolean naturalLanguage;

        /**
         * Adds a row to the screen that an editBox, with a confirm button next to it.
         *
         * @param posX X position of the GUI.
         * @param hint Component that will be displayed in the editBox to help the user knowing what to write.
         * @param naturalLanguage True to allow any wording in the text, false to standardize the text.
         * @param onPress OnPress effect of the confirm button.
         */
        public EditBoxAndConfirm(int posX, Component hint, Font font, boolean naturalLanguage, WidgetAction onPress){
            this.naturalLanguage = naturalLanguage;
            this.button = new IconButton(posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - WIDGET_HEIGHT, 0, WIDGET_HEIGHT, GUI_TEXTURE, 83, 166, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT, btn -> onPress.execute(this));
            button.active = false;
            this.editBox = new EditBox(font, posX + WIDGET_ZONE_X + 1, 0, WIDGET_ZONE_WIDTH - WIDGET_HEIGHT - 2, WIDGET_HEIGHT - 2, Component.empty()) {
                // We must edit the setY function because for some reason, MC devs decided that the actual border of this widget should be bigger than its size...
                @Override
                public void setY(int y) {
                    super.setY(y + 1);
                }

                @Override
                public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                    boolean b = super.keyPressed(keyCode, scanCode, modifiers);
                    button.active = !this.getValue().trim().isEmpty();
                    return b;
                }

                @Override
                public boolean charTyped(char codePoint, int modifiers) {
                    if (!naturalLanguage) {
                        if ((codePoint < 'a' || codePoint > 'z') &&
                                (codePoint < 'A' || codePoint > 'Z') &&
                                (codePoint < '0' || codePoint > '9') &&
                                codePoint != ' ' && codePoint != '_') {
                            return false; // Block other characters
                        }
                    }
                    return super.charTyped(codePoint, modifiers);
                }

                @Override
                public void insertText(@NotNull String textToWrite) {
                    super.insertText(textToWrite.replace(" ", "_").toLowerCase());
                }
            };
            editBox.setHint(hint);
        }

        @Override
        public AbstractWidget[] getWidgets() {
            return new AbstractWidget[]{editBox, button};
        }

        @Override
        public @NotNull String get() {
            String content = editBox.getValue().trim();
            if (!naturalLanguage) {
                content = content.toLowerCase().replace(" ", "_").replaceAll("[^a-z0-9_]", "");
            }
            return content;
        }
    }

    public static class EditDigit extends WidgetCC {

        private final LeftAlignTextButton textButton;
        private final EditBox editBox;

        /**
         * Adds a row to the screen that an editBox, with a confirm button next to it.
         *
         * @param posX X position of the GUI.
         * @param hint Component that will be displayed in the editBox to help the user knowing what to write.
         */
        public EditDigit(int posX, Component hint, Font font, boolean isInt){
            this.textButton = new LeftAlignTextButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH - 3 * WIDGET_HEIGHT, WIDGET_HEIGHT, hint);
            this.editBox = new EditBox(font, posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - 3 * WIDGET_HEIGHT, 0, 3 * WIDGET_HEIGHT - 2, WIDGET_HEIGHT - 2, Component.literal("...")) {
                // We must edit the setY function because for some reason, MC devs decided that the actual border of this widget should be bigger than its size...
                @Override
                public void setY(int y) {
                    super.setY(y + 1);
                }

                @Override
                public boolean charTyped(char codePoint, int modifiers) {
                    if (!Character.isDigit(codePoint)) {
                        if (isInt || (codePoint != '.' && codePoint != '-')) {
                            return false; // Block other characters
                        }
                    }
                    return super.charTyped(codePoint, modifiers);
                }
            };
            editBox.setHint(hint);
        }

        @Override
        public AbstractWidget[] getWidgets() {
            return new AbstractWidget[]{textButton, editBox};
        }

        @Override
        public @NotNull String get() {
            return editBox.getValue().trim();
        }
    }

    public static class EditDigitAndConfirm extends WidgetCC {

        private final LeftAlignTextButton textButton;
        private final IconButton button;
        private final EditBox editBox;

        /**
         * Adds a row to the screen that an editBox, with a confirm button next to it.
         *
         * @param posX X position of the GUI.
         * @param hint Component that will be displayed in the editBox to help the user knowing what to write.
         * @param onPress OnPress effect of the confirm button.
         */
        public EditDigitAndConfirm(int posX, Component hint, Font font, WidgetAction onPress){
            this.textButton = new LeftAlignTextButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH - 3 * WIDGET_HEIGHT, WIDGET_HEIGHT, hint);
            this.button = new IconButton(posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - WIDGET_HEIGHT, 0, WIDGET_HEIGHT, GUI_TEXTURE, 83, 166, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT, btn -> onPress.execute(this));
            this.editBox = new EditBox(font, posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - 3 * WIDGET_HEIGHT, 0, 2 * WIDGET_HEIGHT, WIDGET_HEIGHT - 2, Component.literal("...")) {
                // We must edit the setY function because for some reason, MC devs decided that the actual border of this widget should be bigger than its size...
                @Override
                public void setY(int y) {
                    super.setY(y + 1);
                }

                @Override
                public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                    boolean b = super.keyPressed(keyCode, scanCode, modifiers);
                    button.active = !this.getValue().trim().isEmpty();
                    return b;
                }

                @Override
                public boolean charTyped(char codePoint, int modifiers) {
                    if (!Character.isDigit(codePoint) && codePoint != '.' && codePoint != '-') {
                        return false; // Block other characters
                    }
                    return super.charTyped(codePoint, modifiers);
                }
            };
            editBox.setHint(hint);
            button.active = false;
        }

        @Override
        public AbstractWidget[] getWidgets() {
            return new AbstractWidget[]{textButton, editBox, button};
        }

        @Override
        public @NotNull String get() {
            String content = editBox.getValue().trim();
            try{
                return String.valueOf(Double.parseDouble(content));
            } catch (NumberFormatException e) {
                return "";
            }
        }
    }

    /*
    protected void createNewRawButton(Button.OnPress onPressConfirm) {
        IconButton button = new IconButton(posX + WIDGET_ZONE_X + (WIDGET_ZONE_WIDTH / 2) - (WIDGET_PLUS_BUTTON_HEIGHT /2), (WIDGET_HEIGHT - WIDGET_PLUS_BUTTON_HEIGHT) / 2, WIDGET_PLUS_BUTTON_HEIGHT, GUI_TEXTURE, 67, 193, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT, onPressConfirm);
        widgets.add(new AbstractWidget[]{button});
        this.addRenderableWidget(button);
    }
     */
}

