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


package plugily.projects.minigamesbox.classic.handlers.permissions;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import plugily.projects.minigamesbox.classic.PluginMain;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Tigerpanzer_02
 * <p>
 * Created at 09.10.2021
 */
public class PermissionsManager {

  private final PluginMain plugin;
  private final FileConfiguration config;
  private String joinFullPerm;
  private String joinPerm;
  private String forceStartPerm;
  private final Map<String, PermissionCategory> permissionCategories = new HashMap<>();

  public PermissionsManager(PluginMain plugin) {
    this.plugin = plugin;
    config = ConfigUtils.getConfig(plugin, "permissions");
    this.joinFullPerm = plugin.getPluginNamePrefixLong() + ".fullgames";
    this.joinPerm = plugin.getPluginNamePrefixLong() + ".join.<arena>";
    this.forceStartPerm = plugin.getPluginNamePrefixLong() + ".admin.forcestart";
    setupPermissions();
    loadCustomPermissions();
  }

  public void loadCustomPermissions() {
    PermissionCategory.getPermissionsCategories().forEach(this::loadPermissionCategory);
  }

  public String getJoinFullGames() {
    return joinFullPerm;
  }

  private void setJoinFullGames(String joinFullGames) {
    joinFullPerm = joinFullGames;
  }

  public String getJoinPerm() {
    return joinPerm;
  }

  private void setJoinPerm(String joinPerm) {
    this.joinPerm = joinPerm;
  }

  public void setForceStartPerm(String forceStartPerm) {
    this.forceStartPerm = forceStartPerm;
  }

  public String getForceStart() {
    return forceStartPerm;
  }


  /**
   * Returns permission value
   *
   * @param key option to get value from
   * @return PermissionCategory
   */
  public PermissionCategory getPermissionCategory(String key) {
    if(!permissionCategories.containsKey(key)) {
      throw new IllegalStateException("Permission category with key " + key + " does not exist");
    }
    return permissionCategories.get(key);
  }

  /**
   * Returns PermissionCategoryMap
   *
   * @param key option to get value from
   * @return Map<String, Integer> permissionCategoryMap
   */
  public Map<String, Integer> getPermissionCategoryMap(String key) {
    if(!permissionCategories.containsKey(key)) {
      throw new IllegalStateException("Permission category with key " + key + " does not exist");
    }
    return permissionCategories.get(key).getCustomPermissions();
  }

  /**
   * Returns permission value
   *
   * @param key option to get value from
   * @return PermissionCategory
   */
  public int getPermissionCategoryValue(String key, Player player) {
    if(!permissionCategories.containsKey(key)) {
      throw new IllegalStateException("Permission category with key " + key + " does not exist");
    }
    int value = 0;
    for(Map.Entry<String, Integer> perm : getPermissionCategoryMap(key).entrySet()) {
      if(player.hasPermission(perm.getKey())) {
        value += perm.getValue();
      }
    }
    return value;
  }

  /**
   * Register a new permissions category
   *
   * @param key                The key of the permission category
   * @param permissionCategory Contains the name and the map
   */
  public void registerPermissionCategory(String key, PermissionCategory permissionCategory) {
    if(permissionCategories.containsKey(key)) {
      throw new IllegalStateException("Permissions category with key " + key + " was already registered");
    }
    loadPermissionCategory(key, permissionCategory);
    permissionCategories.put(key, permissionCategory);
  }

  private void loadPermissionCategory(String key, PermissionCategory permissionCategory) {
    ConfigurationSection section = config.getConfigurationSection(permissionCategory.getPath());
    if(section != null) {
      Map<String, Integer> customPermissions = new HashMap<>();
      for(String path : section.getKeys(false)) {
        customPermissions.put(path, section.getInt(path));
        plugin.getDebugger().debug("Loaded custom permission {0} for category {1}", path, key);
      }
      permissionCategories.put(key, new PermissionCategory(permissionCategory.getPath(), customPermissions, permissionCategory.isProtected()));
    }
  }

  /**
   * Remove permissions category that are not protected
   *
   * @param name The name of the category
   */
  public void unregisterPermissionCategory(String name) {
    PermissionCategory permissionCategory = permissionCategories.get(name);
    if(permissionCategory == null) {
      return;
    }
    if(permissionCategory.isProtected()) {
      throw new IllegalStateException("Protected permission category cannot be removed!");
    }
    permissionCategories.remove(name);
  }

  public Map<String, PermissionCategory> getPermissionsCategories() {
    return Collections.unmodifiableMap(permissionCategories);
  }

  private void setupPermissions() {
    setJoinFullGames(config.getString("Basic.Full-Games", plugin.getPluginNamePrefixLong() + ".fullgames"));
    setJoinPerm(config.getString("Basic.Join", plugin.getPluginNamePrefixLong() + ".join.<arena>"));
    setForceStartPerm(config.getString("Basic.Forcestart", plugin.getPluginNamePrefixLong() + ".admin.forcestart"));
    plugin.getDebugger().debug("Basic permissions registered");
  }

}
