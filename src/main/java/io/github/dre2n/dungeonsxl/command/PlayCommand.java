/*
 * Copyright (C) 2012-2016 Frank Baumann
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
 */
package io.github.dre2n.dungeonsxl.command;

import io.github.dre2n.commons.command.BRCommand;
import io.github.dre2n.commons.util.messageutil.MessageUtil;
import io.github.dre2n.dungeonsxl.DungeonsXL;
import io.github.dre2n.dungeonsxl.config.DungeonConfig;
import io.github.dre2n.dungeonsxl.config.MessageConfig;
import io.github.dre2n.dungeonsxl.config.MessageConfig.Messages;
import io.github.dre2n.dungeonsxl.config.WorldConfig;
import io.github.dre2n.dungeonsxl.dungeon.Dungeon;
import io.github.dre2n.dungeonsxl.world.EditWorld;
import io.github.dre2n.dungeonsxl.event.dgroup.DGroupCreateEvent;
import io.github.dre2n.dungeonsxl.world.GameWorld;
import io.github.dre2n.dungeonsxl.player.DGroup;
import io.github.dre2n.dungeonsxl.player.DPlayer;
import java.io.File;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * @author Frank Baumann, Daniel Saukel
 */
public class PlayCommand extends BRCommand {

    protected static DungeonsXL plugin = DungeonsXL.getInstance();
    protected static MessageConfig messageConfig = plugin.getMessageConfig();

    public PlayCommand() {
        setCommand("play");
        setMinArgs(1);
        setMaxArgs(2);
        setHelp(messageConfig.getMessage(Messages.HELP_CMD_PLAY));
        setPermission("dxl.play");
        setPlayerCommand(true);
    }

    @Override
    public void onExecute(String[] args, CommandSender sender) {
        Player player = (Player) sender;
        DPlayer dPlayer = DPlayer.getByPlayer(player);

        if (dPlayer != null) {
            MessageUtil.sendMessage(player, messageConfig.getMessage(Messages.ERROR_LEAVE_DUNGEON));
            return;
        }

        if (!(args.length >= 2 && args.length <= 3)) {
            displayHelp(player);
            return;
        }

        String identifier = args[1];
        String mapName = identifier;

        boolean multiFloor = false;
        if (args.length == 3) {
            identifier = args[2];
            mapName = identifier;
            if (args[1].equalsIgnoreCase("dungeon") || args[1].equalsIgnoreCase("d")) {
                Dungeon dungeon = plugin.getDungeons().getDungeon(args[2]);
                if (dungeon != null) {
                    multiFloor = true;
                    mapName = dungeon.getConfig().getStartFloor();
                } else {
                    displayHelp(player);
                    return;
                }

            } else if (args[1].equalsIgnoreCase("map") || args[1].equalsIgnoreCase("m")) {
                identifier = args[2];
            }
        }

        if (!multiFloor && !EditWorld.exists(identifier)) {
            MessageUtil.sendMessage(player, messageConfig.getMessage(Messages.ERROR_DUNGEON_NOT_EXIST, identifier));
            return;
        }

        if (!GameWorld.canPlayDungeon(identifier, player)) {
            File file = new File(plugin.getDataFolder() + "/maps/" + identifier + "/config.yml");

            if (file.exists()) {
                WorldConfig confReader = new WorldConfig(file);

                if (confReader != null) {
                    MessageUtil.sendMessage(player, messageConfig.getMessage(Messages.ERROR_COOLDOWN, "" + confReader.getTimeToNextPlay()));
                }
            }
            return;
        }

        if (!GameWorld.checkRequirements(mapName, player)) {
            MessageUtil.sendMessage(player, messageConfig.getMessage(Messages.ERROR_REQUIREMENTS));
            return;
        }

        DGroup dGroup = DGroup.getByPlayer(player);

        if (dGroup != null) {
            if (!dGroup.getCaptain().equals(player) && !player.hasPermission("dxl.bypass")) {
                MessageUtil.sendMessage(player, messageConfig.getMessage(Messages.ERROR_NOT_CAPTAIN));
            }

            if (dGroup.getMapName() == null) {
                if (!multiFloor) {
                    dGroup.setMapName(identifier);

                } else {
                    dGroup.setDungeonName(identifier);
                    Dungeon dungeon = plugin.getDungeons().getDungeon(identifier);

                    if (dungeon != null) {
                        DungeonConfig config = dungeon.getConfig();

                        if (config != null) {
                            dGroup.setMapName(config.getStartFloor());
                        }
                    }
                }

            } else {
                MessageUtil.sendMessage(player, messageConfig.getMessage(Messages.ERROR_LEAVE_GROUP));
                return;
            }

        } else {
            dGroup = new DGroup(player, identifier, multiFloor);
        }

        DGroupCreateEvent event = new DGroupCreateEvent(dGroup, player, DGroupCreateEvent.Cause.COMMAND);

        if (event.isCancelled()) {
            plugin.getDGroups().remove(dGroup);
            dGroup = null;
        }

        if (dGroup == null) {
            return;
        }

        if (dGroup.getGameWorld() == null) {
            dGroup.setGameWorld(GameWorld.load(DGroup.getByPlayer(player).getMapName()));
        }

        if (dGroup.getGameWorld() == null) {
            MessageUtil.sendMessage(player, messageConfig.getMessage(Messages.ERROR_NOT_SAVED, DGroup.getByPlayer(player).getMapName()));
            dGroup.delete();
            return;
        }

        if (dGroup.getGameWorld().getLocLobby() == null) {
            for (Player groupPlayer : dGroup.getPlayers()) {
                new DPlayer(groupPlayer, dGroup.getGameWorld());
            }

        } else {
            for (Player groupPlayer : dGroup.getPlayers()) {
                new DPlayer(groupPlayer, dGroup.getGameWorld());
            }
        }
    }

}