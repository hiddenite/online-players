package eu.hiddenite.onlineplayers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class OnlinePlayersPlugin extends Plugin implements Listener {
    private Configuration config;
    private DatabaseManager database;

    private String databaseTable = null;
    private boolean shouldUpdateOnlinePlayers = true;

    @Override
    public void onEnable() {
        if (!loadConfiguration()) {
            return;
        }

        database = new DatabaseManager(config, getLogger());
        if (!database.open()) {
            getLogger().warning("Could not connect to the database. Plugin disabled.");
            return;
        }

        databaseTable = config.getString("mysql.table");
        getLogger().info("Online players will be inserted in " + databaseTable);

        getProxy().getPluginManager().registerListener(this, this);

        getProxy().getScheduler().schedule(this, this::updateOnlinePlayers,
                1, config.getInt("update-interval"), TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        database.close();
    }

    @EventHandler
    public void onServerConnectedEvent(ServerConnectedEvent event) {
        shouldUpdateOnlinePlayers = true;
    }

    @EventHandler
    public void onPlayerDisconnectEvent(PlayerDisconnectEvent event) {
        shouldUpdateOnlinePlayers = true;
    }

    private void updateOnlinePlayers() {
        if (!shouldUpdateOnlinePlayers) {
            return;
        }
        shouldUpdateOnlinePlayers = false;

        JsonArray jsonPlayers = new JsonArray();

        for (ProxiedPlayer player : getProxy().getPlayers()) {
            JsonObject jsonPlayer = new JsonObject();
            jsonPlayer.addProperty("name", player.getName());
            jsonPlayer.addProperty("uuid", player.getUniqueId().toString());
            jsonPlayers.add(jsonPlayer);
        }

        int playersCount = jsonPlayers.size();
        String playersData = jsonPlayers.toString();

        getProxy().getScheduler().runAsync(this, () -> {
            try {
                try (PreparedStatement ps = database.prepareStatement("INSERT INTO `" + databaseTable + "`" +
                        " (creation_date, players_count, players_data)" +
                        " VALUES (NOW(), ?, ?)"
                )) {
                    ps.setInt(1, playersCount);
                    ps.setString(2, playersData);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private boolean loadConfiguration() {
        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdir()) {
                getLogger().warning("Could not create the configuration folder.");
                return false;
            }
        }

        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            getLogger().warning("No configuration file found, creating a default one.");

            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        try {
            config = ConfigurationProvider
                    .getProvider(YamlConfiguration.class)
                    .load(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
