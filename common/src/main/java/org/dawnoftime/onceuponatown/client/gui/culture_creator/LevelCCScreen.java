package org.dawnoftime.onceuponatown.client.gui.culture_creator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.AddWidgetCC;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.DropAndEditBoxWidgetCC;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.EditDigitWidgetCC;
import org.dawnoftime.onceuponatown.network.culturecreator.*;
import oshi.util.tuples.Pair;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class LevelCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;
    private final String level;
    private final String initRequiredEra;
    private final String initDwellingSlots;
    private final Map<Integer, Pair<String, String>> initProfessionSlots;
    private final List<String> professionList;

    public LevelCCScreen(S2COpenLevelCCScreenPacket packet) {
        super(Ouat.translatable("cc", "level_nav", Integer.parseInt(packet.getLevel()) + 1));
        cultureId = packet.getCultureId();
        buildingId = packet.getBuildingId();
        level = packet.getLevel();
        professionList = packet.getProfessionList();
        initRequiredEra = packet.getRequiredEra();
        initDwellingSlots = packet.getDwellingSlots();
        initProfessionSlots = IntStream.range(0, packet.getProfessionSlots().size()).boxed()
                .collect(Collectors.toMap(i -> i, packet.getProfessionSlots()::get));
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return Arrays.asList(
                new NavigationTab(CULTURES_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab(BUILDINGS_FOLDER_NAME, Ouat.translatable("cc", "buildings_nav"), () -> new C2SRequestBuildingsCCPacket(cultureId)),
                new NavigationTab(null, Component.literal(buildingId), () -> new C2SRequestBuildingCCPacket(cultureId, buildingId)),
                new NavigationTab(null, Ouat.translatable("cc", "levels_nav"), () -> new C2SRequestLevelsCCPacket(cultureId, buildingId)),
                new NavigationTab(null, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        this.addWidget("required_era", new EditDigitWidgetCC(posX, Ouat.translatable("cc", "building_level_required_era"), font, true))
                .set(null, initRequiredEra);
        this.addWidget("dwelling_slots", new EditDigitWidgetCC(posX, Ouat.translatable("cc", "building_level_dwelling_slots"), font, true))
                .set(null, initDwellingSlots);
        LinkedHashMap<String, String> professionMap = professionList.stream().sorted().collect(Collectors.toMap(s -> s, s -> s, (a, b) -> a, LinkedHashMap::new));
        for (int slotIndex : initProfessionSlots.keySet()) {
            this.addWidget("profession_slot_" + slotIndex, new DropAndEditBoxWidgetCC(
                            posX,
                            this,
                            Ouat.translatable("cc", "building_level_profession_dropdown_default"),
                            font,
                            (id, name) -> initProfessionSlots.put(slotIndex, new Pair<>(id, initProfessionSlots.get(slotIndex).getB())),
                            professionMap))
                    .set("selection", initProfessionSlots.get(slotIndex).getA())
                    .set("number", initProfessionSlots.get(slotIndex).getB());
        }
        this.addWidget("add_profession_slot", new AddWidgetCC(posX, (widget) -> {
            Set<Integer> keys = initProfessionSlots.keySet();
            int newId = keys.isEmpty() ? 0 : Collections.max(keys) + 1;
            this.insertBeforeLast("profession_slot_" + newId, new DropAndEditBoxWidgetCC(
                            posX,
                            this,
                            Ouat.translatable("cc", "building_level_profession_dropdown_default"),
                            font,
                            (id, name) -> initProfessionSlots.put(newId, new Pair<>(id, initProfessionSlots.get(newId).getB())),
                            professionMap));
            this.updateWidgetPositions();
            this.updateMaxScrollOffset();
        }));
    }

    // TODO Lors de la fermeture de dropdown, le parent screen se réinitialise et reprend les valeurs des variables init, alors que les champs ont depuis été modifié par l'utilisateur (et sauvegardés)...

    @Override
    public void removed() {
        List<Pair<String, String>> professionSlots = new ArrayList<>();
        for (String widgetId : widgets.keySet()) {
            if (widgetId.startsWith("profession_slot")) {
                professionSlots.add(new Pair<>(widgets.get(widgetId).get("selection"), widgets.get(widgetId).get("number")));
            }
        }
        Ouat.CLIENT.sendToServer(new C2SSaveLevelCCPacket(cultureId, buildingId, level, this.widgets.get("required_era").get(), this.widgets.get("dwelling_slots").get(), professionSlots));
        super.removed();
    }
}
