package dev.ascension;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum Element {
    EMBER("Ember", ChatColor.RED, Material.BLAZE_POWDER,
            "Fire Resistance", "Inferno Burst",
            "Ignites and blasts away nearby enemies."),
    TIDE("Tide", ChatColor.AQUA, Material.HEART_OF_THE_SEA,
            "Water Breathing + Dolphin's Grace in water", "Tidal Surge",
            "Shoves and slows nearby enemies, heals you."),
    GALE("Gale", ChatColor.WHITE, Material.FEATHER,
            "Speed + No Fall Damage", "Gale Dash",
            "Launch yourself in the direction you're looking."),
    TERRA("Terra", ChatColor.GREEN, Material.MOSSY_COBBLESTONE,
            "Bonus Hearts", "Stone Skin",
            "Gain Resistance and Absorption for a few seconds."),
    VOID("Void", ChatColor.DARK_PURPLE, Material.ENDER_EYE,
            "Night Vision", "Blink",
            "Teleport forward through the air.");

    private final String display;
    private final ChatColor color;
    private final Material icon;
    private final String passive;
    private final String abilityName;
    private final String abilityDesc;

    Element(String display, ChatColor color, Material icon, String passive, String abilityName, String abilityDesc) {
        this.display = display;
        this.color = color;
        this.icon = icon;
        this.passive = passive;
        this.abilityName = abilityName;
        this.abilityDesc = abilityDesc;
    }

    public String display() { return display; }
    public ChatColor color() { return color; }
    public Material icon() { return icon; }
    public String passive() { return passive; }
    public String abilityName() { return abilityName; }
    public String abilityDesc() { return abilityDesc; }
    public String colored() { return color + display + ChatColor.RESET; }

    public static Element parse(String s) {
        for (Element e : values()) {
            if (e.name().equalsIgnoreCase(s)) return e;
        }
        return null;
    }
}
