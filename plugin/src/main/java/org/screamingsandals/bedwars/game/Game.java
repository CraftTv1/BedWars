package org.screamingsandals.bedwars.game;

import com.onarandombox.MultiverseCore.api.Core;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import misat11.lib.nms.Hologram;
import misat11.lib.nms.NMSUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.ArenaTime;
import org.screamingsandals.bedwars.api.Region;
import org.screamingsandals.bedwars.api.RunningTeam;
import org.screamingsandals.bedwars.api.boss.BossBar;
import org.screamingsandals.bedwars.api.boss.BossBar19;
import org.screamingsandals.bedwars.api.boss.StatusBar;
import org.screamingsandals.bedwars.api.events.*;
import org.screamingsandals.bedwars.api.game.ConfigVariables;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.api.game.GameStore;
import org.screamingsandals.bedwars.api.special.SpecialItem;
import org.screamingsandals.bedwars.api.upgrades.UpgradeRegistry;
import org.screamingsandals.bedwars.api.upgrades.UpgradeStorage;
import org.screamingsandals.bedwars.api.utils.DelayFactory;
import org.screamingsandals.bedwars.boss.BossBarSelector;
import org.screamingsandals.bedwars.boss.XPBar;
import org.screamingsandals.bedwars.inventories.TeamSelectorInventory;
import org.screamingsandals.bedwars.region.FlatteningRegion;
import org.screamingsandals.bedwars.region.LegacyRegion;
import org.screamingsandals.bedwars.statistics.PlayerStatistic;
import org.screamingsandals.bedwars.utils.*;
import org.screamingsandals.lib.signmanager.SignBlock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static misat11.lib.lang.I.*;

public class Game implements org.screamingsandals.bedwars.api.game.Game {
    private String name;
    private Location pos1;
    private Location pos2;
    private Location lobbySpawn;
    private Location specSpawn;
    private List<Team> teams = new ArrayList<>();
    private List<ItemSpawner> spawners = new ArrayList<>();
    private Map<Player, RespawnProtection> respawnProtectionMap = new HashMap<>();
    private int lobbyTime;
    private int gameTime;
    private int minPlayers;
    private List<GamePlayer> players = new ArrayList<>();
    private World world;
    private List<GameStore> gameStore = new ArrayList<>();
    private ArenaTime arenaTime = ArenaTime.WORLD;
    private WeatherType arenaWeather = null;
    private BarColor lobbyBossBarColor = null;
    private BarColor gameBossBarColor = null;
    private GameConfigManager gameConfigManager = new GameConfigManager(this);
    private File gameFile;

    public boolean gameStartItem;
    private boolean preServerRestart = false;
    public static final int POST_GAME_WAITING = 3;

    // STATUS
    private GameStatus previousStatus = GameStatus.DISABLED;
    private GameStatus status = GameStatus.DISABLED;
    private GameStatus afterRebuild = GameStatus.WAITING;
    private int countdown = -1, previousCountdown = -1;
    private int calculatedMaxPlayers;
    private BukkitTask task;
    private List<CurrentTeam> teamsInGame = new ArrayList<>();
    private Region region = Main.isLegacy() ? new LegacyRegion() : new FlatteningRegion();
    private TeamSelectorInventory teamSelectorInventory;
    private Scoreboard gameScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    private StatusBar statusbar;
    private Map<Location, ItemStack[]> usedChests = new HashMap<>();
    private List<SpecialItem> activeSpecialItems = new ArrayList<>();
    private List<DelayFactory> activeDelays = new ArrayList<>();
    private List<Hologram> createdHolograms = new ArrayList<>();
    private Map<ItemSpawner, Hologram> countdownHolograms = new HashMap<>();
    private Map<GamePlayer, Inventory> fakeEnderChests = new HashMap<>();

    private Game() {

    }

    public String getName() {
        return name;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        if (this.world == null) {
            this.world = world;
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    public void setLobbySpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }

    public int getLobbyTime() {
        return lobbyTime;
    }

    public void setLobbyTime(int pauseCountdown) {
        this.lobbyTime = pauseCountdown;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public boolean checkMinPlayers() {
        return players.size() >= getMinPlayers();
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int countPlayers() {
        return this.players.size();
    }

    public List<GameStore> getGameStores() {
        return gameStore;
    }

    public Location getSpecSpawn() {
        return specSpawn;
    }

    public void setSpecSpawn(Location specSpawn) {
        this.specSpawn = specSpawn;
    }

    public int getGameTime() {
        return gameTime;
    }

    public void setGameTime(int gameTime) {
        this.gameTime = gameTime;
    }

    @Override
    public org.screamingsandals.bedwars.api.Team getTeamFromName(String name) {
        Team team = null;
        for (Team t : getTeams()) {
            if (t.getName().equalsIgnoreCase(name)) {
                team = t;
            }
        }
        return team;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public List<ItemSpawner> getSpawners() {
        return spawners;
    }

    public void setGameStores(List<GameStore> gameStore) {
        this.gameStore = gameStore;
    }

    public TeamSelectorInventory getTeamSelectorInventory() {
        return teamSelectorInventory;
    }

    public boolean isBlockAddedDuringGame(Location loc) {
        return status == GameStatus.RUNNING && region.isBlockAddedDuringGame(loc);
    }

    public boolean blockPlace(GamePlayer player, Block block, BlockState replaced, ItemStack itemInHand) {
        if (status != GameStatus.RUNNING) {
            return false; // ?
        }
        if (player.isSpectator) {
            return false;
        }
        if (Main.isFarmBlock(block.getType())) {
            return true;
        }
        if (!GameCreator.isInArea(block.getLocation(), pos1, pos2)) {
            return false;
        }

        BedwarsPlayerBuildBlock event = new BedwarsPlayerBuildBlock(this, player.player, getPlayerTeam(player), block,
                itemInHand, replaced);
        Main.getInstance().getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        if (replaced.getType() != Material.AIR) {
            if (Main.isBreakableBlock(replaced.getType())) {
                region.putOriginalBlock(block.getLocation(), replaced);
            } else if (region.isLiquid(replaced.getType())) {
                region.putOriginalBlock(block.getLocation(), replaced);
            } else {
                return false;
            }
        }
        region.addBuiltDuringGame(block.getLocation());

        return true;
    }

    public boolean blockBreak(GamePlayer player, Block block, BlockBreakEvent event) {
        if (status != GameStatus.RUNNING) {
            return false; // ?
        }
        if (player.isSpectator) {
            return false;
        }
        if (Main.isFarmBlock(block.getType())) {
            return true;
        }
        if (!GameCreator.isInArea(block.getLocation(), pos1, pos2)) {
            return false;
        }

        BedwarsPlayerBreakBlock breakEvent = new BedwarsPlayerBreakBlock(this, player.player, getPlayerTeam(player),
                block);
        Main.getInstance().getServer().getPluginManager().callEvent(breakEvent);

        if (breakEvent.isCancelled()) {
            return false;
        }

        if (region.isBlockAddedDuringGame(block.getLocation())) {
            region.removeBlockBuiltDuringGame(block.getLocation());

            if (block.getType() == Material.ENDER_CHEST) {
                CurrentTeam team = getTeamOfChest(block);
                if (team != null) {
                    team.removeTeamChest(block);

                    for (GamePlayer gp : team.players) {
                        mpr("game.info.player.chests.broken").send(gp.player);
                    }

                    if (breakEvent.isDrops()) {
                        event.setDropItems(false);
                        player.player.getInventory().addItem(new ItemStack(Material.ENDER_CHEST));
                    }
                }
            }

            if (!breakEvent.isDrops()) {
                try {
                    event.setDropItems(false);
                } catch (Throwable tr) {
                    block.setType(Material.AIR);
                }
            }
            return true;
        }

        Location loc = block.getLocation();
        if (region.isBedBlock(block.getState())) {
            if (!region.isBedHead(block.getState())) {
                loc = region.getBedNeighbor(block).getLocation();
            }
        }
        if (isTargetBlock(loc)) {
            if (region.isBedBlock(block.getState())) {
                if (getPlayerTeam(player).teamInfo.bed.equals(loc)) {
                    return false;
                }
                bedDestroyed(loc, player.player, true);
                region.putOriginalBlock(block.getLocation(), block.getState());
                if (block.getLocation().equals(loc)) {
                    Block neighbor = region.getBedNeighbor(block);
                    region.putOriginalBlock(neighbor.getLocation(), neighbor.getState());
                } else {
                    region.putOriginalBlock(loc, region.getBedNeighbor(block).getState());
                }
                try {
                    event.setDropItems(false);
                } catch (Throwable tr) {
                    if (region.isBedHead(block.getState())) {
                        region.getBedNeighbor(block).setType(Material.AIR);
                    } else {
                        block.setType(Material.AIR);
                    }
                }
                return true;
            } else {
                if (getPlayerTeam(player).teamInfo.bed.equals(loc)) {
                    return false;
                }
                bedDestroyed(loc, player.player, false);
                region.putOriginalBlock(loc, block.getState());
                try {
                    event.setDropItems(false);
                } catch (Throwable tr) {
                    block.setType(Material.AIR);
                }
                return true;
            }
        }
        if (Main.isBreakableBlock(block.getType())) {
            region.putOriginalBlock(block.getLocation(), block.getState());
            return true;
        }
        return false;
    }

    private boolean isTargetBlock(Location loc) {
        for (CurrentTeam team : teamsInGame) {
            if (team.isBed && team.teamInfo.bed.equals(loc)) {
                return true;
            }
        }
        return false;
    }

    public Region getRegion() {
        return region;
    }

    public CurrentTeam getPlayerTeam(GamePlayer player) {
        for (CurrentTeam team : teamsInGame) {
            if (team.players.contains(player)) {
                return team;
            }
        }
        return null;
    }

    public CurrentTeam getCurrentTeamFromTeam(org.screamingsandals.bedwars.api.Team team) {
        for (CurrentTeam currentTeam : teamsInGame) {
            if (currentTeam.teamInfo == team) {
                return currentTeam;
            }
        }
        return null;
    }

    private void bedDestroyed(Location loc, Player broker, boolean isItBedBlock) {
        if (status == GameStatus.RUNNING) {
            for (CurrentTeam team : teamsInGame) {
                if (team.teamInfo.bed.equals(loc)) {
                    team.isBed = false;
                    updateScoreboard();
                    for (GamePlayer player : players) {
                        String mainTitle = m(isItBedBlock ? "game.info.global.titles.bed_is_destroyed"
                                : "game.info.global.titles.target_is_destroyed")
                                .get()
                                .replace("%team%",
                                        team.teamInfo.color.chatColor + team.teamInfo.name);
                        String subTitle = m(getPlayerTeam(player) == team ? "game.info.global.titles.bed_is_destroyed_subtitle_for_victim"
                                : "game.info.global.titles.bed_is_destroyed_subtitle").get();

                        MiscUtils.sendTitle(player.player, mainTitle, subTitle);
                        mpr(isItBedBlock ? "game.info.global.titles.bed_is_destroyed"
                                : "game.info.global.titles.target_is_destroyed")
                                .replace("%team%", team.teamInfo.color.chatColor + team.teamInfo.name).send(player.player);

                        SpawnEffects.spawnEffect(this, player.player, "game-effects.beddestroy");
                        Sounds.playSound(player.player, player.player.getLocation(),
                                Main.getConfigurator().config.getString("sounds.on_bed_destroyed"),
                                Sounds.ENTITY_ENDER_DRAGON_GROWL, 1, 1);
                    }

                    if (team.hasBedHolo()) {
                        team.getBedHolo().setLine(0,
                                m(isItBedBlock ? "holograms.game.beds.protect_your_bed_destroyed"
                                        : "holograms.game.beds.protect_your_target_destroyed").get());
                        team.getBedHolo().addViewers(team.getConnectedPlayers());
                    }

                    if (team.hasProtectHolo()) {
                        team.getProtectHolo().destroy();
                    }

                    BedwarsTargetBlockDestroyedEvent targetBlockDestroyed = new BedwarsTargetBlockDestroyedEvent(this,
                            broker, team);
                    Main.getInstance().getServer().getPluginManager().callEvent(targetBlockDestroyed);

                    if (Main.isPlayerStatisticsEnabled()) {
                        PlayerStatistic statistic = Main.getPlayerStatisticsManager().getStatistic(broker);
                        statistic.setCurrentDestroyedBeds(statistic.getCurrentDestroyedBeds() + 1);
                        statistic.setCurrentScore(statistic.getCurrentScore()
                                + Main.getConfigurator().config.getInt("statistics.scores.bed-destroy", 25));
                    }

                    dispatchRewardCommands("player-destroy-bed", broker,
                            Main.getConfigurator().config.getInt("statistics.scores.bed-destroy", 25));
                }
            }
        }
    }

    public void internalJoinPlayer(GamePlayer player) {
        BedwarsPlayerJoinEvent joinEvent = new BedwarsPlayerJoinEvent(this, player.player);
        Main.getInstance().getServer().getPluginManager().callEvent(joinEvent);

        if (joinEvent.isCancelled()) {
            String message = joinEvent.getCancelMessage();
            if (message != null && !message.equals("")) {
                player.player.sendMessage(message);
            }
            player.changeGame(null);
            return;
        }

        boolean isEmpty = players.isEmpty();
        if (!players.contains(player)) {
            players.add(player);
        }
        updateSigns();

        if (Main.isPlayerStatisticsEnabled()) {
            // Load
            Main.getPlayerStatisticsManager().getStatistic(player.player);
        }

        if (arenaTime.time >= 0) {
            player.player.setPlayerTime(arenaTime.time, false);
        }

        if (arenaWeather != null) {
            player.player.setPlayerWeather(arenaWeather);
        }

        mpr("game.info.global.joined").replace("name", player.player.getDisplayName()).replace("players", players.size())
                .replace("maxplayers", calculatedMaxPlayers).send(getConnectedPlayers());

        if (status == GameStatus.WAITING) {
            player.teleport(lobbySpawn);
            SpawnEffects.spawnEffect(this, player.player, "game-effects.lobbyjoin");

            if (getConfigManager().get(ConfigVariables.AUTOBALANCE_ON_JOIN)) {
                joinRandomTeam(player);
            }

            if (getConfigManager().get(ConfigVariables.TEAM_SELECTOR)) {
                int compassPosition = Main.getConfigurator().config.getInt("hotbar.selector", 0);
                if (compassPosition >= 0 && compassPosition <= 8) {
                    ItemStack compass = Main.getConfigurator().readDefinedItem("jointeam", "COMPASS");
                    ItemMeta metaCompass = compass.getItemMeta();
                    metaCompass.setDisplayName(m("game.items.team_selector").get());
                    compass.setItemMeta(metaCompass);
                    player.player.getInventory().setItem(compassPosition, compass);
                }
            }

            int leavePosition = Main.getConfigurator().config.getInt("hotbar.leave", 8);
            if (leavePosition >= 0 && leavePosition <= 8) {
                ItemStack leave = Main.getConfigurator().readDefinedItem("leavegame", "SLIME_BALL");
                ItemMeta leaveMeta = leave.getItemMeta();
                leaveMeta.setDisplayName(m("game.items.leave").get());
                leave.setItemMeta(leaveMeta);
                player.player.getInventory().setItem(leavePosition, leave);
            }

            if (player.player.hasPermission(Permissions.VIP.permission)
                    || player.player.hasPermission(Permissions.VIP_START_ITEM.permission)) {
                int vipPosition = Main.getConfigurator().config.getInt("hotbar.start", 1);
                if (vipPosition >= 0 && vipPosition <= 8) {
                    ItemStack startGame = Main.getConfigurator().readDefinedItem("startgame", "DIAMOND");
                    ItemMeta startGameMeta = startGame.getItemMeta();
                    startGameMeta.setDisplayName(m("game.items.start_game").get());
                    startGame.setItemMeta(startGameMeta);

                    player.player.getInventory().setItem(vipPosition, startGame);
                }
            }

            if (isEmpty) {
                runTask();
            } else {
                statusbar.addPlayer(player.player);
            }
        } else {
            makeSpectator(player, true);
            createdHolograms.forEach(holo -> holo.addViewer(player.player));
        }

        BedwarsPlayerJoinedEvent joinedEvent = new BedwarsPlayerJoinedEvent(this, getPlayerTeam(player), player.player);
        Main.getInstance().getServer().getPluginManager().callEvent(joinedEvent);
    }

    public void internalLeavePlayer(GamePlayer gamePlayer) {
        if (status == GameStatus.DISABLED) {
            return;
        }

        BedwarsPlayerLeaveEvent playerLeaveEvent = new BedwarsPlayerLeaveEvent(this, gamePlayer.player,
                getPlayerTeam(gamePlayer));
        Main.getInstance().getServer().getPluginManager().callEvent(playerLeaveEvent);

        String message = mpr("game.info.global.leaved").replace("%name%", gamePlayer.player.getDisplayName()).get()
                .replace("%players%", Integer.toString(players.size()))
                .replaceAll("%maxplayers%", Integer.toString(calculatedMaxPlayers));

        if (!preServerRestart) {
            for (GamePlayer p : players) {
                p.player.sendMessage(message);
            }
        }

        players.remove(gamePlayer);
        updateSigns();

        if (status == GameStatus.WAITING) {
            SpawnEffects.spawnEffect(this, gamePlayer.player, "game-effects.lobbyleave");
        }

        statusbar.removePlayer(gamePlayer.player);
        createdHolograms.forEach(holo -> holo.removeViewer(gamePlayer.player));

        gamePlayer.player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        if (Main.getConfigurator().config.getBoolean("mainlobby.enabled")
                && !Main.getConfigurator().config.getBoolean("bungee.enabled")) {
            Location mainLobbyLocation = MiscUtils.readLocationFromString(
                    Bukkit.getWorld(Main.getConfigurator().config.getString("mainlobby.world")),
                    Main.getConfigurator().config.getString("mainlobby.location"));
            gamePlayer.teleport(mainLobbyLocation);
        }

        if (status == GameStatus.RUNNING || status == GameStatus.WAITING) {
            CurrentTeam team = getPlayerTeam(gamePlayer);
            if (team != null) {
                team.players.remove(gamePlayer);
                if (status == GameStatus.WAITING) {
                    team.getScoreboardTeam().removeEntry(gamePlayer.player.getName());
                    if (team.players.isEmpty()) {
                        teamsInGame.remove(team);
                        team.getScoreboardTeam().unregister();
                    }
                } else {
                    updateScoreboard();
                }
            }
        }

        if (Main.isPlayerStatisticsEnabled()) {
            PlayerStatistic statistic = Main.getPlayerStatisticsManager().getStatistic(gamePlayer.player);
            Main.getPlayerStatisticsManager().storeStatistic(statistic);

            Main.getPlayerStatisticsManager().unloadStatistic(gamePlayer.player);
        }

        if (players.isEmpty()) {
            if (!preServerRestart) {
                BedWarsPlayerLastLeaveEvent playerLastLeaveEvent = new BedWarsPlayerLastLeaveEvent(this, gamePlayer.player,
                        getPlayerTeam(gamePlayer));
                Main.getInstance().getServer().getPluginManager().callEvent(playerLastLeaveEvent);
            }

            if (status != GameStatus.WAITING) {
                afterRebuild = GameStatus.WAITING;
                updateSigns();
                rebuild();
            } else {
                status = GameStatus.WAITING;
                cancelTask();
            }
            countdown = -1;
            if (gameScoreboard.getObjective("display") != null) {
                gameScoreboard.getObjective("display").unregister();
            }
            if (gameScoreboard.getObjective("lobby") != null) {
                gameScoreboard.getObjective("lobby").unregister();
            }
            gameScoreboard.clearSlot(DisplaySlot.SIDEBAR);
            for (CurrentTeam team : teamsInGame) {
                team.getScoreboardTeam().unregister();
            }
            teamsInGame.clear();
            for (GameStore store : gameStore) {
                LivingEntity villager = store.kill();
                if (villager != null) {
                    Main.unregisterGameEntity(villager);
                }
            }
        }
    }

    public static Game loadGame(File file) {
        if (!file.exists()) {
            return null;
        }
        FileConfiguration configMap = new YamlConfiguration();
        try {
            configMap.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
            return null;
        }

        Game game = new Game();
        game.gameFile = file;
        game.name = configMap.getString("name");
        game.lobbyTime = configMap.getInt("pauseCountdown");
        game.gameTime = configMap.getInt("gameTime");
        String worldName = configMap.getString("world");
        game.world = Bukkit.getWorld(worldName);
        if (game.world == null) {
            if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
                Bukkit.getConsoleSender().sendMessage("§c[B§fW] §cWorld " + worldName
                        + " was not found, but we found Multiverse-Core, so we will try to load this world.");

                Core multiverse = (Core) Bukkit.getPluginManager().getPlugin("Multiverse-Core");
                MVWorldManager manager = multiverse.getMVWorldManager();
                if (manager.loadWorld(worldName)) {
                    Bukkit.getConsoleSender().sendMessage("§c[B§fW] §aWorld " + worldName
                            + " was succesfully loaded with Multiverse-Core, continue in arena loading.");

                    game.world = Bukkit.getWorld(worldName);
                } else {
                    Bukkit.getConsoleSender().sendMessage("§c[B§fW] §cArena " + game.name
                            + " can't be loaded, because world " + worldName + " is missing!");
                    return null;
                }
            } else {
                Bukkit.getConsoleSender().sendMessage(
                        "§c[B§fW] §cArena " + game.name + " can't be loaded, because world " + worldName + " is missing!");
                return null;
            }
        }
        game.pos1 = MiscUtils.readLocationFromString(game.world, configMap.getString("pos1"));
        game.pos2 = MiscUtils.readLocationFromString(game.world, configMap.getString("pos2"));
        game.specSpawn = MiscUtils.readLocationFromString(game.world, configMap.getString("specSpawn"));
        String spawnWorld = configMap.getString("lobbySpawnWorld");
        World lobbySpawnWorld = Bukkit.getWorld(spawnWorld);
        if (lobbySpawnWorld == null) {
            if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
                Bukkit.getConsoleSender().sendMessage("§c[B§fW] §cWorld " + spawnWorld
                        + " was not found, but we found Multiverse-Core, so we will try to load this world.");

                Core multiverse = (Core) Bukkit.getPluginManager().getPlugin("Multiverse-Core");
                MVWorldManager manager = multiverse.getMVWorldManager();
                if (manager.loadWorld(spawnWorld)) {
                    Bukkit.getConsoleSender().sendMessage("§c[B§fW] §aWorld " + spawnWorld
                            + " was succesfully loaded with Multiverse-Core, continue in arena loading.");

                    lobbySpawnWorld = Bukkit.getWorld(spawnWorld);
                } else {
                    Bukkit.getConsoleSender().sendMessage("§c[B§fW] §cArena " + game.name
                            + " can't be loaded, because world " + spawnWorld + " is missing!");
                    return null;
                }
            } else {
                Bukkit.getConsoleSender().sendMessage(
                        "§c[B§fW] §cArena " + game.name + " can't be loaded, because world " + spawnWorld + " is missing!");
                return null;
            }
        }
        game.lobbySpawn = MiscUtils.readLocationFromString(lobbySpawnWorld, configMap.getString("lobbySpawn"));
        game.minPlayers = configMap.getInt("minPlayers", 2);
        if (configMap.isSet("teams")) {
            for (String teamN : configMap.getConfigurationSection("teams").getKeys(false)) {
                ConfigurationSection team = configMap.getConfigurationSection("teams").getConfigurationSection(teamN);
                Team t = new Team();
                t.newColor = team.getBoolean("isNewColor", false);
                t.color = TeamColor.valueOf(MiscUtils.convertColorToNewFormat(team.getString("color"), t));
                t.name = teamN;
                t.bed = MiscUtils.readLocationFromString(game.world, team.getString("bed"));
                t.maxPlayers = team.getInt("maxPlayers");
                t.spawn = MiscUtils.readLocationFromString(game.world, team.getString("spawn"));
                t.game = game;

                t.newColor = true;
                game.teams.add(t);
            }
        }
        if (configMap.isSet("spawners")) {
            List<Map<String, Object>> spawners = (List<Map<String, Object>>) configMap.getList("spawners");
            for (Map<String, Object> spawner : spawners) {
                ItemSpawner sa = new ItemSpawner(
                        MiscUtils.readLocationFromString(game.world, (String) spawner.get("location")),
                        Main.getSpawnerType(((String) spawner.get("type")).toLowerCase()),
                        (String) spawner.get("customName"), ((Boolean) spawner.getOrDefault("hologramEnabled", true)),
                        ((Number) spawner.getOrDefault("startLevel", 1)).doubleValue(),
                        game.getTeamFromName((String) spawner.get("team")),
                        (int) spawner.getOrDefault("maxSpawnedResources", -1));
                game.spawners.add(sa);
            }
        }
        if (configMap.isSet("stores")) {
            List<Object> stores = (List<Object>) configMap.getList("stores");
            for (Object store : stores) {
                if (store instanceof Map) {
                    Map<String, String> map = (Map<String, String>) store;
                    game.gameStore.add(new GameStore(MiscUtils.readLocationFromString(game.world, map.get("loc")),
                            map.get("shop"), "true".equals(map.getOrDefault("parent", "true")),
                            EntityType.valueOf(map.getOrDefault("type", "VILLAGER").toUpperCase()),
                            map.getOrDefault("name", ""), map.containsKey("name")));
                } else if (store instanceof String) {
                    game.gameStore.add(new GameStore(MiscUtils.readLocationFromString(game.world, (String) store), null,
                            true, EntityType.VILLAGER, "", false));
                }
            }
        }
        
        if (configMap.isSet("constant")) {
        	Map<String, Boolean> constantMap = new HashMap<>();
        	ConfigurationSection constant = configMap.getConfigurationSection("constant");
        	for (String key : constant.getKeys(false)) {
        		Object get = constant.get(key);
        		if (get instanceof String) {
        			if (((String) get).equalsIgnoreCase("true")) {
        				constantMap.put(key, true);
        			} else if (((String) get).equalsIgnoreCase("false")) {
        				constantMap.put(key, false);
        			}
        		} else if (get instanceof Boolean) {
        			constantMap.put(key, (boolean) get);
        		}
        	}
        	game.gameConfigManager.changesMap().putAll(constantMap);
        }

        game.arenaTime = ArenaTime.valueOf(configMap.getString("arenaTime", ArenaTime.WORLD.name()).toUpperCase());
        game.arenaWeather = loadWeather(configMap.getString("arenaWeather", "default").toUpperCase());

        try {
            game.lobbyBossBarColor = loadBossBarColor(
                    configMap.getString("lobbyBossBarColor", "default").toUpperCase());
            game.gameBossBarColor = loadBossBarColor(configMap.getString("gameBossBarColor", "default").toUpperCase());
        } catch (Throwable t) {
            // We're using 1.8
        }

        Main.addGame(game);
        game.start();
        Bukkit.getConsoleSender().sendMessage("§c[B§fW] §aArena §f" + game.name + "§a loaded!");
        return game;
    }

    public static WeatherType loadWeather(String weather) {
        try {
            return WeatherType.valueOf(weather);
        } catch (Exception e) {
            return null;
        }
    }

    public static BarColor loadBossBarColor(String color) {
        try {
            return BarColor.valueOf(color);
        } catch (Exception e) {
            return null;
        }
    }

    public void saveToConfig() {
        File parent = gameFile.getParentFile();
        if (!parent.exists()) {
        	parent.mkdirs();
        }
        if (!gameFile.exists()) {
            try {
            	gameFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        FileConfiguration configMap = new YamlConfiguration();
        configMap.set("name", name);
        configMap.set("pauseCountdown", lobbyTime);
        configMap.set("gameTime", gameTime);
        configMap.set("world", world.getName());
        configMap.set("pos1", MiscUtils.setLocationToString(pos1));
        configMap.set("pos2", MiscUtils.setLocationToString(pos2));
        configMap.set("specSpawn", MiscUtils.setLocationToString(specSpawn));
        configMap.set("lobbySpawn", MiscUtils.setLocationToString(lobbySpawn));
        configMap.set("lobbySpawnWorld", lobbySpawn.getWorld().getName());
        configMap.set("minPlayers", minPlayers);
        if (!teams.isEmpty()) {
            for (Team t : teams) {
                configMap.set("teams." + t.name + ".isNewColor", t.isNewColor());
                configMap.set("teams." + t.name + ".color", t.color.name());
                configMap.set("teams." + t.name + ".maxPlayers", t.maxPlayers);
                configMap.set("teams." + t.name + ".bed", MiscUtils.setLocationToString(t.bed));
                configMap.set("teams." + t.name + ".spawn", MiscUtils.setLocationToString(t.spawn));
            }
        }
        List<Map<String, Object>> nS = new ArrayList<>();
        for (ItemSpawner spawner : spawners) {
            Map<String, Object> spawnerMap = new HashMap<>();
            spawnerMap.put("location", MiscUtils.setLocationToString(spawner.loc));
            spawnerMap.put("type", spawner.type.getConfigKey());
            spawnerMap.put("customName", spawner.customName);
            spawnerMap.put("startLevel", spawner.startLevel);
            spawnerMap.put("hologramEnabled", spawner.hologramEnabled);
            if (spawner.getTeam() != null) {
                spawnerMap.put("team", spawner.getTeam().getName());
            } else {
                spawnerMap.put("team", null);
            }
            spawnerMap.put("maxSpawnedResources", spawner.maxSpawnedResources);
            nS.add(spawnerMap);
        }
        configMap.set("spawners", nS);
        if (!gameStore.isEmpty()) {
            List<Map<String, String>> nL = new ArrayList<>();
            for (GameStore store : gameStore) {
                Map<String, String> map = new HashMap<>();
                map.put("loc", MiscUtils.setLocationToString(store.getStoreLocation()));
                map.put("shop", store.getShopFile());
                map.put("parent", store.getUseParent() ? "true" : "false");
                map.put("type", store.getEntityType().name());
                if (store.isShopCustomName()) {
                    map.put("name", store.getShopCustomName());
                }
                nL.add(map);
            }
            configMap.set("stores", nL);
        }
        
        Map<String, Boolean> constants = gameConfigManager.changesMap();
        
        for (Map.Entry<String, Boolean> constant : constants.entrySet()) {
            configMap.set("constant." + constant.getKey(), (boolean) constant.getValue());
        }

        configMap.set("arenaTime", arenaTime.name());
        configMap.set("arenaWeather", arenaWeather == null ? "default" : arenaWeather.name());

        try {
            configMap.set("lobbyBossBarColor", lobbyBossBarColor == null ? "default" : lobbyBossBarColor.name());
            configMap.set("gameBossBarColor", gameBossBarColor == null ? "default" : gameBossBarColor.name());
        } catch (Throwable t) {
            // We're using 1.8
        }

        try {
            configMap.save(gameFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Game createGame(String name) {
        Game game = new Game();
        game.name = name;
        game.lobbyTime = 60;
        game.gameTime = 3600;
        game.minPlayers = 2;
        game.gameFile = new File(new File(Main.getInstance().getDataFolder(), "arenas"), name + ".yml");

        return game;
    }

    public void start() {
        if (status == GameStatus.DISABLED) {
            status = GameStatus.WAITING;
            countdown = -1;
            calculatedMaxPlayers = 0;
            for (Team team : teams) {
                calculatedMaxPlayers += team.maxPlayers;
            }
            new BukkitRunnable() {
                public void run() {
                    updateSigns();
                }
            }.runTask(Main.getInstance());

            if (Main.getConfigurator().config.getBoolean("bossbar.use-xp-bar", false)) {
                statusbar = new XPBar();
            } else {
                statusbar = BossBarSelector.getBossBar();
            }
        }
    }

    public void stop() {
        if (status == GameStatus.DISABLED) {
            return; // Game is already stopped
        }
        List<GamePlayer> clonedPlayers = (List<GamePlayer>) ((ArrayList<GamePlayer>) players).clone();
        for (GamePlayer p : clonedPlayers)
            p.changeGame(null);
        if (status != GameStatus.REBUILDING) {
            status = GameStatus.DISABLED;
            updateSigns();
        } else {
            afterRebuild = GameStatus.DISABLED;
        }
    }

    public void joinToGame(Player player) {
        if (status == GameStatus.DISABLED) {
            return;
        }

        if (status == GameStatus.REBUILDING) {
            if (isBungeeEnabled()) {
                BungeeUtils.movePlayerToBungeeServer(player, false);
                BungeeUtils.sendPlayerBungeeMessage(player,
                        mpr("game.info.player.rebuilding").replace("%arena%", Game.this.name).get());
            } else {
                mpr("game.info.player.rebuilding").replace("%arena%", this.name).send(player);
            }
            return;
        }

        if ((status == GameStatus.RUNNING || status == GameStatus.GAME_END_CELEBRATING)
                && !getOriginalOrInheritedSpectatorJoin()) {
            if (isBungeeEnabled()) {
                BungeeUtils.movePlayerToBungeeServer(player, false);
                BungeeUtils.sendPlayerBungeeMessage(player,
                        mpr("game.info.player.already_running").replace("%arena%", Game.this.name).get());
            } else {
                mpr("game.info.player.already_running").replace("%arena%", this.name).send(player);
            }
            return;
        }

        if (players.size() >= calculatedMaxPlayers && status == GameStatus.WAITING) {
            if (Main.getPlayerGameProfile(player).canJoinFullGame()) {
                List<GamePlayer> withoutVIP = getPlayersWithoutVIP();

                if (withoutVIP.size() == 0) {
                    mpr("game.info.player.vip.full").send(player);
                    return;
                }

                GamePlayer kickPlayer;
                if (withoutVIP.size() == 1) {
                    kickPlayer = withoutVIP.get(0);
                } else {
                    kickPlayer = withoutVIP.get(MiscUtils.randInt(0, players.size() - 1));
                }

                if (isBungeeEnabled()) {
                    BungeeUtils.sendPlayerBungeeMessage(kickPlayer.player,
                            mpr("game.info.player.vip.kicked_by_vip").replace("%arena%", Game.this.name).get());
                } else {
                    mpr("game.info.player.vip.kicked_by_vip").replace("%arena%", this.name).send(kickPlayer.player);
                }
                kickPlayer.changeGame(null);
            } else {
                if (isBungeeEnabled()) {
                    BungeeUtils.sendPlayerBungeeMessage(player,
                            mpr("game.info.player.full").replace("%arena%", Game.this.name).get());
                    BungeeUtils.movePlayerToBungeeServer(player, false);
                } else {
                    mpr("game.info.player.full").replace("%arena%", this.name).send(player);
                }
                return;
            }
        }

        GamePlayer gPlayer = Main.getPlayerGameProfile(player);
        gPlayer.changeGame(this);
    }

    public void leaveFromGame(Player player) {
        if (status == GameStatus.DISABLED) {
            return;
        }
        if (Main.isPlayerInGame(player)) {
            GamePlayer gPlayer = Main.getPlayerGameProfile(player);

            if (gPlayer.getGame() == this) {
                gPlayer.changeGame(null);
                if (status == GameStatus.RUNNING || status == GameStatus.GAME_END_CELEBRATING) {
                    updateScoreboard();
                }
            }
        }
    }

    public CurrentTeam getCurrentTeamByTeam(Team team) {
        for (CurrentTeam current : teamsInGame) {
            if (current.teamInfo == team) {
                return current;
            }
        }
        return null;
    }

    public Team getFirstTeamThatIsntInGame() {
        for (Team team : teams) {
            if (getCurrentTeamByTeam(team) == null) {
                return team;
            }
        }
        return null;
    }

    public CurrentTeam getTeamWithLowestPlayers() {
        CurrentTeam lowest = null;

        for (CurrentTeam team : teamsInGame) {
            if (lowest == null) {
                lowest = team;
            }

            if (lowest.players.size() > team.players.size()) {
                lowest = team;
            }
        }

        return lowest;
    }

    public List<GamePlayer> getPlayersInTeam(Team team) {
        CurrentTeam currentTeam = null;
        for (CurrentTeam cTeam : teamsInGame) {
            if (cTeam.teamInfo == team) {
                currentTeam = cTeam;
            }
        }

        if (currentTeam != null) {
            return currentTeam.players;
        } else {
            return new ArrayList<>();
        }
    }

    private void internalTeamJoin(GamePlayer gamePlayer, Team teamForJoin) {
        CurrentTeam currentTeam = null;
        for (CurrentTeam t : teamsInGame) {
            if (t.teamInfo == teamForJoin) {
                currentTeam = t;
                break;
            }
        }

        CurrentTeam previousTeam = getPlayerTeam(gamePlayer);
        BedwarsPlayerJoinTeamEvent event = new BedwarsPlayerJoinTeamEvent(currentTeam, gamePlayer.player, this, previousTeam);
        Main.getInstance().getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        if (currentTeam == null) {
            currentTeam = new CurrentTeam(teamForJoin, this);
            org.bukkit.scoreboard.Team scoreboardTeam = gameScoreboard.getTeam(teamForJoin.name);
            if (scoreboardTeam == null) {
                scoreboardTeam = gameScoreboard.registerNewTeam(teamForJoin.name);
            }
            if (!Main.isLegacy()) {
                scoreboardTeam.setColor(teamForJoin.color.chatColor);
            } else {
                scoreboardTeam.setPrefix(teamForJoin.color.chatColor.toString());
            }
            scoreboardTeam.setAllowFriendlyFire(getOriginalOrInheritedFriendlyfire());

            currentTeam.setScoreboardTeam(scoreboardTeam);
        }
        if (previousTeam == currentTeam) {
            mpr("game.info.player.team.already_selected")
                    .replace("%team%", teamForJoin.color.chatColor + teamForJoin.name)
                    .replace("%players%", Integer.toString(currentTeam.players.size()))
                    .replace("%maxplayers%", Integer.toString(currentTeam.teamInfo.maxPlayers)).send(gamePlayer.player);
            return;
        }
        if (currentTeam.players.size() >= currentTeam.teamInfo.maxPlayers) {
            if (previousTeam != null) {
                mpr("game.info.player.team.full")
                        .replace("%team%", teamForJoin.color.chatColor + teamForJoin.name)
                        .replace("%oldteam%", previousTeam.teamInfo.color.chatColor + previousTeam.teamInfo.name).send(gamePlayer.player);
            } else {
                mpr("game.info.player.team.full")
                        .replace("%team%", teamForJoin.color.chatColor + teamForJoin.name).send(gamePlayer.player);
            }
            return;
        }
        if (previousTeam != null) {
            previousTeam.players.remove(gamePlayer);
            previousTeam.getScoreboardTeam().removeEntry(gamePlayer.player.getName());
            if (previousTeam.players.isEmpty()) {
                teamsInGame.remove(previousTeam);
                previousTeam.getScoreboardTeam().unregister();
            }
        }
        currentTeam.players.add(gamePlayer);
        currentTeam.getScoreboardTeam().addEntry(gamePlayer.player.getName());
        mpr("game.info.player.team.selected").replace("%team%", teamForJoin.color.chatColor + teamForJoin.name)
                .replace("%players%", Integer.toString(currentTeam.players.size()))
                .replace("%maxplayers%", Integer.toString(currentTeam.teamInfo.maxPlayers))
                .send(gamePlayer.player);

        if (getOriginalOrInheritedAddWoolToInventoryOnJoin()) {
            int colorPosition = Main.getConfigurator().config.getInt("hotbar.color", 1);
            if (colorPosition >= 0 && colorPosition <= 8) {
                ItemStack stack = teamForJoin.color.getWool();
                ItemMeta stackMeta = stack.getItemMeta();
                stackMeta.setDisplayName(teamForJoin.color.chatColor + teamForJoin.name);
                stack.setItemMeta(stackMeta);
                gamePlayer.player.getInventory().setItem(colorPosition, stack);
            }
        }

        if (getOriginalOrInheritedColoredLeatherByTeamInLobby()) {
            ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta meta = (LeatherArmorMeta) chestplate.getItemMeta();
            meta.setColor(teamForJoin.color.leatherColor);
            chestplate.setItemMeta(meta);
            gamePlayer.player.getInventory().setChestplate(chestplate);
        }

        if (!teamsInGame.contains(currentTeam)) {
            teamsInGame.add(currentTeam);
        }
    }

    public void joinRandomTeam(GamePlayer gamePlayer) {
        Team teamForJoin;
        if (teamsInGame.size() < 2) {
            teamForJoin = getFirstTeamThatIsntInGame();
        } else {
            CurrentTeam current = getTeamWithLowestPlayers();
            if (current.players.size() >= current.getMaxPlayers()) {
                teamForJoin = getFirstTeamThatIsntInGame();
            } else {
                teamForJoin = current.teamInfo;
            }
        }

        if (teamForJoin == null) {
            return;
        }

        internalTeamJoin(gamePlayer, teamForJoin);
    }

    public Location makeSpectator(GamePlayer gamePlayer, boolean leaveItem) {
        Player player = gamePlayer.player;
        gamePlayer.isSpectator = true;

        new BukkitRunnable() {

            @Override
            public void run() {
                gamePlayer.teleport(specSpawn);
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setGameMode(GameMode.SPECTATOR);
            }
        }.runTask(Main.getInstance());

        if (leaveItem) {
            int leavePosition = Main.getConfigurator().config.getInt("hotbar.leave", 8);
            if (leavePosition >= 0 && leavePosition <= 8) {
                ItemStack leave = Main.getConfigurator().readDefinedItem("leavegame", "SLIME_BALL");
                ItemMeta leaveMeta = leave.getItemMeta();
                leaveMeta.setDisplayName(m("game.items.leave").get());
                leave.setItemMeta(leaveMeta);
                gamePlayer.player.getInventory().setItem(leavePosition, leave);
            }
        }
        return specSpawn;

    }

    @SuppressWarnings("unchecked")
    public void makePlayerFromSpectator(GamePlayer gamePlayer) {
        Player player = gamePlayer.player;
        Game game = gamePlayer.getGame();
        RunningTeam runningTeam = game.getTeamOfPlayer(player);

        gamePlayer.isSpectator = false;
        new BukkitRunnable() {
            @Override
            public void run() {
                gamePlayer.teleport(runningTeam.getTeamSpawn());
                player.setAllowFlight(false);
                player.setFlying(false);
                player.setGameMode(GameMode.SURVIVAL);

                if (gamePlayer.getGame().getOriginalOrInheritedPlayerRespawnItems()) {
                    List<ItemStack> givedGameStartItems = (List<ItemStack>) Main.getConfigurator().config
                            .getList("gived-player-respawn-items");
                    for (ItemStack stack : givedGameStartItems) {
                        gamePlayer.player.getInventory().addItem(Main.applyColor(runningTeam.getColor(), stack));
                    }
                }
            }
        }.runTask(Main.getInstance());
    }

    public void setBossbarProgress(int count, int max) {
        double progress = (double) count / (double) max;
        statusbar.setProgress(progress);
        if (statusbar instanceof XPBar) {
            XPBar xpbar = (XPBar) statusbar;
            xpbar.setSeconds(count);
        }
    }

    @SuppressWarnings("unchecked")
    public void run() {
        // Phase 1: Check if game is running
        if (status == GameStatus.DISABLED) { // Game is not running, why cycle is still running?
            cancelTask();
            return;
        }

        // Phase 2: If this is first tick, prepare waiting lobby
        if (countdown == -1 && status == GameStatus.WAITING) {
            previousCountdown = countdown = lobbyTime;
            previousStatus = GameStatus.WAITING;
            statusbar.setProgress(0);
            statusbar.setVisible(getOriginalOrInheritedLobbyBossbar());
            for (GamePlayer p : players) {
                statusbar.addPlayer(p.player);
            }
            if (statusbar instanceof BossBar) {
                BossBar bossbar = (BossBar) statusbar;
                bossbar.setMessage(m("game.info.global.bossbar.waiting").get());
                if (bossbar instanceof BossBar19) {
                    BossBar19 bossbar19 = (BossBar19) bossbar;
                    bossbar19.setColor(lobbyBossBarColor != null ? lobbyBossBarColor
                            : BarColor.valueOf(Main.getConfigurator().config.getString("bossbar.lobby.color")));
                    bossbar19
                            .setStyle(BarStyle.valueOf(Main.getConfigurator().config.getString("bossbar.lobby.style")));
                }
            }
            if (teamSelectorInventory == null) {
                teamSelectorInventory = new TeamSelectorInventory(Main.getInstance(), this);
            }
            updateSigns();
        }

        // Phase 3: Prepare information about next tick for tick event and update
        // bossbar with scoreboard
        int nextCountdown = countdown;
        GameStatus nextStatus = status;

        if (status == GameStatus.WAITING) {
            // Game start item
            if (gameStartItem) {
                if (players.size() >= getMinPlayers()) {
                    for (GamePlayer player : players) {
                        if (getPlayerTeam(player) == null) {
                            joinRandomTeam(player);
                        }
                    }
                }
                if (players.size() > 1) {
                    countdown = 0;
                    gameStartItem = false;
                }
            }

            if (players.size() >= getMinPlayers()
                    && (getOriginalOrInheritedJoinRandomTeamAfterLobby() || teamsInGame.size() > 1)) {
                if (countdown == 0) {
                    nextCountdown = gameTime;
                    nextStatus = GameStatus.RUNNING;
                } else {
                    nextCountdown--;

                    if (countdown <= 10 && countdown >= 1 && countdown != previousCountdown) {
                        for (GamePlayer player : players) {
                            MiscUtils.sendTitle(player.player, ChatColor.YELLOW + Integer.toString(countdown), "");
                            Sounds.playSound(player.player, player.player.getLocation(),
                                    Main.getConfigurator().config.getString("sounds.on_countdown"), Sounds.UI_BUTTON_CLICK,
                                    1, 1);
                        }
                    }
                }
            } else {
                nextCountdown = countdown = lobbyTime;
            }
            setBossbarProgress(countdown, lobbyTime);
            updateLobbyScoreboard();
        } else if (status == GameStatus.RUNNING) {
            if (countdown == 0) {
                nextCountdown = POST_GAME_WAITING;
                nextStatus = GameStatus.GAME_END_CELEBRATING;
            } else {
                nextCountdown--;
            }
            setBossbarProgress(countdown, gameTime);
            updateScoreboardTimer();
        } else if (status == GameStatus.GAME_END_CELEBRATING) {
            if (countdown == 0) {
                nextStatus = GameStatus.REBUILDING;
                nextCountdown = 0;
            } else {
                nextCountdown--;
            }
            setBossbarProgress(countdown, POST_GAME_WAITING);
        }

        // Phase 4: Call Tick Event
        BedwarsGameTickEvent tick = new BedwarsGameTickEvent(this, previousCountdown, previousStatus, countdown, status,
                nextCountdown, nextStatus);
        Bukkit.getPluginManager().callEvent(tick);

        // Phase 5: Update Previous information
        previousCountdown = countdown;
        previousStatus = status;

        // Phase 6: Process tick
        // Phase 6.1: If status changed
        if (status != tick.getNextStatus()) {
            // Phase 6.1.1: Prepare game if next status is RUNNING
            if (tick.getNextStatus() == GameStatus.RUNNING) {
                BedwarsGameStartEvent startE = new BedwarsGameStartEvent(this);
                Main.getInstance().getServer().getPluginManager().callEvent(startE);

                if (startE.isCancelled()) {
                    tick.setNextCountdown(lobbyTime);
                    tick.setNextStatus(GameStatus.WAITING);
                } else {

                    if (getOriginalOrInheritedJoinRandomTeamAfterLobby()) {
                        for (GamePlayer player : players) {
                            if (getPlayerTeam(player) == null) {
                                joinRandomTeam(player);
                            }
                        }
                    }

                    statusbar.setProgress(0);
                    statusbar.setVisible(getOriginalOrInheritedGameBossbar());
                    if (statusbar instanceof BossBar) {
                        BossBar bossbar = (BossBar) statusbar;
                        bossbar.setMessage(m("game.info.global.bossbar.running").get());
                        if (bossbar instanceof BossBar19) {
                            BossBar19 bossbar19 = (BossBar19) bossbar;
                            bossbar19.setColor(gameBossBarColor != null ? gameBossBarColor
                                    : BarColor.valueOf(Main.getConfigurator().config.getString("bossbar.game.color")));
                            bossbar19.setStyle(
                                    BarStyle.valueOf(Main.getConfigurator().config.getString("bossbar.game.style")));
                        }
                    }
                    if (teamSelectorInventory != null)
                        teamSelectorInventory.destroy();
                    teamSelectorInventory = null;
                    if (gameScoreboard.getObjective("lobby") != null) {
                        gameScoreboard.getObjective("lobby").unregister();
                    }
                    gameScoreboard.clearSlot(DisplaySlot.SIDEBAR);
                    updateSigns();
                    for (GameStore store : gameStore) {
                        LivingEntity villager = store.spawn();
                        if (villager != null) {
                            Main.registerGameEntity(villager, this);
                            NMSUtils.disableEntityAI(villager);
                        }
                    }

                    for (ItemSpawner spawner : spawners) {
                        UpgradeStorage storage = UpgradeRegistry.getUpgrade("spawner");
                        if (storage != null) {
                            storage.addUpgrade(this, spawner);
                        }
                    }

                    if (getOriginalOrInheritedSpawnerHolograms()) {
                        for (ItemSpawner spawner : spawners) {
                            if (spawner.getHologramEnabled()) {
                                Location loc = spawner.loc.clone().add(0,
                                        Main.getConfigurator().config.getDouble("spawner-holo-height", 0.25), 0);
                                Hologram holo = NMSUtils.spawnHologram(getConnectedPlayers(), loc,
                                        spawner.type.getItemBoldName());
                                createdHolograms.add(holo);
                                if (getOriginalOrInheritedSpawnerHologramsCountdown()) {
                                    holo.addLine(spawner.type.getInterval() < 2 ? m("game.spawners.every_second").get()
                                            : m("game.spawners.countdown").replace("%seconds%",
                                            Integer.toString(spawner.type.getInterval())).get());
                                    countdownHolograms.put(spawner, holo);
                                }
                            }
                        }
                    }

                    String gameStartTitle = m("game.info.player.titles.start").get();
                    String gameStartSubtitle = m("game.info.player.titles").replace("%arena%", this.name).get();
                    for (GamePlayer player : this.players) {
                        CurrentTeam team = getPlayerTeam(player);
                        player.player.getInventory().clear();
                        // Player still had armor on legacy versions
                        player.player.getInventory().setHelmet(null);
                        player.player.getInventory().setChestplate(null);
                        player.player.getInventory().setLeggings(null);
                        player.player.getInventory().setBoots(null);
                        MiscUtils.sendTitle(player.player, gameStartTitle, gameStartSubtitle);
                        if (team == null) {
                            makeSpectator(player, true);
                        } else {
                            player.teleport(team.teamInfo.spawn);
                            if (getOriginalOrInheritedGameStartItems()) {
                                List<ItemStack> givedGameStartItems = (List<ItemStack>) Main.getConfigurator().config
                                        .getList("gived-game-start-items");
                                assert givedGameStartItems != null;
                                for (ItemStack stack : givedGameStartItems) {
                                    player.player.getInventory().addItem(Main.applyColor(team.getColor(), stack));
                                }
                            }
                            SpawnEffects.spawnEffect(this, player.player, "game-effects.start");
                        }
                        Sounds.playSound(player.player, player.player.getLocation(),
                                Main.getConfigurator().config.getString("sounds.on_game_start"),
                                Sounds.ENTITY_PLAYER_LEVELUP, 1, 1);
                    }

                    if (getOriginalOrInheritedRemoveUnusedTargetBlocks()) {
                        for (Team team : teams) {
                            CurrentTeam ct = null;
                            for (CurrentTeam curt : teamsInGame) {
                                if (curt.teamInfo == team) {
                                    ct = curt;
                                    break;
                                }
                            }
                            if (ct == null) {
                                Location loc = team.bed;
                                Block block = team.bed.getBlock();
                                if (region.isBedBlock(block.getState())) {
                                    region.putOriginalBlock(block.getLocation(), block.getState());
                                    Block neighbor = region.getBedNeighbor(block);
                                    region.putOriginalBlock(neighbor.getLocation(), neighbor.getState());
                                    neighbor.setType(Material.AIR);
                                    block.setType(Material.AIR);
                                } else {
                                    region.putOriginalBlock(loc, block.getState());
                                    block.setType(Material.AIR);
                                }
                            }
                        }
                    }

                    if (getOriginalOrInheritedHoloAboveBed()) {
                        for (CurrentTeam team : teamsInGame) {
                            Block bed = team.teamInfo.bed.getBlock();
                            Location loc = team.teamInfo.bed.clone().add(0.5, 1.5, 0.5);
                            boolean isBlockTypeBed = region.isBedBlock(bed.getState());
                            List<Player> enemies = getConnectedPlayers();
                            enemies.removeAll(team.getConnectedPlayers());
                            Hologram holo = NMSUtils.spawnHologram(enemies, loc,
                                    m(isBlockTypeBed ? "hologram.game.beds.destroy_this_bed" : "hologram.game.beds.destroy_this_target")
                                            .replace("%teamcolor%", team.teamInfo.color.chatColor.toString())
                                            .get());
                            createdHolograms.add(holo);
                            team.setBedHolo(holo);
                            Hologram protectHolo = NMSUtils.spawnHologram(team.getConnectedPlayers(), loc,
                                    m(isBlockTypeBed ? "hologram.game.beds.protect_your_bed" : "hologram.game.beds.protect_your_target")
                                            .replace("%teamcolor%", team.teamInfo.color.chatColor.toString())
                                            .get());
                            createdHolograms.add(protectHolo);
                            team.setProtectHolo(protectHolo);
                        }
                    }
					
					// Check target blocks existence
					for (CurrentTeam team : teamsInGame) {
						Location targetLocation = team.getTargetBlock();
						if (targetLocation.getBlock().getType() == Material.AIR) {
							ItemStack stack = team.teamInfo.color.getWool();
							Block placedBlock = targetLocation.getBlock();
		                    placedBlock.setType(stack.getType());
		                    if (!Main.isLegacy()) {
			                    try {
			                        // The method is no longer in API, but in legacy versions exists
			                        Block.class.getMethod("setData", byte.class).invoke(placedBlock, (byte) stack.getDurability());
			                    } catch (Exception e) {
			                    }
		                    }
						}
					}

                    BedwarsGameStartedEvent startedEvent = new BedwarsGameStartedEvent(this);
                    Main.getInstance().getServer().getPluginManager().callEvent(startedEvent);
                    updateScoreboard();
                }
            }
            // Phase 6.2: If status is same as before
        } else {
            // Phase 6.2.1: On game tick (if not interrupted by a change of status)
            if (status == GameStatus.RUNNING && tick.getNextStatus() == GameStatus.RUNNING) {
                int runningTeams = 0;
                for (CurrentTeam t : teamsInGame) {
                    runningTeams += t.isAlive() ? 1 : 0;
                }
                if (runningTeams <= 1) {
                    if (runningTeams == 1) {
                        for (CurrentTeam t : teamsInGame) {
                            if (t.isAlive()) {
                                String time = getFormattedTimeLeft(gameTime - countdown);
                                String message = mpr("game.info.global.game_win")
                                        .replace("%team%", TeamColor.fromApiColor(t.getColor()).chatColor + t.getName())
                                        .replace("%time%", time).get();
                                String subtitle = m("game.info.global.game_win")
                                        .replace("%team%", TeamColor.fromApiColor(t.getColor()).chatColor + t.getName())
                                        .replace("%time%", time)
                                        .get();
                                boolean madeRecord = processRecord(t, gameTime - countdown);
                                for (GamePlayer player : players) {
                                    player.player.sendMessage(message);
                                    if (getPlayerTeam(player) == t) {
                                        MiscUtils.sendTitle(player.player, m("game.info.player.team.you_won").get(), subtitle);
                                        Main.depositPlayer(player.player, Main.getVaultWinReward());

                                        SpawnEffects.spawnEffect(this, player.player, "game-effects.end");

                                        if (Main.isPlayerStatisticsEnabled()) {
                                            PlayerStatistic statistic = Main.getPlayerStatisticsManager()
                                                    .getStatistic(player.player);
                                            statistic.setCurrentWins(statistic.getCurrentWins() + 1);
                                            statistic.setCurrentScore(statistic.getCurrentScore()
                                                    + Main.getConfigurator().config.getInt("statistics.scores.win", 50));

                                            if (madeRecord) {
                                                statistic.setCurrentScore(
                                                        statistic.getCurrentScore() + Main.getConfigurator().config
                                                                .getInt("statistics.scores.record", 100));
                                            }

                                            if (Main.isHologramsEnabled()) {
                                                Main.getHologramInteraction().updateHolograms(player.player);
                                            }

                                            if (Main.getConfigurator().config
                                                    .getBoolean("statistics.show-on-game-end")) {
                                                Main.getInstance().getServer().dispatchCommand(player.player,
                                                        "bw stats");
                                            }

                                        }

                                        if (Main.getConfigurator().config.getBoolean("rewards.enabled")) {
                                            final Player pl = player.player;
                                            new BukkitRunnable() {

                                                @Override
                                                public void run() {
                                                    if (Main.isPlayerStatisticsEnabled()) {
                                                        PlayerStatistic statistic = Main.getPlayerStatisticsManager()
                                                                .getStatistic(player.player);
                                                        Game.this.dispatchRewardCommands("player-win", pl,
                                                                statistic.getCurrentScore());
                                                    } else {
                                                        Game.this.dispatchRewardCommands("player-win", pl, 0);
                                                    }
                                                }

                                            }.runTaskLater(Main.getInstance(), (2 + Game.POST_GAME_WAITING) * 20); //TODO - make this configurable
                                        }
                                    } else {
                                        MiscUtils.sendTitle(player.player, m("game.info.player.team.you_lost").get(), subtitle);

                                        if (Main.isPlayerStatisticsEnabled() && Main.isHologramsEnabled()) {
                                            Main.getHologramInteraction().updateHolograms(player.player);
                                        }
                                    }
                                }
                                break;
                            }
                        }
                        tick.setNextCountdown(Game.POST_GAME_WAITING);
                        tick.setNextStatus(GameStatus.GAME_END_CELEBRATING);
                    } else {
                        tick.setNextStatus(GameStatus.REBUILDING);
                        tick.setNextCountdown(0);
                    }
                } else if (countdown != gameTime /* Prevent spawning resources on game start */) {
                    for (ItemSpawner spawner : spawners) {
                        CurrentTeam spawnerTeam = getCurrentTeamFromTeam(spawner.getTeam());
                        ItemSpawnerType type = spawner.type;
                        int cycle = type.getInterval();
                        /*
                         * Calculate resource spawn from elapsedTime, not from remainingTime/countdown
                         */
                        int elapsedTime = gameTime - countdown;

                        if (spawner.getHologramEnabled()) {
                            if (getOriginalOrInheritedSpawnerHolograms()
                                    && getOriginalOrInheritedSpawnerHologramsCountdown()
                                    && !spawner.spawnerIsFullHologram) {
                                if (cycle > 1) {
                                    int timeToSpawn = cycle - elapsedTime % cycle;
                                    countdownHolograms.get(spawner).setLine(1,
                                            m("game.spawners.countdown").replace("%seconds%", Integer.toString(timeToSpawn)).get());
                                } else if (spawner.rerenderHologram) {
                                    countdownHolograms.get(spawner).setLine(1, m("game.spawners.every_second").get());
                                    spawner.rerenderHologram = false;
                                }
                            }
                        }

                        if (spawnerTeam != null) {
                            if (getOriginalOrInheritedStopTeamSpawnersOnDie() && (spawnerTeam.isDead())) {
                                continue;
                            }
                        }

                        if ((elapsedTime % cycle) == 0) {
                            int calculatedStack = 1;
                            double currentLevel = spawner.getCurrentLevel();
                            calculatedStack = (int) currentLevel;

                            /* Allow half level */
                            if ((currentLevel % 1) != 0) {
                                int a = elapsedTime / cycle;
                                if ((a % 2) == 0) {
                                    calculatedStack++;
                                }
                            }

                            BedwarsResourceSpawnEvent resourceSpawnEvent = new BedwarsResourceSpawnEvent(this, spawner,
                                    type.getStack(calculatedStack));
                            Main.getInstance().getServer().getPluginManager().callEvent(resourceSpawnEvent);

                            if (resourceSpawnEvent.isCancelled()) {
                                continue;
                            }

                            ItemStack resource = resourceSpawnEvent.getResource();

                            resource.setAmount(spawner.nextMaxSpawn(resource.getAmount(), countdownHolograms.get(spawner)));

                            if (resource.getAmount() > 0) {
                                Location loc = spawner.getLocation().clone().add(0, 0.05, 0);
                                Item item = loc.getWorld().dropItem(loc, resource);
                                double spread = type.getSpread();
                                if (spread != 1.0) {
                                    item.setVelocity(item.getVelocity().multiply(spread));
                                }
                                item.setPickupDelay(0);
                                spawner.add(item);
                            }
                        }
                    }
                }
            }
        }

        // Phase 7: Update status and countdown for next tick
        countdown = tick.getNextCountdown();
        status = tick.getNextStatus();

        // Phase 8: Check if game end celebrating started and remove title on bossbar
        if (status == GameStatus.GAME_END_CELEBRATING && previousStatus != status) {
            if (statusbar instanceof BossBar) {
                BossBar bossbar = (BossBar) statusbar;
                bossbar.setMessage(" ");
            }
        }

        // Phase 9: Check if status is rebuilding and rebuild game
        if (status == GameStatus.REBUILDING) {
            BedwarsGameEndEvent event = new BedwarsGameEndEvent(this);
            Main.getInstance().getServer().getPluginManager().callEvent(event);

            String message = mpr("game.info.global.game_end").get();
            for (GamePlayer player : (List<GamePlayer>) ((ArrayList<GamePlayer>) players).clone()) {
                player.player.sendMessage(message);
                player.changeGame(null);

                if (Main.getConfigurator().config.getBoolean("rewards.enabled")) {
                    final Player pl = player.player;
                    new BukkitRunnable() {

                        @Override
                        public void run() {
                            if (Main.isPlayerStatisticsEnabled()) {
                                PlayerStatistic statistic = Main.getPlayerStatisticsManager()
                                        .getStatistic(player.player);
                                Game.this.dispatchRewardCommands("player-end-game", pl, statistic.getCurrentScore());
                            } else {
                                Game.this.dispatchRewardCommands("player-end-game", pl, 0);
                            }
                        }

                    }.runTaskLater(Main.getInstance(), 40);
                }
            }

            if (status == GameStatus.REBUILDING) { // If status is still rebuilding
                rebuild();
            }

            if (isBungeeEnabled()) {
                preServerRestart = true;

                if (!getConnectedPlayers().isEmpty()) {
                    kickAllPlayers();
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (Main.getConfigurator().config.getBoolean("bungee.serverRestart")) {
                            BedWarsServerRestartEvent serverRestartEvent = new BedWarsServerRestartEvent();
                            Main.getInstance().getServer().getPluginManager().callEvent(serverRestartEvent);

                            Main.getInstance().getServer()
                                    .dispatchCommand(Main.getInstance().getServer().getConsoleSender(), "restart");
                        } else if (Main.getConfigurator().config.getBoolean("bungee.serverStop")) {
                            Bukkit.shutdown();
                        }
                    }

                }.runTaskLater(Main.getInstance(), 30L);
            }
        }
    }

    public void rebuild() {
        teamsInGame.clear();
        activeSpecialItems.clear();
        activeDelays.clear();

        BedwarsPreRebuildingEvent preRebuildingEvent = new BedwarsPreRebuildingEvent(this);
        Main.getInstance().getServer().getPluginManager().callEvent(preRebuildingEvent);

        for (ItemSpawner spawner : spawners) {
            spawner.currentLevel = spawner.startLevel;
            spawner.spawnedItems.clear();
        }
        for (GameStore store : gameStore) {
            LivingEntity villager = store.kill();
            if (villager != null) {
                Main.unregisterGameEntity(villager);
            }
        }

        region.regen();
        // Remove items
        for (Entity e : this.world.getEntities()) {
            if (GameCreator.isInArea(e.getLocation(), pos1, pos2)) {
                if (e instanceof Item) {
                    Chunk chunk = e.getLocation().getChunk();
                    if (!chunk.isLoaded()) {
                        chunk.load();
                    }
                    e.remove();
                }
            }
        }

        // Chest clearing
        for (Map.Entry<Location, ItemStack[]> entry : usedChests.entrySet()) {
            Location location = entry.getKey();
            Chunk chunk = location.getChunk();
            if (!chunk.isLoaded()) {
                chunk.load();
            }
            Block block = location.getBlock();
            ItemStack[] contents = entry.getValue();
            if (block.getState() instanceof InventoryHolder) {
                InventoryHolder chest = (InventoryHolder) block.getState();
                chest.getInventory().setContents(contents);
            }
        }
        usedChests.clear();

        // Clear fake ender chests
        for (Inventory inv : fakeEnderChests.values()) {
            inv.clear();
        }
        fakeEnderChests.clear();

        // Remove remaining entities registered by other plugins
        for (Entity entity : Main.getGameEntities(this)) {
            Chunk chunk = entity.getLocation().getChunk();
            if (!chunk.isLoaded()) {
                chunk.load();
            }
            entity.remove();
            Main.unregisterGameEntity(entity);
        }

        // Holograms destroy
        for (Hologram holo : createdHolograms) {
            holo.destroy();
        }
        createdHolograms.clear();
        countdownHolograms.clear();

        UpgradeRegistry.clearAll(this);

        BedwarsPostRebuildingEvent postRebuildingEvent = new BedwarsPostRebuildingEvent(this);
        Main.getInstance().getServer().getPluginManager().callEvent(postRebuildingEvent);

        this.status = this.afterRebuild;
        this.countdown = -1;
        updateSigns();
        cancelTask();

    }

    public boolean processRecord(CurrentTeam t, int wonTime) {
        int time = Main.getConfigurator().recordConfig.getInt("record." + this.getName() + ".time", Integer.MAX_VALUE);
        if (time > wonTime) {
            Main.getConfigurator().recordConfig.set("record." + this.getName() + ".time", wonTime);
            Main.getConfigurator().recordConfig.set("record." + this.getName() + ".team",
                    t.teamInfo.color.chatColor + t.teamInfo.name);
            List<String> winners = new ArrayList<String>();
            for (GamePlayer p : t.players) {
                winners.add(p.player.getName());
            }
            Main.getConfigurator().recordConfig.set("record." + this.getName() + ".winners", winners);
            try {
                Main.getConfigurator().recordConfig.save(Main.getConfigurator().recordFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void runTask() {
        if (task != null) {
            if (Bukkit.getScheduler().isQueued(task.getTaskId())) {
                task.cancel();
            }
            task = null;
        }
        task = (new BukkitRunnable() {

            public void run() {
                Game.this.run();
            }

        }.runTaskTimer(Main.getInstance(), 0, 20));
    }

    private void cancelTask() {
        if (task != null) {
            if (Bukkit.getScheduler().isQueued(task.getTaskId())) {
                task.cancel();
            }
            task = null;
        }
    }

    public void selectTeam(GamePlayer playerGameProfile, String displayName) {
        if (status == GameStatus.WAITING) {
            displayName = ChatColor.stripColor(displayName);
            playerGameProfile.player.closeInventory();
            for (Team team : teams) {
                if (displayName.equals(team.name)) {
                    internalTeamJoin(playerGameProfile, team);
                    break;
                }
            }
        }
    }

    public void updateScoreboard() {
        if (!getOriginalOrInheritedScoreaboard()) {
            return;
        }

        Objective obj = this.gameScoreboard.getObjective("display");
        if (obj == null) {
            obj = this.gameScoreboard.registerNewObjective("display", "dummy");
        }

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(this.formatScoreboardTitle());

        for (CurrentTeam team : teamsInGame) {
            this.gameScoreboard.resetScores(this.formatScoreboardTeam(team, false));
            this.gameScoreboard.resetScores(this.formatScoreboardTeam(team, true));

            Score score = obj.getScore(this.formatScoreboardTeam(team, !team.isBed));
            score.setScore(team.players.size());
        }

        for (GamePlayer player : players) {
            player.player.setScoreboard(gameScoreboard);
        }
    }

    private String formatScoreboardTeam(CurrentTeam team, boolean destroy) {
        if (team == null) {
            return "";
        }

        return Main.getConfigurator().config.getString("scoreboard.teamTitle")
                .replace("%color%", team.teamInfo.color.chatColor.toString()).replace("%team%", team.teamInfo.name)
                .replace("%bed%", destroy ? bedLostString() : bedExistString());
    }

    public static String bedExistString() {
        return Main.getConfigurator().config.getString("scoreboard.bedExists");
    }

    public static String bedLostString() {
        return Main.getConfigurator().config.getString("scoreboard.bedLost");
    }

    private void updateScoreboardTimer() {
        if (this.status != GameStatus.RUNNING || !getOriginalOrInheritedScoreaboard()) {
            return;
        }

        Objective obj = this.gameScoreboard.getObjective("display");
        if (obj == null) {
            obj = this.gameScoreboard.registerNewObjective("display", "dummy");
        }

        obj.setDisplayName(this.formatScoreboardTitle());

        for (GamePlayer player : players) {
            player.player.setScoreboard(gameScoreboard);
        }
    }

    private String formatScoreboardTitle() {
        return Main.getConfigurator().config.getString("scoreboard.title").replace("%game%", this.name)
                .replace("%time%", this.getFormattedTimeLeft());
    }

    public String getFormattedTimeLeft() {
        return getFormattedTimeLeft(this.countdown);
    }

    public String getFormattedTimeLeft(int countdown) {
        int min;
        int sec;
        String minStr;
        String secStr;

        min = (int) Math.floor(countdown / 60);
        sec = countdown % 60;

        minStr = (min < 10) ? "0" + min : String.valueOf(min);
        secStr = (sec < 10) ? "0" + sec : String.valueOf(sec);

        return minStr + ":" + secStr;
    }

    public void updateSigns() {
        List<SignBlock> gameSigns = Main.getSignManager().getSignsForName(this.name);

        if (gameSigns.isEmpty()) {
            return;
        }

        String statusLine = "";
        String playersLine = "";
        switch (status) {
            case DISABLED:
                statusLine = m("signs.status.disabled.line1").get();
                playersLine = m("signs.status.disabled.line2").get();
                break;
            case REBUILDING:
                statusLine = m("signs.status.rebuilding.line1").get();
                playersLine = m("signs.status.rebuilding.line2").get();
                break;
            case RUNNING:
            case GAME_END_CELEBRATING:
                statusLine = m("signs.status.running.line1").get();
                playersLine = m("signs.status.running.line2").get();
                break;
            case WAITING:
                statusLine = m("signs.status.waiting.line1").get();
                playersLine = m("signs.status.waiting.line2").get();
                break;
        }
        playersLine = playersLine.replace("%players%", Integer.toString(players.size()));
        playersLine = playersLine.replace("%maxplayers%", Integer.toString(calculatedMaxPlayers));

        List<String> texts = new ArrayList<>(Main.getConfigurator().config.getStringList("sign"));

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            texts.set(i, text.replaceAll("%arena%", this.getName()).replaceAll("%status%", statusLine)
                    .replaceAll("%players%", playersLine));
        }

        for (SignBlock sign : gameSigns) {
            if (sign.getLocation().getChunk().isLoaded()) {
                Block block = sign.getLocation().getBlock();
                if (block.getState() instanceof Sign) {
                    Sign state = (Sign) block.getState();
                    for (int i = 0; i < texts.size() && i < 4; i++) {
                        state.setLine(i, texts.get(i));
                    }
                    state.update();
                }
            }
        }
    }

    private void updateLobbyScoreboard() {
        if (status != GameStatus.WAITING || !getOriginalOrInheritedLobbyScoreaboard()) {
            return;
        }
        gameScoreboard.clearSlot(DisplaySlot.SIDEBAR);

        Objective obj = gameScoreboard.getObjective("lobby");
        if (obj != null) {
            obj.unregister();
        }

        obj = gameScoreboard.registerNewObjective("lobby", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(this.formatLobbyScoreboardString(
                Main.getConfigurator().config.getString("lobby-scoreboard.title", "§eBEDWARS")));

        List<String> rows = Main.getConfigurator().config.getStringList("lobby-scoreboard.content");
        int rowMax = rows.size();
        if (rows == null || rows.isEmpty()) {
            return;
        }

        for (String row : rows) {
            if (row.trim().equals("")) {
                StringBuilder rowBuilder = new StringBuilder(row);
                for (int i = 0; i <= rowMax; i++) {
                    rowBuilder.append(" ");
                }
                row = rowBuilder.toString();
            }

            Score score = obj.getScore(this.formatLobbyScoreboardString(row));
            score.setScore(rowMax);
            rowMax--;
        }

        for (GamePlayer player : players) {
            player.player.setScoreboard(gameScoreboard);
        }
    }

    private String formatLobbyScoreboardString(String str) {
        String finalStr = str;

        finalStr = finalStr.replace("%arena%", name);
        finalStr = finalStr.replace("%players%", String.valueOf(players.size()));
        finalStr = finalStr.replace("%maxplayers%", String.valueOf(calculatedMaxPlayers));

        return finalStr;
    }

    @Override
    public void selectPlayerTeam(Player player, org.screamingsandals.bedwars.api.Team team) {
        if (!Main.isPlayerInGame(player)) {
            return;
        }
        GamePlayer profile = Main.getPlayerGameProfile(player);
        if (profile.getGame() != this) {
            return;
        }

        selectTeam(profile, team.getName());
    }

    @Override
    public World getGameWorld() {
        return world;
    }

    @Override
    public Location getSpectatorSpawn() {
        return specSpawn;
    }

    @Override
    public int countConnectedPlayers() {
        return players.size();
    }

    @Override
    public List<Player> getConnectedPlayers() {
        List<Player> playerList = new ArrayList<>();
        for (GamePlayer player : players) {
            playerList.add(player.player);
        }
        return playerList;
    }

    @Override
    public List<org.screamingsandals.bedwars.api.Team> getAvailableTeams() {
        return new ArrayList<>(teams);
    }

    @Override
    public List<RunningTeam> getRunningTeams() {
        return new ArrayList<>(teamsInGame);
    }

    @Override
    public RunningTeam getTeamOfPlayer(Player player) {
        if (!Main.isPlayerInGame(player)) {
            return null;
        }
        return getPlayerTeam(Main.getPlayerGameProfile(player));
    }

    @Override
    public boolean isLocationInArena(Location location) {
        return GameCreator.isInArea(location, pos1, pos2);
    }

    @Override
    public World getLobbyWorld() {
        return lobbySpawn.getWorld();
    }

    @Override
    public int getLobbyCountdown() {
        return lobbyTime;
    }

    @Override
    public CurrentTeam getTeamOfChest(Location location) {
        for (CurrentTeam team : teamsInGame) {
            if (team.isTeamChestRegistered(location)) {
                return team;
            }
        }
        return null;
    }

    @Override
    public CurrentTeam getTeamOfChest(Block block) {
        for (CurrentTeam team : teamsInGame) {
            if (team.isTeamChestRegistered(block)) {
                return team;
            }
        }
        return null;
    }

    public void addChestForFutureClear(Location loc, Inventory inventory) {
        if (!usedChests.containsKey(loc)) {
            ItemStack[] contents = inventory.getContents();
            ItemStack[] clone = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack != null)
                    clone[i] = stack.clone();
            }
            usedChests.put(loc, clone);
        }
    }

    @Override
    public int getMaxPlayers() {
        return calculatedMaxPlayers;
    }

    @Override
    public int countGameStores() {
        return gameStore.size();
    }

    @Override
    public int countAvailableTeams() {
        return teams.size();
    }

    @Override
    public int countRunningTeams() {
        return teamsInGame.size();
    }

    @Override
    public boolean isPlayerInAnyTeam(Player player) {
        return getTeamOfPlayer(player) != null;
    }

    @Override
    public boolean isPlayerInTeam(Player player, RunningTeam team) {
        return getTeamOfPlayer(player) == team;
    }

    @Override
    public int countTeamChests() {
        int total = 0;
        for (CurrentTeam team : teamsInGame) {
            total += team.countTeamChests();
        }
        return total;
    }

    @Override
    public int countTeamChests(RunningTeam team) {
        return team.countTeamChests();
    }

    @Override
    public List<SpecialItem> getActivedSpecialItems() {
        return new ArrayList<>(activeSpecialItems);
    }

    @Override
    public List<SpecialItem> getActivedSpecialItems(Class<? extends SpecialItem> type) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (type.isInstance(item)) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfTeam(org.screamingsandals.bedwars.api.Team team) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (item.getTeam() == team) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfTeam(org.screamingsandals.bedwars.api.Team team,
                                                          Class<? extends SpecialItem> type) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (type.isInstance(item) && item.getTeam() == team) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfTeam(org.screamingsandals.bedwars.api.Team team) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getTeam() == team) {
                return item;
            }
        }
        return null;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfTeam(org.screamingsandals.bedwars.api.Team team,
                                                        Class<? extends SpecialItem> type) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getTeam() == team && type.isInstance(item)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfPlayer(Player player) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfPlayer(Player player, Class<? extends SpecialItem> type) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player && type.isInstance(item)) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfPlayer(Player player) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player) {
                return item;
            }
        }
        return null;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfPlayer(Player player, Class<? extends SpecialItem> type) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player && type.isInstance(item)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void registerSpecialItem(SpecialItem item) {
        if (!activeSpecialItems.contains(item)) {
            activeSpecialItems.add(item);
        }
    }

    @Override
    public void unregisterSpecialItem(SpecialItem item) {
        if (activeSpecialItems.contains(item)) {
            activeSpecialItems.remove(item);
        }
    }

    @Override
    public boolean isRegisteredSpecialItem(SpecialItem item) {
        return activeSpecialItems.contains(item);
    }

    @Override
    public List<DelayFactory> getActiveDelays() {
        return new ArrayList<>(activeDelays);
    }

    @Override
    public List<DelayFactory> getActiveDelaysOfPlayer(Player player) {
        List<DelayFactory> delays = new ArrayList<>();
        for (DelayFactory delay : activeDelays) {
            if (delay.getPlayer() == player) {
                delays.add(delay);
            }
        }
        return delays;
    }

    @Override
    public DelayFactory getActiveDelay(Player player, Class<? extends SpecialItem> specialItem) {
        for (DelayFactory delayFactory : getActiveDelaysOfPlayer(player)) {
            if (specialItem.isInstance(delayFactory.getSpecialItem())) {
                return delayFactory;
            }
        }
        return null;
    }

    @Override
    public void registerDelay(DelayFactory delayFactory) {
        if (!activeDelays.contains(delayFactory)) {
            activeDelays.add(delayFactory);
        }
    }

    @Override
    public void unregisterDelay(DelayFactory delayFactory) {
        activeDelays.remove(delayFactory);
    }

    @Override
    public boolean isDelayActive(Player player, Class<? extends SpecialItem> specialItem) {
        for (DelayFactory delayFactory : getActiveDelaysOfPlayer(player)) {
            if (specialItem.isInstance(delayFactory.getSpecialItem())) {
                return delayFactory.getDelayActive();
            }
        }
        return false;
    }

    @Override
    public ArenaTime getArenaTime() {
        return arenaTime;
    }

    public void setArenaTime(ArenaTime arenaTime) {
        this.arenaTime = arenaTime;
    }

    @Override
    public WeatherType getArenaWeather() {
        return arenaWeather;
    }

    public void setArenaWeather(WeatherType arenaWeather) {
        this.arenaWeather = arenaWeather;
    }

    @Override
    public BarColor getLobbyBossBarColor() {
        return this.lobbyBossBarColor;
    }

    public void setLobbyBossBarColor(BarColor color) {
        this.lobbyBossBarColor = color;
    }

    @Override
    public BarColor getGameBossBarColor() {
        return this.gameBossBarColor;
    }

    public void setGameBossBarColor(BarColor color) {
        this.gameBossBarColor = color;
    }

    @Override
    public List<org.screamingsandals.bedwars.api.game.ItemSpawner> getItemSpawners() {
        return new ArrayList<>(spawners);
    }

    public void dispatchRewardCommands(String type, Player player, int score) {
        if (!Main.getConfigurator().config.getBoolean("rewards.enabled")) {
            return;
        }

        List<String> list = Main.getConfigurator().config.getStringList("rewards." + type);
        for (String command : list) {
            command = command.replaceAll("\\{player}", player.getName());
            command = command.replaceAll("\\{score}", Integer.toString(score));
            command = command.startsWith("/") ? command.substring(1) : command;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    @Override
    public void selectPlayerRandomTeam(Player player) {
        joinRandomTeam(Main.getPlayerGameProfile(player));
    }

    @Override
    public StatusBar getStatusBar() {
        return statusbar;
    }

    public void kickAllPlayers() {
        for (Player player : getConnectedPlayers()) {
            leaveFromGame(player);
        }
    }

    public static boolean isBungeeEnabled() {
        return Main.getConfigurator().config.getBoolean("bungee.enabled");
    }

    @Override
    public boolean getBungeeEnabled() {
        return Main.getConfigurator().config.getBoolean("bungee.enabled");
    }

    @Override
    public boolean isEntityShop(Entity entity) {
        for (GameStore store : gameStore) {
            if (store.getEntity().equals(entity)) {
                return true;
            }
        }
        return false;
    }

    public RespawnProtection addProtectedPlayer(Player player) {
        int time = Main.getConfigurator().config.getInt("respawn.protection-time", 10);

        RespawnProtection respawnProtection = new RespawnProtection(this, player, time);
        respawnProtectionMap.put(player, respawnProtection);

        return respawnProtection;
    }

    public void removeProtectedPlayer(Player player) {
        RespawnProtection respawnProtection = respawnProtectionMap.get(player);
        if (respawnProtection == null) {
            return;
        }

        try {
            respawnProtection.cancel();
        } catch (Exception ignored) {
        }

        respawnProtectionMap.remove(player);
    }

    @Override
    public boolean isProtectionActive(Player player) {
        return (respawnProtectionMap.containsKey(player));
    }

    public List<GamePlayer> getPlayersWithoutVIP() {
        List<GamePlayer> gamePlayerList = new ArrayList<>(this.players);
        gamePlayerList.removeIf(GamePlayer::canJoinFullGame);

        return gamePlayerList;
    }

    public Inventory getFakeEnderChest(GamePlayer player) {
        if (!fakeEnderChests.containsKey(player)) {
            fakeEnderChests.put(player, Bukkit.createInventory(player.player, InventoryType.ENDER_CHEST));
        }
        return fakeEnderChests.get(player);
    }

    public String getGameStatusString() {
        String toReturn = "";
        switch (getStatus()) {
            case DISABLED:
                toReturn = mpr("arena.status.disabled").get();
                break;
            case REBUILDING:
                toReturn = mpr("arena.status.rebuilding").get();
                break;
            case RUNNING:
            case GAME_END_CELEBRATING:
                toReturn = mpr("arena.status.running").get();
                break;
            case WAITING:
                toReturn = mpr("arena.status.waiting").get();
                break;
        }
        return toReturn;
    }

	@Override
	public GameConfigManager getConfigManager() {
		return gameConfigManager;
	}
}
