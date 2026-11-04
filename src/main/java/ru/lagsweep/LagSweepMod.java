package ru.lagsweep;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import ru.lagsweep.config.SweepConfig;

/**
 * LagSweep — серверный мод для NeoForge 1.21.1, который периодически удаляет
 * все валяющиеся на земле предметы (ItemEntity).
 *
 * Период очистки регулируется конфигом (по умолчанию 10 минут).
 * Есть whitelist предметов, тегов и модов — они НЕ удаляются.
 * За 2 и 1 минуту до очистки и за 5..1 секунд отправляются предупреждения в чат.
 */
@Mod(LagSweepMod.MODID)
public class LagSweepMod {
    public static final String MODID = "lagsweep";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LagSweepMod(IEventBus modEventBus, ModContainer modContainer) {
        // Конфиг: TOML-файл config/lagsweep.toml (COMMON — есть и на выделенном сервере)
        modContainer.registerConfig(ModConfig.Type.COMMON, SweepConfig.SPEC);

        SweepEvents sweep = new SweepEvents();

        // Игровые события (tick / старт и остановка сервера)
        NeoForge.EVENT_BUS.register(sweep);
        // События конфиг-шины (перезагрузка lagsweep.toml без рестарта)
        modEventBus.register(new SweepEvents.ConfigListener(sweep));

        LOGGER.info("[Lagsweep] initialized");
    }
}
