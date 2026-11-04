package ru.lagsweep.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import ru.lagsweep.LagSweepMod;

import java.util.List;

/**
 * Конфиг мода (config/lagsweep.toml, тип COMMON — работает и на сервере, и на клиенте-интеграции).
 */
public final class SweepConfig {

    public static final ModConfigSpec SPEC;
    public static final General GENERAL;

    static {
        Pair<General, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(General::new);
        GENERAL = pair.getLeft();
        SPEC = pair.getRight();
    }

    public static final class General {
        // --- Основные настройки ---
        public final ModConfigSpec.IntValue intervalMinutes;   // период очистки, минуты
        public final ModConfigSpec.BooleanValue enableWarnings; // предупреждения в чат
        public final ModConfigSpec.BooleanValue removeXpOrbs;   // удалять ли сферы опыта
        public final ModConfigSpec.BooleanValue keepPersistent; // не удалять сущностей с isPersistent()
        public final ModConfigSpec.IntValue maxMergableStack;   // порог "лагов": суммарное количество одинаковых стакающихся предметов в чанке
        public final ModConfigSpec.BooleanValue onlyWhenOverThreshold; // удалять только чанки, превысившие порог

        // --- Whitelist ---
        public final ModConfigSpec.ConfigValue<List<? extends String>> whitelistItems; // minecraft:diamond и т.п.
        public final ModConfigSpec.ConfigValue<List<? extends String>> whitelistTags;  // #minecraft:swords, #forge:ingots
        public final ModConfigSpec.ConfigValue<List<? extends String>> whitelistMods;  // id модов, чьи предметы не трогать

        General(ModConfigSpec.Builder builder) {
            builder.push("general");

            intervalMinutes = builder
                    .comment(
                            "Период автоматической очистки валяющихся предметов, в минутах.",
                            "Можно регулировать: 1 .. 1440 (по умолчанию 10).")
                    .defineInRange("intervalMinutes", 10, 1, 1440);

            enableWarnings = builder
                    .comment(
                            "Предупреждения в чат: за 2 и 1 минуту до очистки, и обратный отсчет 5-4-3-2-1 секунд.")
                    .define("enableWarnings", true);

            removeXpOrbs = builder
                    .comment("Удалять вместе с предметами также сферы опыта (ExperienceOrb).")
                    .define("removeXpOrbs", false);

            keepPersistent = builder
                    .comment(
                            "Не удалять предметы, помеченные игроком как 'сохранённые' (P/N через Nametag или ",
                            "другими модами, т.е. Item#isPersistent() == true).")
                    .define("keepPersistent", true);

            onlyWhenOverThreshold = builder
                    .comment(
                            "Режим 'только при лагах': удалять предметы лишь в тех чанках, где суммарное количество",
                            "одинаковых стакающихся предметов превышает maxMergableStack.")
                    .define("onlyWhenOverThreshold", false);

            maxMergableStack = builder
                    .comment("Порог для onlyWhenOverThreshold.")
                    .defineInRange("maxMergableStack", 640, 1, Integer.MAX_VALUE);

            builder.pop();

            builder.push("whitelist");

            whitelistItems = builder
                    .comment(
                            "Предметы, которые НИКОГДА не удаляются.",
                            "Формат: 'мод:item', например 'minecraft:diamond'.",
                            "Поддерживается тег количества: 'minecraft:diamond{count>1}' не нужен —",
                            "просто указывайте сам предмет.")
                    .defineListAllowEmpty("items",
                            List.of("minecraft:nether_star", "minecraft:enchanted_golden_apple"),
                            obj -> obj instanceof String s && !s.isBlank());

            whitelistTags = builder
                    .comment(
                            "Тег-предметы (#tag) тоже не удаляются.",
                            "Формат: '#namespace:tag', например '#minecraft:swords' или '#c:ingots'.")
                    .defineListAllowEmpty("tags", List.of(),
                            obj -> obj instanceof String s && s.startsWith("#"));

            whitelistMods = builder
                    .comment(
                            "Все предметы указанных модов не удаляются.",
                            "Например: ['minecolonies', 'lootr', 'farmersdelight'].",
                            "Для совместимости со списком модов пользователя этот список по умолчанию",
                            "уже содержит небезопасные моды (см. defaults ниже).")
                    .defineListAllowEmpty("mods",
                            List.of(
                                    "minecolonies", "betterwithminecolonies", "christmascolonies",
                                    "mctradepost", "stylecolonies", "structurize",
                                    "lootr", "farmersdelight", "oceansdelight", "storagedelight",
                                    "sophisticatedbackpacks", "sophisticatedcore",
                                    "twilightforest", "artifacts", "relics", "irons_spellbooks",
                                    "chronomancy", "cataclysm", "mcwfurnitures", "mcwlights",
                                    "another_furniture", "domum_ornamentum", "animatedcats",
                                    "darksleep", "mr_dungeons_andtaverns", "mr_epic_structuresdungeons",
                                    "explorify", "byzantine", "mes", "mns", "mvs", "moogs_structures"
                            ),
                            obj -> obj instanceof String s && !s.isBlank());

            builder.pop();

            LagSweepMod.LOGGER.debug("[Lagsweep] config spec built");
        }
    }
}
