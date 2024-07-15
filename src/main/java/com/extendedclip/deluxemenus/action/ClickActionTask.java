package com.extendedclip.deluxemenus.action;

import com.extendedclip.deluxemenus.DeluxeMenus;
import com.extendedclip.deluxemenus.menu.Menu;
import com.extendedclip.deluxemenus.menu.MenuHolder;
import com.extendedclip.deluxemenus.menu.MenuItem;
import com.extendedclip.deluxemenus.menu.SetHolder;
import com.extendedclip.deluxemenus.utils.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;

public class ClickActionTask extends BukkitRunnable {

    private final DeluxeMenus plugin;
    private final UUID uuid;
    private final ActionType actionType;
    private final String exec;
    // Ugly hack to get around the fact that arguments are not available at task execution time
    private final Map<String, String> arguments;
    private final boolean parsePlaceholdersInArguments;
    private final boolean parsePlaceholdersAfterArguments;

    public ClickActionTask(
            @NotNull final DeluxeMenus plugin,
            @NotNull final UUID uuid,
            @NotNull final ActionType actionType,
            @NotNull final String exec,
            @NotNull final Map<String, String> arguments,
            final boolean parsePlaceholdersInArguments,
            final boolean parsePlaceholdersAfterArguments
    ) {
        this.plugin = plugin;
        this.uuid = uuid;
        this.actionType = actionType;
        this.exec = exec;
        this.arguments = arguments;
        this.parsePlaceholdersInArguments = parsePlaceholdersInArguments;
        this.parsePlaceholdersAfterArguments = parsePlaceholdersAfterArguments;
    }

    @Override
    public void run() {
        final Player player = Bukkit.getPlayer(this.uuid);
        if (player == null) {
            return;
        }

        final Optional<MenuHolder> holder = Menu.getMenuHolder(player);
        final Player target = holder.isPresent() && holder.get().getPlaceholderPlayer() != null
                ? holder.get().getPlaceholderPlayer()
                : player;


        final String executable = StringUtils.replacePlaceholdersAndArguments(
                this.exec,
                this.arguments,
                target,
                this.parsePlaceholdersInArguments,
                this.parsePlaceholdersAfterArguments);

        switch (actionType) {
            case META:
                if (!VersionHelper.IS_PDC_VERSION || DeluxeMenus.getInstance().getPersistentMetaHandler() == null) {
                    DeluxeMenus.debug(DebugLevel.HIGHEST, Level.INFO, "Meta action not supported on this server version.");
                    break;
                }
                try {
                    final boolean result = DeluxeMenus.getInstance().getPersistentMetaHandler().setMeta(player, executable);
                    if (!result) {
                        DeluxeMenus.debug(DebugLevel.HIGHEST, Level.INFO, "Invalid meta action! Make sure you have the right syntax.");
                        break;
                    }
                } catch (final NumberFormatException exception) {
                    DeluxeMenus.debug(DebugLevel.HIGHEST, Level.INFO, "Invalid integer value for meta action!");
                }
                break;

            case PLAYER:
                player.chat("/" + executable);
                break;

            case PLAYER_COMMAND_EVENT:
                Bukkit.getPluginManager().callEvent(new PlayerCommandPreprocessEvent(player, "/" + executable));
                break;

            case PLACEHOLDER:
                holder.ifPresent(it -> it.setPlaceholders(executable));
                break;

            case CHAT:
                player.chat(executable);
                break;

            case CONSOLE:
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), executable);
                break;

            case MINI_MESSAGE:
                plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(executable));
                break;

            case MINI_BROADCAST:
                plugin.adventure().all().sendMessage(MiniMessage.miniMessage().deserialize(executable));
                break;

            case MESSAGE:
                player.sendMessage(StringUtils.color(executable));
                break;

            case BROADCAST:
                Bukkit.broadcastMessage(StringUtils.color(executable));
                break;

            case CLOSE:
                Menu.closeMenu(player, true, true);
                break;

            case OPEN_GUI_MENU:
            case OPEN_MENU:
                final String temporaryExecutable = executable.replaceAll("\\s+", " ").replace("  ", " ");
                final String[] executableParts = temporaryExecutable.split(" ", 2);

                if (executableParts.length == 0) {
                    DeluxeMenus.debug(DebugLevel.HIGHEST, Level.WARNING, "Could not find and open menu " + executable);
                    break;
                }

                final String menuName = executableParts[0];

                final Optional<Menu> optionalMenuToOpen = Menu.getMenuByName(menuName);

                if (optionalMenuToOpen.isEmpty()) {
                    DeluxeMenus.debug(DebugLevel.HIGHEST, Level.WARNING, "Could not find and open menu " + executable);
                    break;
                }

                final Menu menuToOpen = optionalMenuToOpen.get();

                final List<String> menuArgumentNames = menuToOpen.options().arguments();

                String[] passedArgumentValues = null;
                if (executableParts.length > 1) {
                    passedArgumentValues = executableParts[1].split(" ");
                }

                if (menuArgumentNames.isEmpty()) {
                    if (passedArgumentValues != null && passedArgumentValues.length > 0) {
                        DeluxeMenus.debug(
                                DebugLevel.HIGHEST,
                                Level.WARNING,
                                "Arguments were given for menu " + menuName + " in action [openguimenu] or [openmenu], but the menu does not support arguments!"
                        );
                    }

                    if (holder.isEmpty()) {
                        menuToOpen.openMenu(player);
                        break;
                    }

                    menuToOpen.openMenu(player, holder.get().getTypedArgs(), holder.get().getPlaceholderPlayer());
                    break;
                }

                if (passedArgumentValues == null || passedArgumentValues.length == 0) {
                    // Replicate old behavior: If no arguments are given, open the menu with the arguments from the current menu
                    if (holder.isEmpty()) {
                        menuToOpen.openMenu(player);
                        break;
                    }

                    menuToOpen.openMenu(player, holder.get().getTypedArgs(), holder.get().getPlaceholderPlayer());
                    break;
                }

                if (passedArgumentValues.length < menuArgumentNames.size()) {
                    DeluxeMenus.debug(
                            DebugLevel.HIGHEST,
                            Level.WARNING,
                            "Not enough arguments given for menu " + menuName + " when opening using the [openguimenu] or [openmenu] action!"
                    );
                    break;
                }

                final Map<String, String> argumentsMap = new HashMap<>();
                if (holder.isPresent() && holder.get().getTypedArgs() != null) {
                    // Pass the arguments from the current menu to the new menu. If the new menu has arguments with the
                    // same name, they will be overwritten
                    argumentsMap.putAll(holder.get().getTypedArgs());
                }

                for (int index = 0; index < menuArgumentNames.size(); index++) {
                    final String argumentName = menuArgumentNames.get(index);

                    if (passedArgumentValues.length <= index) {
                        // This should never be the case!
                        DeluxeMenus.debug(
                                DebugLevel.HIGHEST,
                                Level.WARNING,
                                "Not enough arguments given for menu " + menuName + " when opening using the [openguimenu] or [openmenu] action!"
                        );
                        break;
                    }

                    if (menuArgumentNames.size() == index + 1) {
                        // If this is the last argument, get all remaining values and join them
                        final String lastArgumentValue = String.join(" ", Arrays.asList(passedArgumentValues).subList(index, passedArgumentValues.length));
                        argumentsMap.put(argumentName, lastArgumentValue);
                        break;
                    }

                    argumentsMap.put(argumentName, passedArgumentValues[index]);
                }

                if (holder.isEmpty()) {
                    menuToOpen.openMenu(player, argumentsMap, null);
                    break;
                }

                menuToOpen.openMenu(player, argumentsMap, holder.get().getPlaceholderPlayer());
                break;

            case CONNECT:
                DeluxeMenus.getInstance().connect(player, executable);
                break;

            case JSON_MESSAGE:
                AdventureUtils.sendJson(player, executable);
                break;

            case JSON_BROADCAST:
            case BROADCAST_JSON:
                plugin.adventure().all().sendMessage(AdventureUtils.fromJson(executable));
                break;

            case REFRESH:
                if (holder.isEmpty()) {
                    DeluxeMenus.debug(
                            DebugLevel.MEDIUM,
                            Level.WARNING,
                            player.getName() + " does not have menu open! Nothing to refresh!"
                    );
                    break;
                }

                holder.get().refreshMenu();
                break;

            case TAKE_MONEY:
                if (DeluxeMenus.getInstance().getVault() == null || !DeluxeMenus.getInstance().getVault().hooked()) {
                    DeluxeMenus.debug(DebugLevel.HIGHEST, Level.WARNING, "Vault not hooked! Cannot take money!");
                    break;
                }

                try {
                    DeluxeMenus.getInstance().getVault().takeMoney(player, Double.parseDouble(executable));
                } catch (final NumberFormatException exception) {
                    DeluxeMenus.debug(
                            DebugLevel.HIGHEST,
                            Level.WARNING,
                            "Amount for take money action: " + executable + ", is not a valid number!"
                    );
                }
                break;

            case GIVE_MONEY:
                if (DeluxeMenus.getInstance().getVault() == null || !DeluxeMenus.getInstance().getVault().hooked()) {
                    DeluxeMenus.debug(DebugLevel.HIGHEST, Level.WARNING, "Vault not hooked! Cannot give money!");
                    break;
                }

                try {
                    DeluxeMenus.getInstance().getVault().giveMoney(player, Double.parseDouble(executable));
                } catch (final NumberFormatException exception) {
                    DeluxeMenus.debug(
                            DebugLevel.HIGHEST,
                            Level.WARNING,
                            "Amount for give money action: " + executable + ", is not a valid number!"
                    );
                }
                break;

            case TAKE_EXP:
            case GIVE_EXP:
                final String lowerCaseExecutable = executable.toLowerCase();

                try {
                    if (Integer.parseInt(lowerCaseExecutable.replaceAll("l", "")) <= 0) break;

                    if (actionType == ActionType.TAKE_EXP) {
                        ExpUtils.setExp(player, "-" + lowerCaseExecutable);
                        break;
                    }

                    ExpUtils.setExp(player, lowerCaseExecutable);
                    break;

                } catch (final NumberFormatException exception) {
                    if (actionType == ActionType.TAKE_EXP) {
                        DeluxeMenus.debug(
                                DebugLevel.HIGHEST,
                                Level.WARNING,
                                "Amount for take exp action: " + executable + ", is not a valid number!"
                        );
                        break;
                    }

                    DeluxeMenus.debug(
                            DebugLevel.HIGHEST,
                            Level.WARNING,
                            "Amount for give exp action: " + executable + ", is not a valid number!"
                    );
                    break;
                }

            case GIVE_PERM:
                if (DeluxeMenus.getInstance().getVault() == null || !DeluxeMenus.getInstance().getVault().hooked()) {
                    DeluxeMenus.debug(
                            DebugLevel.HIGHEST,
                            Level.WARNING,
                            "Vault not hooked! Cannot give permission: " + executable + "!");
                    break;
                }

                DeluxeMenus.getInstance().getVault().givePermission(player, executable);
                break;

            case TAKE_PERM:
                if (DeluxeMenus.getInstance().getVault() == null || !DeluxeMenus.getInstance().getVault().hooked()) {
                    DeluxeMenus.debug(
                            DebugLevel.HIGHEST,
                            Level.WARNING,
                            "Vault not hooked! Cannot take permission: " + executable + "!");
                    break;
                }

                DeluxeMenus.getInstance().getVault().takePermission(player, executable);
                break;

            case BROADCAST_SOUND:
            case BROADCAST_WORLD_SOUND:
            case PLAY_SOUND:
                final Sound sound;
                float volume = 1;
                float pitch = 1;

                if (!executable.contains(" ")) {
                    try {
                        sound = Sound.valueOf(executable.toUpperCase());
                    } catch (final IllegalArgumentException exception) {
                        DeluxeMenus.printStacktrace(
                                "Sound name given for sound action: " + executable + ", is not a valid sound!",
                                exception
                        );
                        break;
                    }
                } else {
                    String[] parts = executable.split(" ", 3);

                    try {
                        sound = Sound.valueOf(parts[0].toUpperCase());
                    } catch (final IllegalArgumentException exception) {
                        DeluxeMenus.printStacktrace(
                                "Sound name given for sound action: " + parts[0] + ", is not a valid sound!",
                                exception
                        );
                        break;
                    }

                    if (parts.length == 3) {
                        try {
                            pitch = Float.parseFloat(parts[2]);
                        } catch (final NumberFormatException exception) {
                            DeluxeMenus.debug(
                                    DebugLevel.HIGHEST,
                                    Level.WARNING,
                                    "Pitch given for sound action: " + parts[2] + ", is not a valid number!"
                            );

                            DeluxeMenus.printStacktrace(
                                    "Pitch given for sound action: " + parts[2] + ", is not a valid number!",
                                    exception
                            );
                        }
                    }


                    try {
                        volume = Float.parseFloat(parts[1]);
                    } catch (final NumberFormatException exception) {
                        DeluxeMenus.debug(
                                DebugLevel.HIGHEST,
                                Level.WARNING,
                                "Volume given for sound action: " + parts[1] + ", is not a valid number!"
                        );

                        DeluxeMenus.printStacktrace(
                                "Volume given for sound action: " + parts[1] + ", is not a valid number!",
                                exception
                        );
                    }
                }

                switch (actionType) {
                    case BROADCAST_SOUND:
                        for (final Player broadcastTarget : Bukkit.getOnlinePlayers()) {
                            broadcastTarget.playSound(broadcastTarget.getLocation(), sound, volume, pitch);
                        }
                        break;

                    case BROADCAST_WORLD_SOUND:
                        for (final Player broadcastTarget : player.getWorld().getPlayers()) {
                            broadcastTarget.playSound(broadcastTarget.getLocation(), sound, volume, pitch);
                        }
                        break;

                    case PLAY_SOUND:
                        player.playSound(player.getLocation(), sound, volume, pitch);
                        break;
                }
                break;

            case SET_ITEM, SET_LORE, SET_NAME: {
                if (holder.isEmpty()) {
                    DeluxeMenus.debug(
                            DebugLevel.MEDIUM,
                            Level.WARNING,
                            player.getName() + " does not have menu open!"
                    );
                    break;
                }

                // 40 4 item
                String[] args = executable.split(" ");
                if(args.length >= 3) {
                    int time = Integer.parseInt(args[0]);
                    Integer slot = Integer.parseInt(args[1]);
                    SetHolder setHolder = holder.get().getHoldItems();
                    String object = org.apache.commons.lang3.StringUtils.join(args, " ", 2, args.length);

                    MenuItem item = holder.get().getItem(slot);
                    ItemStack itemStack = item.getItemStack(holder.get());
                    ItemMeta itemMeta = itemStack.getItemMeta();
                    Object old = null;
                    switch (actionType) {
                        case SET_ITEM -> {
                            if(setHolder.material.contains(slot)) {
                                return;
                            }
                            old = itemStack.getType();
                            item.options().setMaterial(object);
                            itemStack.setType(Material.valueOf(object.toUpperCase()));
                        }
                        case SET_NAME -> {
                            if(setHolder.name.contains(slot)) {
                                return;
                            }
                            old = item.options().displayName().orElse(null);
                            item.options().setDisplayName(object);
                            if (itemMeta != null) {
                                itemMeta.setDisplayName(object);
                                itemStack.setItemMeta(itemMeta);
                            }
                        }
                        case SET_LORE -> {
                            if(setHolder.lore.contains(slot)) {
                                return;
                            }
                            old = item.options().lore();
                            List<String> lore = Collections.singletonList(object);
                            item.options().setLore(lore);
                            if (itemMeta != null) {
                                itemMeta.setLore(lore);
                                itemStack.setItemMeta(itemMeta);
                            }
                        }
                    }
                    holder.get().refreshMenu();
                    Object finalOld = old;
                    // SET_ITEM
                    if(!setHolder.material.contains(slot)) {
                        if(actionType == ActionType.SET_ITEM) {
                            setHolder.material.add(slot);
                            Bukkit.getScheduler().runTaskLaterAsynchronously(DeluxeMenus.getInstance(), () -> {
                                Material material = (Material) finalOld;
                                item.options().setMaterial(material.name());
                                itemStack.setType(material);
                                holder.get().getHoldItems().material.remove(slot);
                                holder.get().refreshMenu();
                            }, time);
                        }
                    }
                    // SET NAME
                    if(!setHolder.name.contains(slot)) {
                        if(actionType == ActionType.SET_NAME) {
                            setHolder.name.add(slot);
                            Bukkit.getScheduler().runTaskLaterAsynchronously(DeluxeMenus.getInstance(), () -> {
                                String name = (String) finalOld;
                                item.options().setDisplayName(name);
                                if(itemMeta != null) {
                                    itemMeta.setDisplayName((String) finalOld);
                                    itemStack.setItemMeta(itemMeta);
                                }
                                holder.get().getHoldItems().name.remove(slot);
                                holder.get().refreshMenu();
                            }, time);
                        }
                    }

                    // SET LORE
                    if(!setHolder.lore.contains(slot)) {
                        if (actionType == ActionType.SET_LORE) {
                            setHolder.lore.add(slot);
                            Bukkit.getScheduler().runTaskLaterAsynchronously(DeluxeMenus.getInstance(), () -> {
                                List<String> lore = (List<String>) finalOld;
                                item.options().setLore(lore);
                                if (itemMeta != null) {
                                    itemMeta.setLore(lore);
                                    itemStack.setItemMeta(itemMeta);
                                }
                                holder.get().getHoldItems().lore.remove(slot);
                                holder.get().refreshMenu();
                            }, time);
                        }
                    }
                } else {
                    DeluxeMenus.debug(
                            DebugLevel.MEDIUM,
                            Level.WARNING,
                            player.getName() + " not enough arguments!"
                    );
                }

                break;
            }

            default:
                break;
        }
    }
}