package id.cyayo.gathering.model;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeType {

    private final String id;
    private final String name;
    private final String category;

    private final double hitboxWidth;
    private final double hitboxHeight;
    private final double hologramOffsetY;

    private final int chargesMin;
    private final int chargesMax;

    private final double baseGatherTimeSeconds;
    private final int inactivityResetSeconds;
    private final int cooldownSeconds;

    private final double exp;
    private final int requiredLevel;

    private final ToolRequirement toolRequirement;
    private final DropTable dropTable;

    // Effects
    private Particle particle;
    private org.bukkit.Material particleBlock;
    private int particleCount;
    private Double particleSpeed;
    private Integer particleIntervalTicks;
    private Double particleOffsetX;
    private Double particleOffsetY;
    private Double particleOffsetZ;

    private Integer swingIntervalTicks;

    private Sound soundTick;
    private float soundTickPitch;
    private Float soundTickVolume;
    private Integer soundTickIntervalTicks;

    private Sound soundComplete;
    private float soundCompletePitch;
    private Float soundCompleteVolume;

    // Hologram format
    private final Map<String, List<String>> hologramFormat = new HashMap<>();

    public NodeType(String id, YamlConfiguration config) {
        this.id = id;
        this.name = config.getString("name", id);
        this.category = config.getString("category", "GATHERING").toUpperCase();

        this.hitboxWidth = config.getDouble("hitbox.width", 1.2);
        this.hitboxHeight = config.getDouble("hitbox.height", 1.8);
        this.hologramOffsetY = config.getDouble("hitbox.hologram_offset_y", 2.3);

        this.chargesMin = config.getInt("charges.min", 8);
        this.chargesMax = Math.max(chargesMin, config.getInt("charges.max", 12));

        this.baseGatherTimeSeconds = Math.max(0.5, config.getDouble("base_gather_time_seconds", 3.0));
        this.inactivityResetSeconds = config.getInt("inactivity_reset_seconds", 180);
        this.cooldownSeconds = config.getInt("cooldown_seconds", 45);

        this.exp = config.getDouble("exp", 10.0);
        this.requiredLevel = Math.max(1, config.getInt("required_level", config.getInt("level_requirement", 1)));

        this.toolRequirement = new ToolRequirement(config.getConfigurationSection("tool_requirements"));
        this.dropTable = new DropTable(config.getConfigurationSection("drop_table"));

        loadEffects(config.getConfigurationSection("effects"));
        loadHologramFormat(config.getConfigurationSection("hologram_format"));
    }

    private void loadEffects(ConfigurationSection section) {
        if (section == null) return;
        try {
            String pStr = section.getString("particle", "CRIT");
            this.particle = Particle.valueOf(pStr.toUpperCase());
        } catch (Exception e) {
            this.particle = Particle.CRIT;
        }

        try {
            String pbStr = section.getString("particle_block");
            if (pbStr != null) {
                this.particleBlock = org.bukkit.Material.matchMaterial(pbStr);
            }
        } catch (Exception ignored) {}

        this.particleCount = section.getInt("particle_count", 5);

        if (section.contains("particle_speed")) {
            this.particleSpeed = section.getDouble("particle_speed");
        }
        if (section.contains("particle_interval_ticks")) {
            this.particleIntervalTicks = section.getInt("particle_interval_ticks");
        }
        if (section.contains("particle_offset_x")) {
            this.particleOffsetX = section.getDouble("particle_offset_x");
        }
        if (section.contains("particle_offset_y")) {
            this.particleOffsetY = section.getDouble("particle_offset_y");
        }
        if (section.contains("particle_offset_z")) {
            this.particleOffsetZ = section.getDouble("particle_offset_z");
        }

        if (section.contains("swing_interval_ticks")) {
            this.swingIntervalTicks = section.getInt("swing_interval_ticks");
        }

        try {
            String stStr = section.getString("sound_tick", "BLOCK_STONE_HIT");
            this.soundTick = Sound.valueOf(stStr.toUpperCase());
        } catch (Exception e) {
            this.soundTick = Sound.BLOCK_STONE_HIT;
        }
        this.soundTickPitch = (float) section.getDouble("sound_tick_pitch", 1.0);

        if (section.contains("sound_tick_volume")) {
            this.soundTickVolume = (float) section.getDouble("sound_tick_volume");
        }
        if (section.contains("sound_tick_interval_ticks")) {
            this.soundTickIntervalTicks = section.getInt("sound_tick_interval_ticks");
        }

        try {
            String scStr = section.getString("sound_complete", "ENTITY_EXPERIENCE_ORB_PICKUP");
            this.soundComplete = Sound.valueOf(scStr.toUpperCase());
        } catch (Exception e) {
            this.soundComplete = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
        this.soundCompletePitch = (float) section.getDouble("sound_complete_pitch", 1.2);

        if (section.contains("sound_complete_volume")) {
            this.soundCompleteVolume = (float) section.getDouble("sound_complete_volume");
        }
    }

    private void loadHologramFormat(ConfigurationSection section) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            hologramFormat.put(key.toLowerCase(), section.getStringList(key));
        }
    }

    public int rollRandomCharges() {
        if (chargesMax <= chargesMin) return chargesMin;
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(chargesMin, chargesMax + 1);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getHitboxWidth() { return hitboxWidth; }
    public double getHitboxHeight() { return hitboxHeight; }
    public double getHologramOffsetY() { return hologramOffsetY; }
    public int getChargesMin() { return chargesMin; }
    public int getChargesMax() { return chargesMax; }
    public double getBaseGatherTimeSeconds() { return baseGatherTimeSeconds; }
    public int getInactivityResetSeconds() { return inactivityResetSeconds; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public double getExp() { return exp; }
    public int getRequiredLevel() { return requiredLevel; }
    public ToolRequirement getToolRequirement() { return toolRequirement; }
    public DropTable getDropTable() { return dropTable; }
    public Particle getParticle() { return particle; }
    public org.bukkit.Material getParticleBlock() { return particleBlock; }
    public int getParticleCount() { return particleCount; }
    public Double getParticleSpeed() { return particleSpeed; }
    public Integer getParticleIntervalTicks() { return particleIntervalTicks; }
    public Double getParticleOffsetX() { return particleOffsetX; }
    public Double getParticleOffsetY() { return particleOffsetY; }
    public Double getParticleOffsetZ() { return particleOffsetZ; }
    public Integer getSwingIntervalTicks() { return swingIntervalTicks; }
    public Sound getSoundTick() { return soundTick; }
    public float getSoundTickPitch() { return soundTickPitch; }
    public Float getSoundTickVolume() { return soundTickVolume; }
    public Integer getSoundTickIntervalTicks() { return soundTickIntervalTicks; }
    public Sound getSoundComplete() { return soundComplete; }
    public float getSoundCompletePitch() { return soundCompletePitch; }
    public Float getSoundCompleteVolume() { return soundCompleteVolume; }
    public List<String> getHologramLines(String state) {
        return hologramFormat.getOrDefault(state.toLowerCase(), List.of("&e" + name));
    }
}
