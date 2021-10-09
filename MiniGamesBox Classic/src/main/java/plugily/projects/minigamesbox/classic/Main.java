/*
 * MiniGamesBox - Library box with massive content that could be seen as minigames core.
 * Copyright (C)  2021  Plugily Projects - maintained by Tigerpanzer_02 and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package plugily.projects.minigamesbox.classic;

import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.jetbrains.annotations.TestOnly;
import plugily.projects.minigamesbox.classic.api.StatsStorage;
import plugily.projects.minigamesbox.classic.handlers.permissions.PermissionsManager;
import plugily.projects.minigamesbox.classic.handlers.holiday.HolidayManager;
import plugily.projects.minigamesbox.classic.handlers.hologram.LeaderboardRegistry;
import plugily.projects.minigamesbox.classic.handlers.items.SpecialItemManager;
import plugily.projects.minigamesbox.classic.handlers.party.PartyHandler;
import plugily.projects.minigamesbox.classic.handlers.party.PartySupportInitializer;
import plugily.projects.minigamesbox.classic.handlers.placeholder.PlaceholderManager;
import plugily.projects.minigamesbox.classic.handlers.powerup.PowerupRegistry;
import plugily.projects.minigamesbox.classic.handlers.reward.RewardsFactory;
import plugily.projects.minigamesbox.classic.handlers.sign.SignManager;
import plugily.projects.minigamesbox.classic.kits.KitMenuHandler;
import plugily.projects.minigamesbox.classic.kits.KitRegistry;
import plugily.projects.minigamesbox.classic.preferences.ConfigPreferences;
import plugily.projects.minigamesbox.classic.user.UserManager;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;
import plugily.projects.minigamesbox.classic.utils.helper.BukkitHelper;
import plugily.projects.minigamesbox.classic.utils.hologram.HologramManager;
import plugily.projects.minigamesbox.classic.utils.misc.Debugger;
import plugily.projects.minigamesbox.classic.utils.misc.MessageUtils;
import plugily.projects.minigamesbox.classic.utils.misc.MiscUtils;
import plugily.projects.minigamesbox.classic.utils.serialization.InventorySerializer;
import plugily.projects.minigamesbox.classic.utils.services.ServiceRegistry;
import plugily.projects.minigamesbox.classic.utils.services.UpdateChecker;
import plugily.projects.minigamesbox.classic.utils.services.exception.ExceptionLogHandler;
import plugily.projects.minigamesbox.classic.utils.services.metrics.Metrics;
import plugily.projects.minigamesbox.classic.utils.version.ServerVersion;
import plugily.projects.minigamesbox.classic.utils.version.events.EventsInitializer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

/**
 * @author Tigerpanzer_02
 * <p>
 * Created at 12.09.2021
 */
public class Main extends JavaPlugin {

  private final String pluginPrefix = "[" + getDescription().getName() + "] ";
  private MessageUtils messageUtils;
  private ConfigPreferences configPreferences;
  private PartyHandler partyHandler;
  private Debugger debugger;
  private UserManager userManager;
  private StatsStorage statsStorage;
  private BukkitHelper bukkitHelper;
  private SpecialItemManager specialItemManager;
  private RewardsFactory rewardsHandler;
  private KitMenuHandler kitMenuHandler;
  private HologramManager hologramManager;
  private SignManager signManager;
  private PowerupRegistry powerupRegistry;
  private LeaderboardRegistry leaderboardRegistry;
  private HolidayManager holidayManager;
  private PlaceholderManager placeholderManager;
  private boolean forceDisable = false;
  private ExceptionLogHandler exceptionLogHandler;
  private PermissionsManager permissionsManager;

  @TestOnly
  public Main() {
    super();
  }

  @TestOnly
  protected Main(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
    super(loader, description, dataFolder, file);
  }

  @Override
  public void onEnable() {
    long start = System.currentTimeMillis();

    //checking startup
    if(!validateIfPluginShouldStart()) {
      return;
    }
    if(!ServiceRegistry.registerService(this)) {
      debugger.sendConsoleMsg(pluginPrefix + "&cSadly, we can't connect to Plugily Projects Services. Some functions may won't work. e.g. Translations, Automatic Error Report");
    }
    exceptionLogHandler = new ExceptionLogHandler(this);
    messageUtils = new MessageUtils(this);

    //run file creation
    saveDefaultConfig();
    setupFiles();

    configPreferences = new ConfigPreferences(this);

    //check debug mode
    debugger = new Debugger(this, getDescription().getVersion().contains("debug") || getConfig().getBoolean("Debug"));
    debugger.debug("[System] Initialization start");
    if(getDescription().getVersion().contains("debug") || getConfig().getBoolean("Developer-Mode")) {
      debugger.deepDebug(true);
      debugger.debug(Level.FINE, "Deep debug enabled");

      getConfig().getStringList("Performance-Listenable").forEach(debugger::monitorPerformance);
    }

    //check for updates
    checkUpdate(ConfigUtils.getConfig(this, "/internal/data").getInt("PluginId.Spigot", 0));

    //start metrics
    setupPluginMetrics(ConfigUtils.getConfig(this, "/internal/data").getInt("PluginId.BStats", 0));

    //initialize default classes
    initializeDefaultClasses();

    //send console message
    MiscUtils.sendStartUpMessage(this);

    //finished initial start
    debugger.debug("[System] Initialization finished took {0}ms", System.currentTimeMillis() - start);

  }

  public void initializeDefaultClasses() {
    bukkitHelper = new BukkitHelper(this);
    partyHandler = new PartySupportInitializer().initialize(this);
    statsStorage = new StatsStorage(this);
    userManager = new UserManager(this);
    new EventsInitializer(this);

    specialItemManager = new SpecialItemManager(this);
    kitMenuHandler = new KitMenuHandler(this);
    new KitRegistry(this);
    rewardsHandler = new RewardsFactory(this);
    hologramManager = new HologramManager(this);
    powerupRegistry = new PowerupRegistry(this);
    signManager = new SignManager(this);
    ArenaRegistry.registerArenas();
    signManager.loadSigns();
    signManager.updateSigns();

    if(configPreferences.getOption("HOLOGRAMS")) {
      if(!new File(getDataFolder(), "internal/holograms_data.yml").exists()) {
        new File(getDataFolder().getName() + "/internal").mkdir();
      }
      leaderboardRegistry = new LeaderboardRegistry(this);
    }

    holidayManager = new HolidayManager(this);
    if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      debugger.debug(pluginPrefix + "Hooking into PlaceholderAPI");
      placeholderManager = new PlaceholderManager(this);
    }
    permissionsManager = new PermissionsManager(this);
  }

  private boolean validateIfPluginShouldStart() {
    try {
      Class.forName("org.spigotmc.SpigotConfig");
    } catch(Exception e) {
      messageUtils.thisVersionIsNotSupported();
      debugger.sendConsoleMsg(pluginPrefix + "&cYour server software is not supported by " + getDescription().getName() + "!");
      debugger.sendConsoleMsg(pluginPrefix + "&cWe support Spigot and Spigot forks only! Shutting off...");
      forceDisable = true;
      getServer().getPluginManager().disablePlugin(this);
      return false;
    }
    if(ServerVersion.Version.isCurrentLower(ServerVersion.Version.v1_8_R3)) {
      messageUtils.thisVersionIsNotSupported();
      debugger.sendConsoleMsg(pluginPrefix + "&cYour server version is not supported by " + getDescription().getName() + "!");
      debugger.sendConsoleMsg(pluginPrefix + "&cSadly, we must shut off. Maybe you consider changing your server version?");
      forceDisable = true;
      getServer().getPluginManager().disablePlugin(this);
      return false;
    }
    return true;
  }

  private final ArrayList<String> fileNames = new ArrayList<>(Arrays.asList("arenas", "rewards", "stats", "special_items", "mysql"));

  public ArrayList<String> getFileNames() {
    return fileNames;
  }

  //some plugins need to register files such as "kits"
  public void addFileName(String filename) {
    if(fileNames.contains(filename)) {
      throw new IllegalStateException("Filename " + filename + " already on the list!");
    }
    fileNames.add(filename);
  }

  private void setupFiles() {
    for(String fileName : fileNames) {
      File file = new File(getDataFolder(), fileName + ".yml");
      if(!file.exists()) {
        saveResource(fileName + ".yml", false);
      }
    }
  }

  private void checkUpdate(int pluginUpdateId) {
    if(!getConfig().getBoolean("Update-Notifier.Enabled", true)) {
      return;
    }
    UpdateChecker.init(this, pluginUpdateId).requestUpdateCheck().whenComplete((result, exception) -> {
      if(!result.requiresUpdate()) {
        return;
      }
      if(result.getNewestVersion().contains("b")) {
        if(getConfig().getBoolean("Update-Notifier.Notify-Beta-Versions", true)) {
          debugger.sendConsoleMsg(pluginPrefix + "Your software is ready for update! However it's a BETA VERSION. Proceed with caution.");
          debugger.sendConsoleMsg(pluginPrefix + "Current version %old%, latest version %new%".replace("%old%", getDescription().getVersion()).replace("%new%",
              result.getNewestVersion()));
        }
        return;
      }
      messageUtils.updateIsHere();
      debugger.sendConsoleMsg(pluginPrefix + "&aYour " + getDescription().getName() + " plugin is outdated! Download it to keep with latest changes and fixes.");
      debugger.sendConsoleMsg(pluginPrefix + "&aDisable this option in config.yml if you wish.");
      debugger.sendConsoleMsg(pluginPrefix + "&eCurrent version: &c" + getDescription().getVersion() + " &eLatest version: &a" + result.getNewestVersion());
    });
  }

  public String getPluginPrefix() {
    return pluginPrefix;
  }

  public Debugger getDebugger() {
    return debugger;
  }

  public ConfigPreferences getConfigPreferences() {
    return configPreferences;
  }

  public PartyHandler getPartyHandler() {
    return partyHandler;
  }

  public MessageUtils getMessageUtils() {
    return messageUtils;
  }

  public UserManager getUserManager() {
    return userManager;
  }

  public StatsStorage getStatsStorage() {
    return statsStorage;
  }

  public BukkitHelper getBukkitHelper() {
    return bukkitHelper;
  }

  public SpecialItemManager getSpecialItemManager() {
    return specialItemManager;
  }

  public HologramManager getHologramManager() {
    return hologramManager;
  }

  public PowerupRegistry getPowerupRegistry() {
    return powerupRegistry;
  }

  public SignManager getSignManager() {
    return signManager;
  }

  public RewardsFactory getRewardsHandler() {
    return rewardsHandler;
  }

  public LeaderboardRegistry getLeaderboardRegistry() {
    return leaderboardRegistry;
  }

  public HolidayManager getHolidayManager() {
    return holidayManager;
  }

  public ExceptionLogHandler getExceptionLogHandler() {
    return exceptionLogHandler;
  }

  public KitMenuHandler getKitMenuHandler() {
    return kitMenuHandler;
  }

  public PlaceholderManager getPlaceholderManager() {
    return placeholderManager;
  }

  public PermissionsManager getPermissionsManager() {
    return permissionsManager;
  }

  private void setupPluginMetrics(int pluginMetricsId) {
    Metrics metrics = new Metrics(this, pluginMetricsId);

    metrics.addCustomChart(new Metrics.SimplePie("database_enabled", () -> String.valueOf(configPreferences
        .getOption("DATABASE"))));
    metrics.addCustomChart(new Metrics.SimplePie("locale_used", () -> LanguageManager.getPluginLocale().getPrefix()));
    metrics.addCustomChart(new Metrics.SimplePie("update_notifier", () -> {
      if(getConfig().getBoolean("Update-Notifier.Enabled", true)) {
        return getConfig().getBoolean("Update-Notifier.Notify-Beta-Versions", true) ? "Enabled with beta notifier" : "Enabled";
      }

      return getConfig().getBoolean("Update-Notifier.Notify-Beta-Versions", true) ? "Beta notifier only" : "Disabled";
    }));
  }


  @Override
  public void onDisable() {
    if(forceDisable) {
      return;
    }
    debugger.debug("System disable initialized");
    long start = System.currentTimeMillis();

    Bukkit.getLogger().removeHandler(exceptionLogHandler);
    for(Arena arena : ArenaRegistry.getArenas()) {
      arena.getScoreboardManager().stopAllScoreboards();

      for(Player player : arena.getPlayers()) {
        arena.teleportToEndLocation(player);
        player.setFlySpeed(0.1f);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getActivePotionEffects().forEach(pe -> player.removePotionEffect(pe.getType()));
        arena.doBarAction(Arena.BarAction.REMOVE, player);
        if(configPreferences.getOption(ConfigPreferences.Option.INVENTORY_MANAGER_ENABLED)) {
          InventorySerializer.loadInventory(this, player);
        }
      }

      arena.getMapRestorerManager().fullyRestoreArena();
    }
    userManager.getDatabase().disable();
    if(configPreferences.getOption("HOLOGRAMS")) {
      leaderboardRegistry.disableHolograms();
    }
    for(ArmorStand armorStand : hologramManager.getArmorStands()) {
      armorStand.remove();
      armorStand.setCustomNameVisible(false);
    }
    hologramManager.getArmorStands().clear();
    debugger.debug(pluginPrefix + "System disable finished took {0}ms", System.currentTimeMillis() - start);
  }

  /*
      old normal Main to be removed over time
   */

/*

  private BungeeManager bungeeManager;
  private ChatManager chatManager;

  private ArgumentsRegistry registry;






  private FileConfiguration languageConfig;

  private FileConfiguration entityUpgradesConfig;
  private EnemySpawnerRegistry enemySpawnerRegistry;

  private boolean forceDisable = false, holographicEnabled = false;


  public BungeeManager getBungeeManager() {
    return bungeeManager;
  }



  public KitMenuHandler getKitMenuHandler() {
    return kitMenuHandler;
  }



  public FileConfiguration getLanguageConfig() {
    return languageConfig;
  }

  public FileConfiguration getEntityUpgradesConfig() {
    return entityUpgradesConfig;
  }

  @Override
  public void onEnable() {


    Messages.init(this);
    LanguageManager.init(this);


    chatManager = new ChatManager(this);

    languageConfig = ConfigUtils.getConfig(this, "language");
    initializeClasses();

  }


  //order matters
  private void initializeClasses() {
    startInitiableClasses();

    ScoreboardLib.setPluginInstance(this);
    registry = new ArgumentsRegistry(this);
    new ArenaEvents(this);
    new SpectatorEvents(this);
    new QuitEvent(this);
    new JoinEvent(this);
    new ChatEvents(this);
    setupPluginMetrics();

    new Events(this);
    new LobbyEvents(this);
    new SpectatorItemEvents(this);





    enemySpawnerRegistry = new EnemySpawnerRegistry(this);

    User.cooldownHandlerTask();
    if(configPreferences.getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
      Debugger.debug("Bungee enabled");
      bungeeManager = new BungeeManager(this);
      new MiscEvents(this);
    }

    if(configPreferences.getOption(ConfigPreferences.Option.UPGRADES_ENABLED)) {
      entityUpgradesConfig = ConfigUtils.getConfig(this, "entity_upgrades");
      Upgrade.init(this);
      UpgradeBuilder.init(this);
      new EntityUpgradeMenu(this);
    }

    new DoorBreakListener(this);


    FastInvManager.register(this);
    MiscUtils.sendStartUpMessage(this, "VillageDefense", getDescription(), true, true);
  }

  private void startInitiableClasses() {
    StatsStorage.init(this);
    ArenaRegistry.init(this);
    Utils.init(this);
    CreatureUtils.init(this);
    User.init(this);
    ArenaManager.init(this);

    SetupInventory.init(this);
    ArenaUtils.init(this);
    Arena.init(this);
  }




  public ChatManager getChatManager() {
    return chatManager;
  }














  public ArgumentsRegistry getArgumentsRegistry() {
    return registry;
  }

  public EnemySpawnerRegistry getEnemySpawnerRegistry() {
    return enemySpawnerRegistry;
  }

*/
}
