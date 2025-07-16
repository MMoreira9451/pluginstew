package com.minecraft.stewstacker;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class Main extends JavaPlugin implements Listener {

    private boolean stackingEnabled = true;
    private int maxStackSize = 64;

    @Override
    public void onEnable() {
        // Cargar configuración
        saveDefaultConfig();
        loadConfiguration();

        // Registrar eventos
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("SuspiciousStewStacker habilitado!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SuspiciousStewStacker deshabilitado!");
    }

    private void loadConfiguration() {
        stackingEnabled = getConfig().getBoolean("stacking-enabled", true);
        maxStackSize = getConfig().getInt("max-stack-size", 64);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!stackingEnabled) return;

        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // Verificar si ambos items son suspicious stews
        if (clicked != null && cursor != null &&
                clicked.getType() == Material.SUSPICIOUS_STEW &&
                cursor.getType() == Material.SUSPICIOUS_STEW) {

            // Verificar si tienen los mismos efectos
            if (haveSameEffects(clicked, cursor)) {
                event.setCancelled(true);
                stackItems(clicked, cursor, event);
            }
        }
    }

    private boolean haveSameEffects(ItemStack item1, ItemStack item2) {
        if (!(item1.getItemMeta() instanceof SuspiciousStewMeta meta1) ||
                !(item2.getItemMeta() instanceof SuspiciousStewMeta meta2)) {
            return false;
        }

        List<PotionEffect> effects1 = meta1.getCustomEffects();
        List<PotionEffect> effects2 = meta2.getCustomEffects();

        if (effects1.size() != effects2.size()) {
            return false;
        }

        for (PotionEffect effect1 : effects1) {
            boolean found = false;
            for (PotionEffect effect2 : effects2) {
                if (effect1.getType().equals(effect2.getType()) &&
                        effect1.getDuration() == effect2.getDuration() &&
                        effect1.getAmplifier() == effect2.getAmplifier()) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        return true;
    }

    private void stackItems(ItemStack clicked, ItemStack cursor, InventoryClickEvent event) {
        int clickedAmount = clicked.getAmount();
        int cursorAmount = cursor.getAmount();
        int totalAmount = clickedAmount + cursorAmount;

        if (totalAmount <= maxStackSize) {
            // Stackear completamente
            clicked.setAmount(totalAmount);
            cursor.setAmount(0);
        } else {
            // Stackear parcialmente
            clicked.setAmount(maxStackSize);
            cursor.setAmount(totalAmount - maxStackSize);
        }

        event.getWhoClicked().getInventory().setItem(event.getSlot(), clicked);
        event.getWhoClicked().setItemOnCursor(cursor);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("stewstack")) {
            return false;
        }

        if (!sender.hasPermission("stewstack.admin")) {
            sender.sendMessage("§cNo tienes permisos para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eUso: /stewstack <enable|disable|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "enable":
                stackingEnabled = true;
                getConfig().set("stacking-enabled", true);
                saveConfig();
                sender.sendMessage("§aStackeo de suspicious stews habilitado!");
                break;

            case "disable":
                stackingEnabled = false;
                getConfig().set("stacking-enabled", false);
                saveConfig();
                sender.sendMessage("§cStackeo de suspicious stews deshabilitado!");
                break;

            case "reload":
                reloadConfig();
                loadConfiguration();
                sender.sendMessage("§eConfiguración recargada!");
                break;

            default:
                sender.sendMessage("§cUso: /stewstack <enable|disable|reload>");
                break;
        }

        return true;
    }
}