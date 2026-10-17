package dev.senob;

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

        itemManager.registerItem(redlineSpear);
        itemManager.registerItem(draculaPlate);
        itemManager.registerItem(mythicalArmorSet);

        // Internal standard listeners for items
        Bukkit.getPluginManager().registerEvents(redlineSpear, this);

        // 4. Register Command Router
        GravenCoreCommand gravencoreCmd = new GravenCoreCommand(this, redlineSpear, draculaPlate, mythicalArmorSet);
        Objects.requireNonNull(getCommand("gravencore")).setExecutor(gravencoreCmd);
        Objects.requireNonNull(getCommand("gravencore")).setTabCompleter(gravencoreCmd);

        getLogger().info("GravenCore enabled!");
    }
}