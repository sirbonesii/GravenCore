package dev.senob;

import dev.senob.commands.BountyCommand;
import dev.senob.commands.GravenCoreCommand;
import dev.senob.commands.InvSeeCommand;
import dev.senob.core.CooldownManager;
import dev.senob.core.GravenConfig;
import dev.senob.guis.GUIManager;
import dev.senob.home_system.HomeSystem;
import dev.senob.items.AlterOfDespair;
import dev.senob.items.DraculaPlate;
import dev.senob.items.ItemManager;
import dev.senob.items.MythicalArmorSet;
import dev.senob.items.CelerityBoots;
import dev.senob.items.RedlineAltar;
import dev.senob.items.RedlineSpear;
import dev.senob.listeners.AttributeHider;
import dev.senob.listeners.EnchantSyncListener;
import dev.senob.listeners.CombatLogger;
import dev.senob.listeners.DurabilityLoreListener;
import dev.senob.listeners.EnderChestEx;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;

public class GravenCore extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1. Initialize Universal Managers
        CooldownManager cooldownManager = new CooldownManager(this);
        GravenConfig.init(this);
        ItemManager itemManager = new ItemManager();
        GUIManager guiManager = new GUIManager();
        Bukkit.getPluginManager().registerEvents(guiManager, this);
        Bukkit.getPluginManager().registerEvents(itemManager, this);

        // 2. Register standard features
        new HomeSystem(this);
        Bukkit.getPluginManager().registerEvents(new CombatLogger(this, cooldownManager), this);
        Bukkit.getPluginManager().registerEvents(new EnderChestEx(this), this);
        Bukkit.getPluginManager().registerEvents(new AttributeHider(), this);

        BountyCommand bountyCmd = new BountyCommand();
        Objects.requireNonNull(getCommand("bounty")).setExecutor(bountyCmd);
        Objects.requireNonNull(getCommand("bounty")).setTabCompleter(bountyCmd);

        // 3. Initialize Custom Items (Passing in the Cooldown Manager)
        RedlineSpear redlineSpear = new RedlineSpear(this, cooldownManager);
        DraculaPlate draculaPlate = new DraculaPlate(this, cooldownManager);
        MythicalArmorSet mythicalArmorSet = new MythicalArmorSet(this);
        CelerityBoots celerityBoots = new CelerityBoots(this, cooldownManager);
        AlterOfDespair alterOfDespair = new AlterOfDespair(this);

        itemManager.registerItem(redlineSpear);
        itemManager.registerItem(draculaPlate);
        itemManager.registerItem(mythicalArmorSet);
        itemManager.registerItem(celerityBoots);

        DurabilityLoreListener durabilityLore = new DurabilityLoreListener(this);
        durabilityLore.track(mythicalArmorSet.idKey());
        durabilityLore.track(celerityBoots.idKey());
        Bukkit.getPluginManager().registerEvents(durabilityLore, this);

        // Internal standard listeners for items
        Bukkit.getPluginManager().registerEvents(redlineSpear, this);
        Bukkit.getPluginManager().registerEvents(mythicalArmorSet, this);
        Bukkit.getPluginManager().registerEvents(celerityBoots, this);
        Bukkit.getPluginManager().registerEvents(new RedlineAltar(this, redlineSpear), this);
        Bukkit.getPluginManager().registerEvents(new EnchantSyncListener(this, draculaPlate, mythicalArmorSet, celerityBoots), this);
        Bukkit.getPluginManager().registerEvents(alterOfDespair, this);

        // 4. Register Command Router
        GravenCoreCommand gravencoreCmd = new GravenCoreCommand(this, redlineSpear, draculaPlate, mythicalArmorSet, celerityBoots, alterOfDespair);
        Objects.requireNonNull(getCommand("gravencore")).setExecutor(gravencoreCmd);
        Objects.requireNonNull(getCommand("gravencore")).setTabCompleter(gravencoreCmd);

        InvSeeCommand invseeCmd = new InvSeeCommand(this);
        Bukkit.getPluginManager().registerEvents(invseeCmd, this);
        Objects.requireNonNull(getCommand("invsee")).setExecutor(invseeCmd);
        Objects.requireNonNull(getCommand("invsee")).setTabCompleter(invseeCmd);

        getLogger().info("GravenCore enabled!");
    }

    @Override
    public void onDisable() {
        // Never leave the server stuck at a slowed tick rate after a reload/stop mid-slow
        CelerityBoots.cancelActiveSlow();
        getLogger().info("GravenCore disabled!");
    }
}