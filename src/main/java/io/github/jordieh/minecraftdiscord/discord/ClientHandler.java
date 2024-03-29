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

package io.github.jordieh.minecraftdiscord.discord;

import io.github.jordieh.minecraftdiscord.MinecraftDiscord;
import io.github.jordieh.minecraftdiscord.listeners.discord.MessageReceivedEventHandler;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.IListener;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.obj.ActivityType;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.StatusType;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RequestBuffer;

public class ClientHandler implements IListener<ReadyEvent> {

    private final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private static ClientHandler instance;

    @Getter private IDiscordClient client;

    private boolean disable;
    public static boolean crashed;

    private ClientHandler() {
        logger.debug("Constructing ClientHandler");
        MinecraftDiscord plugin = MinecraftDiscord.getInstance();
        FileConfiguration configuration = plugin.getConfig();
        String token = configuration.getString("token");

        logger.trace("Starting ClientBuilder");
        ClientBuilder builder = new ClientBuilder();
        builder.withRecommendedShardCount();
        builder.withToken(token);
        builder.registerListener(this); // ReadyEvent
        builder.registerListener(new MessageReceivedEventHandler()); // MessageReceivedEvent

        try {
            logger.debug("Trying to connect to Discord");
            client = builder.login();
            logger.debug("Successfully connected to Discord with {} listeners", 1);
        } catch (DiscordException e) {
            if (e.getMessage().contains("401")) {
                logger.error("\n#############################################\n" +
                        "# First startup error detected              #\n" +
                        "# You seem to have a invalid bot token      #\n" +
                        "# Please visit ... and change your token!   #\n" +
                        "#############################################");
                crashed = true;
                this.disable(true);
            } else {
                logger.warn("Error detected while attempting Discord connection", e);
            }
        }
    }

    public static ClientHandler getInstance() {
        return instance == null ? instance = new ClientHandler() : instance;
    }

    public final void request(Runnable runnable) {
        RequestBuffer.request(() -> {
            try {
                runnable.run();
            } catch (DiscordException e) {
                logger.error("Catched a rare Discord error while doing a request, please visit ...", e);
            } catch (MissingPermissionsException e) {
                if (e.getErrorMessage().contains("role hierarchy")) {
                    logger.warn("{} Please visit ...", e.getErrorMessage());
                } else {
                    logger.warn("{} Please edit the bot role", e.getMessage());
                }
            }
        });
    }

    public void giveRole(IRole role, IUser user) {
        request(() -> {
            if (!user.hasRole(role)) {
                logger.trace("Attempting to give user {} role [{}] ({})", user.getLongID(), role.getName(), role.getLongID());
                user.addRole(role);
            }
        });
    }

    public void removeRole(IRole role, IUser user) {
        request(() -> {
            if (user.hasRole(role)) {
                logger.trace("Attempting to remove role [{}] ({}) from user {}", role.getName(), role.getLongID(), user.getLongID());
                user.removeRole(role);
            }
        });
    }

    public void deleteMessage(IMessage message) {
        request(() -> {
            logger.trace("Attempting to delete message {} in #{}", message.getLongID(), message.getChannel().getName());
            message.delete();
        });
    }

    public void sendMessage(IChannel channel, String message) {
        request(() -> {
            logger.trace("Attempting to send '{}' to #{}", message, channel.getName());
            channel.sendMessage(message);
        });
    }

    public void sendMessage(IChannel channel, EmbedObject embed) {
        request(() -> {
            logger.trace("Attempting to send an embed to #{}", channel.getName());
            channel.sendMessage(embed);
        });
    }

    public void disable(boolean force) {
        if ((client == null || !client.isReady()) && !force) {
            logger.trace("Waiting for ReadyEvent to call ClientHandler#disable();");
            disable = true;
            return;
        }

        logger.debug("Disabling plugin: Read previous output for more information");
        if (!force) {
            client.logout();
        }

        logger.debug("Disabling plugin via ClientHandler#disable();");
        Plugin plugin = MinecraftDiscord.getInstance();
        plugin.getServer().getPluginManager().disablePlugin(plugin);
    }

    @Override
    public void handle(ReadyEvent event) {
        logger.debug("ReadyEvent has been called");

        if (this.disable) {
            this.disable(false);
            return;
        }

        MinecraftDiscord.getInstance().finishStartup();

        FileConfiguration configuration = MinecraftDiscord.getInstance().getConfig();
        this.updatePresence(configuration);

//        this.findConfigChannel(ConfigSection.SHUTDOWN_CHANNEL).ifPresent(channel -> { TODO Fix this
//            EmbedBuilder builder = new EmbedBuilder();
//            builder.withDescription(":green_book: The server has been turned on");
//            builder.withColor(0x00AA00);
//            this.sendMessage(channel, builder.build());
//        });
    }

    private void updatePresence(FileConfiguration configuration) {
        logger.trace("Attempting to update Discord presence");
        if (!configuration.getBoolean("presence.enabled")) {
            logger.debug("Discord presence is disabled in config.yml");
            return;
        }
        String state;

        StatusType status;
        state = configuration.getString("presence.status").toUpperCase();
        try {
            status = StatusType.valueOf(state);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid presence status type detected in config.yml ({}) using ONLINE", state);
            status = StatusType.ONLINE;
        }

        ActivityType activity;
        state = configuration.getString("presence.activity").toUpperCase();
        try {
            activity = ActivityType.valueOf(state);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid presence activity type detected in config.yml ({}) using PLAYING", state);
            activity = ActivityType.PLAYING;
        }

        // Streaming uses a separate method that we will not be allowing
        if (activity == ActivityType.STREAMING) {
            logger.warn("Detected usage of activity type STREAMING in config.yml, this is unsupported, using PLAYING");
            activity = ActivityType.PLAYING;
        }

        String text = configuration.getString("presence.text");

        logger.debug("Attempting to change presence [{}] [{}] [{}]", status.name(), activity.name(), text);
        this.client.changePresence(status, activity, text);
    }
}
