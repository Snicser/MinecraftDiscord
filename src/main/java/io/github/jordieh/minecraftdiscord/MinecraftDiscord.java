/*
 *     This file is part of MinecraftDiscord.
 *
 *     MinecraftDiscord is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     MinecraftDiscord is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with MinecraftDiscord.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.jordieh.minecraftdiscord;

import io.github.jordieh.minecraftdiscord.command.LinkCommand;
import io.github.jordieh.minecraftdiscord.discord.ClientHandler;
import io.github.jordieh.minecraftdiscord.discord.LinkHandler;
import io.github.jordieh.minecraftdiscord.discord.WebhookHandler;
import io.github.jordieh.minecraftdiscord.listeners.minecraft.AsyncPlayerChatListener;
import io.github.jordieh.minecraftdiscord.listeners.minecraft.PlayerJoinListener;
import io.github.jordieh.minecraftdiscord.listeners.minecraft.PlayerQuitListener;
import io.github.jordieh.minecraftdiscord.metrics.MetricsHandler;
import io.github.jordieh.minecraftdiscord.world.WorldHandler;
import lombok.Getter;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.PatternLayout;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.NumberFormat;

public final class MinecraftDiscord extends JavaPlugin {

    private final Logger logger = LoggerFactory.getLogger(MinecraftDiscord.class);

    // Resolving log4j dependency TODO Find a way to fix this in the pom.xml
    // InputStream in = getClass().getClassLoader().getResourceAsStream("log4j.properties");
    // PropertyConfigurator.configure(in);
    static {
        ConsoleAppender appender = new ConsoleAppender();
        PatternLayout layout = new PatternLayout();
        FileAppender fileAppender = new FileAppender();
    }

    @Getter private static MinecraftDiscord instance;

    @Override
    public void onEnable() {
        double startup = System.currentTimeMillis();
        System.out.println("============== [MinecraftDiscord] ==============");

        instance = this;

        logger.debug("Saving default configuration");
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        logger.debug("Registering handlers");
        ClientHandler.getInstance();
        LinkHandler.getInstance();
        WebhookHandler.getInstance();
        MetricsHandler.getInstance();
        WorldHandler.getInstance();

        logger.debug("Registering Bukkit events");
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(), this);
        getServer().getPluginManager().registerEvents(new AsyncPlayerChatListener(), this);

        logger.debug("Registering commands");
        getCommand("link").setExecutor(new LinkCommand());

        startup = ((System.currentTimeMillis() - startup)) / 1000.0d;
        NumberFormat format = new DecimalFormat("#0.00");
        logger.info("The plugin has been enabled in {} seconds", format.format(startup));
        System.out.println("============== [MinecraftDiscord] ==============");
    }

    @Override
    public void onDisable() {
        logger.debug("Plugin disable procedure has been engaged");
        ClientHandler.getInstance().disable();
    }

}
