package com.nnpg.glazed;

import com.nnpg.glazed.commands.DeathPosCommand;
import com.nnpg.glazed.commands.RemoverTestCommand;
import com.nnpg.glazed.modules.esp.*;
import com.nnpg.glazed.modules.main.*;
import com.nnpg.glazed.modules.pvp.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GlazedAddon extends MeteorAddon {

    public static final Category CATEGORY = new Category("Glazed", () -> new ItemStack(Items.CAKE));
    public static final Category esp = new Category("Glazed ESP ", () -> new ItemStack(Items.VINE));
    public static final Category pvp = new Category("Glazed PVP", () -> new ItemStack(Items.DIAMOND_SWORD));

    // read from the jar so gradle.properties is the only place you set it
    public static final String VERSION = resolveVersion();

    // change this when porting to a new mc version
    public static final String VERSION_CHECK_URL = "https://glazedclient.com/versions/normal26.1.2.txt";

    @Override
    public void onInitialize() {
        // commands
        Commands.add(new DeathPosCommand());
        Commands.add(new RemoverTestCommand());

        // esp
        Modules.get().add(new AdvancedStashFinder());
        Modules.get().add(new AmethystESP());
        Modules.get().add(new BedrockVoidESP());
        Modules.get().add(new BeehiveESP());
        Modules.get().add(new BlockNotifier());
        Modules.get().add(new ChunkFinder());
        Modules.get().add(new CollectibleESP());
        Modules.get().add(new CoveredHole());
        Modules.get().add(new DebrisLeakESP());
        Modules.get().add(new DeepslateESP());
        Modules.get().add(new DrownedTridentESP());
        Modules.get().add(new DripstoneESP());
        Modules.get().add(new HoleTunnelStairsESP());
        Modules.get().add(new InvisESP());
        Modules.get().add(new KelpESP());
        Modules.get().add(new LamaESP());
        Modules.get().add(new LightESP());
        Modules.get().add(new OneByOneHoles());
        Modules.get().add(new PillagerESP());
        Modules.get().add(new PistonESP());
        Modules.get().add(new RegionMap());
        Modules.get().add(new RotatedDeepslateESP());
        Modules.get().add(new SkeletonESP());
        Modules.get().add(new SweetBerryESP());
        Modules.get().add(new SpawnerNotifier());
        Modules.get().add(new VillagerESP());
        Modules.get().add(new VineESP());
        Modules.get().add(new WanderingESP());

        // main
        Modules.get().add(new AHSell());
        Modules.get().add(new AhShieldSeller());
        Modules.get().add(new AdminHud());
        Modules.get().add(new AdminList());
        Modules.get().add(new AutoLeave());
        Modules.get().add(new AutoPearlChain());
        Modules.get().add(new AutoRaidAfk());
        Modules.get().add(new AutoSell());
        Modules.get().add(new AutoSpawnerSell());
        Modules.get().add(new BoatSeller());
        Modules.get().add(new ChestAndShulkerStealer());
        Modules.get().add(new ChestSeller());
        Modules.get().add(new CoordSnapper());
        Modules.get().add(new EmergencyOrder());
        Modules.get().add(new EmergencySeller());
        Modules.get().add(new FakePay());
        Modules.get().add(new FakeScoreboard());
        Modules.get().add(new GlazedFreecam());
        Modules.get().add(new GlazedFreelook());
        Modules.get().add(new GodTrident());
        Modules.get().add(new HideScoreboard());
        Modules.get().add(new HomeReset());
        Modules.get().add(new InvSell());
        Modules.get().add(new IronAhRestocker());
        Modules.get().add(new NoBlockInteract());
        Modules.get().add(new OrderDropper());
        Modules.get().add(new PlayerDetection());
        Modules.get().add(new PlayerBypass());
        Modules.get().add(new PremiumTunnelBaseFinder());
        Modules.get().add(new RTPBaseFinder());
        Modules.get().add(new RTPEndBaseFinder());
        Modules.get().add(new RTPNetherBaseFinder());
        Modules.get().add(new RTPer());
        Modules.get().add(new RainNoti());
        Modules.get().add(new SlabCrafter());
        Modules.get().add(new SlabSeller());
        Modules.get().add(new SlabUltimate());
        Modules.get().add(new ShulkerDropper());
        Modules.get().add(new SpawnerDropper());
        Modules.get().add(new SpawnerOrder());
        Modules.get().add(new SpawnerProtect());
        Modules.get().add(new SpotifyHud());
        Modules.get().add(new TabDetector());
        Modules.get().add(new TpaMacro());

        // pvp
        Modules.get().add(new AimAssist());
        Modules.get().add(new AnchorMacro());
        Modules.get().add(new AntiTrap());
        Modules.get().add(new AutoDoubleHand());
        Modules.get().add(new AutoInvTotem());
        Modules.get().add(new CrystalMacro());
        Modules.get().add(new HoverTotem());
        Modules.get().add(new KeyPearl());
        Modules.get().add(new LungeMacro());
        Modules.get().add(new ShieldBreaker());
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        MyScreen.checkVersionOnServerJoin();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        MyScreen.resetSessionCheck();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(esp);
        Modules.registerCategory(pvp);
    }

    @Override
    public String getPackage() {
        return "com.nnpg.glazed";
    }

    private static String resolveVersion() {
        return FabricLoader.getInstance().getModContainer("glazed")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .map(full -> {
                // mod_version looks like "1.21.11-n-17", we just want the 17
                int marker = full.lastIndexOf("-n-");
                return marker >= 0 ? full.substring(marker + 3) : full;
            })
            .orElse("0");
    }
}
