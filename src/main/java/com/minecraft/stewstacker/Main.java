package com.minecraft.stewstacker;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

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
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!stackingEnabled) return;

        // Solo procesar jugadores
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        ItemStack pickedItem = event.getItem().getItemStack();

        // Verificar si es suspicious stew
        if (pickedItem.getType() != Material.SUSPICIOUS_STEW) return;

        // Buscar en el inventario items similares para stackear
        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem != null &&
                    invItem.getType() == Material.SUSPICIOUS_STEW &&
                    haveSameEffects(invItem, pickedItem)) {

                int invAmount = invItem.getAmount();
                int pickAmount = pickedItem.getAmount();
                int totalAmount = invAmount + pickAmount;

                if (totalAmount <= maxStackSize) {
                    // Stackear completamente
                    invItem.setAmount(totalAmount);
                    event.setCancelled(true);
                    event.getItem().remove();
                    return;
                } else if (invAmount < maxStackSize) {
                    // Stackear parcialmente
                    int canAdd = maxStackSize - invAmount;
                    invItem.setAmount(maxStackSize);
                    pickedItem.setAmount(pickAmount - canAdd);
                    event.getItem().getItemStack().setAmount(pickAmount - canAdd);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!stackingEnabled) return;

        // Manejar Shift + Click en resultado de crafteo
        if (event.getClick().isShiftClick() &&
                event.getSlotType() == InventoryType.SlotType.RESULT) {

            ItemStack result = event.getCurrentItem();
            if (result != null && result.getType() == Material.SUSPICIOUS_STEW) {
                event.setCancelled(true);
                handleShiftClickCrafting(event, result);
                return;
            }
        }

        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // Manejar doble click para stackear todos los items similares
        if (event.getClick().toString().contains("DOUBLE_CLICK") &&
                cursor != null && cursor.getType() == Material.SUSPICIOUS_STEW) {

            event.setCancelled(true);
            handleDoubleClick(event, cursor);
            return;
        }

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

    private void handleShiftClickCrafting(InventoryClickEvent event, ItemStack result) {
        Player player = (Player) event.getWhoClicked();

        // Verificar si es un inventario de crafteo
        if (!(event.getInventory() instanceof CraftingInventory)) {
            return;
        }

        CraftingInventory craftingInventory = (CraftingInventory) event.getInventory();

        // Obtener la cantidad máxima que se puede craftear
        int maxCraftable = getMaxCraftableAmount(craftingInventory);

        // Craftear todo lo posible
        for (int i = 0; i < maxCraftable; i++) {
            ItemStack craftedItem = result.clone();
            craftedItem.setAmount(1);

            // Consumir materiales
            consumeCraftingMaterials(craftingInventory);

            // Intentar stackear con items existentes
            if (!addToInventoryWithStacking(player, craftedItem)) {
                // Si no se pudo agregar, parar
                break;
            }
        }

        // Actualizar el resultado del crafteo
        craftingInventory.setResult(craftingInventory.getResult());
    }

    private int getMaxCraftableAmount(CraftingInventory craftingInventory) {
        // Contar materiales disponibles
        ItemStack[] matrix = craftingInventory.getMatrix();
        int minAmount = Integer.MAX_VALUE;

        for (ItemStack item : matrix) {
            if (item != null && item.getAmount() > 0) {
                minAmount = Math.min(minAmount, item.getAmount());
            }
        }

        return minAmount == Integer.MAX_VALUE ? 0 : minAmount;
    }

    private void consumeCraftingMaterials(CraftingInventory craftingInventory) {
        ItemStack[] matrix = craftingInventory.getMatrix();

        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item != null && item.getAmount() > 0) {
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) {
                    matrix[i] = null;
                }
            }
        }

        craftingInventory.setMatrix(matrix);
    }

    private boolean addToInventoryWithStacking(Player player, ItemStack newItem) {
        // Primero intentar stackear con items existentes
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack invItem = player.getInventory().getItem(i);

            if (invItem != null &&
                    invItem.getType() == Material.SUSPICIOUS_STEW &&
                    haveSameEffects(invItem, newItem)) {

                int invAmount = invItem.getAmount();
                int newAmount = newItem.getAmount();
                int totalAmount = invAmount + newAmount;

                if (totalAmount <= maxStackSize) {
                    // Stackear completamente
                    invItem.setAmount(totalAmount);
                    return true;
                } else if (invAmount < maxStackSize) {
                    // Stackear parcialmente
                    int canAdd = maxStackSize - invAmount;
                    invItem.setAmount(maxStackSize);
                    newItem.setAmount(newAmount - canAdd);

                    // Continuar buscando más slots para el resto
                    if (newItem.getAmount() > 0) {
                        return addToInventoryWithStacking(player, newItem);
                    }
                    return true;
                }
            }
        }

        // Si no se pudo stackear, intentar agregar en slot vacío
        return player.getInventory().addItem(newItem).isEmpty();
    }

    private void handleDoubleClick(InventoryClickEvent event, ItemStack cursorItem) {
        Player player = (Player) event.getWhoClicked();

        // Buscar todos los items similares en el inventario
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack invItem = player.getInventory().getItem(i);

            if (invItem != null &&
                    invItem.getType() == Material.SUSPICIOUS_STEW &&
                    haveSameEffects(invItem, cursorItem)) {

                int cursorAmount = cursorItem.getAmount();
                int invAmount = invItem.getAmount();
                int totalAmount = cursorAmount + invAmount;

                if (totalAmount <= maxStackSize) {
                    // Stackear completamente
                    cursorItem.setAmount(totalAmount);
                    player.getInventory().setItem(i, null);
                } else {
                    // Stackear parcialmente
                    int canAdd = maxStackSize - cursorAmount;
                    if (canAdd > 0) {
                        cursorItem.setAmount(maxStackSize);
                        invItem.setAmount(invAmount - canAdd);
                        player.getInventory().setItem(i, invItem);
                    }
                    break;
                }
            }
        }

        event.getWhoClicked().setItemOnCursor(cursorItem);
    }

    private void autoStackInInventory(Player player, ItemStack newItem) {
        // Buscar slots con items similares
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack invItem = player.getInventory().getItem(i);

            if (invItem != null &&
                    invItem.getType() == Material.SUSPICIOUS_STEW &&
                    haveSameEffects(invItem, newItem)) {

                int invAmount = invItem.getAmount();
                int newAmount = newItem.getAmount();
                int totalAmount = invAmount + newAmount;

                if (totalAmount <= maxStackSize) {
                    // Stackear completamente
                    invItem.setAmount(totalAmount);
                    newItem.setAmount(0);
                    return;
                } else if (invAmount < maxStackSize) {
                    // Stackear parcialmente
                    int canAdd = maxStackSize - invAmount;
                    invItem.setAmount(maxStackSize);
                    newItem.setAmount(newAmount - canAdd);
                }
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