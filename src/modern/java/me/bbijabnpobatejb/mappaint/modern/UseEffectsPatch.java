package me.bbijabnpobatejb.mappaint.modern;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseEffects;
import lombok.experimental.UtilityClass;
import org.bukkit.inventory.ItemStack;

@UtilityClass
@SuppressWarnings("UnstableApiUsage")
public class UseEffectsPatch {

    public void apply(ItemStack item) {
        item.setData(DataComponentTypes.USE_EFFECTS, UseEffects.useEffects()
                .canSprint(true)
                .interactVibrations(false)
                .speedMultiplier(1f));
    }
}
