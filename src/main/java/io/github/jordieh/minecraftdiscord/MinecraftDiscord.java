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

import io.github.jordieh.minecraftdiscord.command.DiscordCommand;
import io.github.jordieh.minecraftdiscord.command.LinkCommand;
import io.github.jordieh.minecraftdiscord.command.UnlinkCommand;
import io.github.jordieh.minecraftdiscord.dependencies.DependencyHandler;
import io.github.jordieh.minecraftdiscord.discord.ClientHandler;
import io.github.jordieh.minecraftdiscord.discord.CommandHandler;
import io.github.jordieh.minecraftdiscord.discord.LinkHandler;
import io.github.jordieh.minecraftdiscord.discord.RoleHandler;
import io.github.jordieh.minecraftdiscord.listeners.minecraft.AsyncPlayerChatListener;
import io.github.jordieh.minecraftdiscord.listeners.minecraft.PlayerJoinListener;
import io.github.jordieh.minecraftdiscord.listeners.minecraft.PlayerQuitListener;
import io.github.jordieh.minecraftdiscord.metrics.MetricsHandler;
import io.github.jordieh.minecraftdiscord.util.ConsoleWorker;
import io.github.jordieh.minecraftdiscord.util.LangUtil;
import io.github.jordieh.minecraftdiscord.world.ChannelHandler;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

public final class MinecraftDiscord extends JavaPlugin {

    private final Logger logger = LoggerFactory.getLogger(MinecraftDiscord.class);

    @Getter private static MinecraftDiscord instance;

    private double startup;

    public static final Queue<String> queue = new LinkedBlockingDeque<>();
    public static boolean started = false;

    @Override
    public void onEnable() {
        startup = System.currentTimeMillis();
        instance = this;

        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        logger.debug("Saving default configuration");
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        saveResource("language/messages_en.properties", false);
        saveResource("language/messages.properties", false);

        logger.debug("Registering handlers");
        ClientHandler.getInstance();
        logger.info("Waiting for the bot to be ready");
    }

    @Override
    public void onDisable() {
        if (!ClientHandler.crashed) {
            RoleHandler.getInstance().clearConnectionUsers(false);
            LinkHandler.getInstance().saveResources();
        }
        ClientHandler.getInstance().disable(false);
    }

    /**
     * Make sure the bot has started up before doing anything special
     */
    public void finishStartup() {
        if (startup == -1 || !ClientHandler.getInstance().getClient().isReady()) {
            return;
        }

        LangUtil.getInstance();
        LinkHandler.getInstance();
        MetricsHandler.getInstance();
        ChannelHandler.getInstance();
        CommandHandler.getInstance();
        DependencyHandler.getInstance();

//        DiscordAppender.process();

        new ConsoleWorker().start();
//        ChannelHandler.getInstance().getConnectedChannel("console").ifPresent((channel -> {
//            for (String s : ChannelHandler.getInstance().consoleMessages) {
//                ClientHandler.getInstance().sendMessage(channel, s);
//            }
//        }));

        RoleHandler.getInstance().clearConnectionUsers(true);
        RoleHandler.getInstance().distributeConnectionRole();

        logger.debug("Registering Bukkit events");
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(), this);
        getServer().getPluginManager().registerEvents(new AsyncPlayerChatListener(), this);

        logger.debug("Registering commands");
        getCommand("link").setExecutor(new LinkCommand());
        getCommand("unlink").setExecutor(new UnlinkCommand());
        getCommand("discord").setExecutor(new DiscordCommand());

        startup = ((System.currentTimeMillis() - startup)) / 1000.0d;
        NumberFormat format = new DecimalFormat("#0.00");
        logger.info("The plugin has been enabled in {} seconds", format.format(startup));

        startup = -1;
    }



    @Deprecated
    public void saveResource(String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.equals("")) {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }

        resourcePath = resourcePath.replace('\\', '/');
        InputStream in = getResource(resourcePath);
        if (in == null) {
            throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found in " + "?");
        }

        File outFile = new File(getDataFolder(), resourcePath);
        int lastIndex = resourcePath.lastIndexOf('/');
        File outDir = new File(getDataFolder(), resourcePath.substring(0, lastIndex >= 0 ? lastIndex : 0));

        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            if (!outFile.exists() || replace) {
                OutputStream out = new FileOutputStream(outFile);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.close();
                in.close();
            } else {
//                logger.info("Could not save " + outFile.getName() + " to " + outFile + " because " + outFile.getName() + " already exists.");
            }
        } catch (IOException ex) {
            logger.info("Could not save " + outFile.getName() + " to " + outFile, ex);
        }
    }

}
