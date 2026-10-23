package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Drillships.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.drillships"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.BASIC_DRILLSHIP.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.BASIC_DRILLSHIP.get());
                output.accept(ModItems.BASIC_WAGON.get());
                output.accept(ModItems.BASIC_DRILL.get());
                output.accept(ModItems.BASIC_ENGINE.get());
                output.accept(ModItems.BASIC_HULL.get());
                output.accept(ModItems.BASIC_TREAD.get());
                output.accept(ModItems.BASIC_WHEEL.get());
                output.accept(ModItems.BASIC_DRILL_PIECE.get());
                output.accept(ModItems.BASIC_DRILL_SHAFT.get());
            }).build());

    private ModCreativeTabs() {
    }
}
