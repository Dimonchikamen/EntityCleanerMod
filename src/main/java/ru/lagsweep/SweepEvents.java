package ru.lagsweep;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.lagsweep.compat.ModCompat;
import ru.lagsweep.config.SweepConfig;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Ядро мода: цикл очистки, предупреждения в чат и удаление предметов.
 */
public class SweepEvents {

    private MinecraftServer server;

    // --- состояние цикла (в тиках сервера, 20 тиков = 1 секунда) ---
    private long cycleTicksTotal = 0L;   // период из конфига, в тиках
    private long ticksUntilSweep = 0L;   // сколько тиков осталось до очистки
    private boolean warned2min = false;
    private boolean warned1min = false;
    private int lastCountdownSec = -1;

    /** Экземпляр обработчика для доступа из команд (/lagsweep). */
    public static volatile SweepEvents instance;

    // --- кэш whitelist ---
    private final Set<Item> whitelistItemSet = new HashSet<>();
    private final Set<TagKey<Item>> whitelistTagSet = new HashSet<>();
    private final Set<String> whitelistModSet = new HashSet<>();
    private volatile boolean whitelistDirty = true;

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        this.server = event.getServer();
        this.whitelistDirty = true;
        instance = this;
        resetCycle();
        LagSweepMod.LOGGER.info("[Lagsweep] sweep interval = {} min",
                SweepConfig.GENERAL.intervalMinutes.get());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        this.server = event.getServer();
        // теги предметов уже загружены — пересобираем whitelist перед первым циклом
        this.whitelistDirty = true;
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        this.server = null;
    }

    /** Пересчёт периода из актуального значения конфига. */
    private void resetCycle() {
        long minutes = SweepConfig.GENERAL.intervalMinutes.get();
        cycleTicksTotal = minutes * 60L * 20L;
        ticksUntilSweep = cycleTicksTotal;
        warned2min = false;
        warned1min = false;
        lastCountdownSec = -1;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer srv = event.getServer();
        this.server = srv;

        if (ticksUntilSweep <= 0L) {
            doSweep(srv);
            resetCycle();
            return;
        }

        ticksUntilSweep--;
        long remainingSec = ticksUntilSweep / 20L;

        if (SweepConfig.GENERAL.enableWarnings.get()) {
            // За 2 минуты до очистки
            if (!warned2min && ticksUntilSweep <= 120L * 20L) {
                warned2min = true;
                broadcast(Component.literal("⚠ Очистка валяющихся предметов через 2 минуты!")
                        .withStyle(ChatFormatting.GOLD));
            }
            // За 1 минуту до очистки
            if (!warned1min && ticksUntilSweep <= 60L * 20L) {
                warned1min = true;
                broadcast(Component.literal("⚠ Очистка валяющихся предметов через 1 минуту!")
                        .withStyle(ChatFormatting.RED));
            }
            // Обратный отсчёт 5-4-3-2-1 секунд
            if (remainingSec >= 1 && remainingSec <= 5 && (int) remainingSec != lastCountdownSec) {
                lastCountdownSec = (int) remainingSec;
                broadcast(Component.literal("🧹 Удаление лежащих предметов через "
                        + remainingSec + " сек...").withStyle(ChatFormatting.YELLOW));
            }
        }

        if (ticksUntilSweep == 0L) {
            doSweep(srv);
            resetCycle();
        }
    }

    /** Собственно очистка всех уровней. */
    private void doSweep(MinecraftServer srv) {
        rebuildWhitelistIfNeeded();

        int removedItems = 0;
        int keptItems = 0;
        int removedXp = 0;
        boolean xp = SweepConfig.GENERAL.removeXpOrbs.get();
        boolean thresholdMode = SweepConfig.GENERAL.onlyWhenOverThreshold.get();

        for (ServerLevel level : srv.getAllLevels()) {
            for (Entity entity : level.getEntities()) {
                if (entity instanceof ItemEntity item) {
                    if (shouldKeep(item)) {
                        keptItems++;
                        continue;
                    }
                    if (thresholdMode && !isPartOfLaggyCluster(level, item)) {
                        keptItems++;
                        continue;
                    }
                    item.discard();
                    removedItems++;
                } else if (xp && entity instanceof ExperienceOrb) {
                    entity.discard();
                    removedXp++;
                }
            }
        }

        Component msg = Component.literal(String.format(Locale.ROOT,
                "🧹 Очистка завершена: удалено %d, сохранено (whitelist) %d",
                removedItems, keptItems))
                .withStyle(removedItems > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        broadcast(msg);
        if (removedXp > 0) {
            broadcast(Component.literal("🧹 Удалено сфер опыта: " + removedXp)
                    .withStyle(ChatFormatting.GREEN));
        }
        LagSweepMod.LOGGER.info("[Lagsweep] swept {} item entities, kept {}, xp orbs {}",
                removedItems, keptItems, removedXp);
    }

    /**
     * Режим "только при лагах": удалять только те предметы, вокруг которых
     * скопление одинаковых стакающихся предметов больше порога maxMergableStack.
     */
    private boolean isPartOfLaggyCluster(ServerLevel level, ItemEntity item) {
        int threshold = SweepConfig.GENERAL.maxMergableStack.get();
        ItemStack stack = item.getItem();
        int total = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class,
                item.getBoundingBox().inflate(8))) {
            if (ItemStack.isSameItemSameComponents(e.getItem(), stack)) {
                total += e.getItem().getCount();
                if (total >= threshold) return true;
            }
        }
        return false;
    }

    /** Проверка: должен ли предмет быть СОХРАНЁН (не удалён). */
    private boolean shouldKeep(ItemEntity item) {
        // 1. Предмет помечен как persistent (P/N игроком или другим модом)
        if (SweepConfig.GENERAL.keepPersistent.get() && item.isPersistent()) return true;

        // 2. Совместимость с модами сборки (MineColonies, Sophisticated Backpacks и т.д.)
        if (ModCompat.isProtectedByMod(item)) return true;

        ItemStack stack = item.getItem();
        Item it = stack.getItem();

        // 3. Whitelist по конкретному предмету
        if (whitelistItemSet.contains(it)) return true;

        // 4. Whitelist по namespace (моду-владельцу предмета)
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(it);
        if (whitelistModSet.contains(rl.getNamespace())) return true;

        // 5. Whitelist по тегам предметов
        for (TagKey<Item> tag : whitelistTagSet) {
            if (stack.is(tag)) return true;
        }

        return false;
    }

    private void rebuildWhitelistIfNeeded() {
        if (!whitelistDirty) return;
        whitelistItemSet.clear();
        whitelistTagSet.clear();
        whitelistModSet.clear();

        for (String s : SweepConfig.GENERAL.whitelistItems.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(s.trim());
            if (rl == null) {
                LagSweepMod.LOGGER.warn("[Lagsweep] bad whitelist item id: {}", s);
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != net.minecraft.world.item.Items.AIR) {
                whitelistItemSet.add(item);
            } else {
                LagSweepMod.LOGGER.warn("[Lagsweep] whitelist item not found: {}", rl);
            }
        }

        for (String s : SweepConfig.GENERAL.whitelistTags.get()) {
            String t = s.trim();
            if (t.startsWith("#")) t = t.substring(1);
            ResourceLocation rl = ResourceLocation.tryParse(t);
            if (rl != null) {
                whitelistTagSet.add(TagKey.create(net.minecraft.registry.RegistryKeys.ITEM, rl));
            }
        }

        for (String s : SweepConfig.GENERAL.whitelistMods.get()) {
            whitelistModSet.add(s.trim().toLowerCase(Locale.ROOT));
        }

        whitelistDirty = false;
        LagSweepMod.LOGGER.info("[Lagsweep] whitelist rebuilt: {} items, {} tags, {} mods",
                whitelistItemSet.size(), whitelistTagSet.size(), whitelistModSet.size());
    }

    /** Отправка системного сообщения всем игрокам. */
    private void broadcast(Component message) {
        MinecraftServer srv = this.server;
        if (srv == null) return;
        for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    /**
     * Помечает whitelist и цикл невалидными после перезагрузки lagsweep.toml.
     * Регистрируется на шине мода из LagSweepMod.
     */
    public static class ConfigListener {
        private final SweepEvents events;

        public ConfigListener(SweepEvents events) {
            this.events = events;
        }

        @SubscribeEvent
        public void onConfigReload(ModConfigEvent.Reloading event) {
            if (event.getConfig().getModId().equals(LagSweepMod.MODID)) {
                events.whitelistDirty = true;
                events.resetCycle();
            }
        }
    }
}
