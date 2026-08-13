package com.heyimsoap.dmzmultiverse.forms;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.heyimsoap.dmzmultiverse.DMZMultiverse;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Installs and identifies the forms supplied by Dragon Mine Z: Multiverse.
 *
 * <p>The form files are deliberately disk-backed. Dragon Mine Z synchronizes
 * its configuration files to multiplayer clients, so a purely in-memory form
 * registration would disappear when that synchronization batch begins. New
 * form files are written once and are never overwritten, allowing server
 * owners to tune every generated value afterwards.</p>
 */
public final class MultiverseForms {
    public static final String SAIYAN_RACE = "saiyan";

    public static final String GOD_GROUP = "dmz_multiverse_godforms";
    public static final String ULTRA_GROUP = "dmz_multiverse_ultraforms";
    public static final String ALIGNMENT_GROUP = "dmz_multiverse_alignment_variants";

    // Contains "god" so DMZ treats the group as divine, but deliberately does
    // not contain the contiguous "godform" token that DMZ aliases to the
    // built-in godforms skill.
    public static final String GOD_SKILL = "dmz_multiverse_god_tiers";
    public static final String ULTRA_SKILL = ULTRA_GROUP;
    // The hidden group still contains "god" so DMZ's ki-sense and lock-on
    // rules classify its alignment-only palettes as divine transformations.
    public static final String INTERNAL_SKILL = "dmz_multiverse_god_internal";

    public static final String SUPER_SAIYAN_GOD = "super_saiyan_god";
    public static final String SUPER_SAIYAN_BLUE = "super_saiyan_blue";
    public static final String SUPER_SAIYAN_EVOLVED = "super_saiyan_evolved";
    public static final String ULTRA_INSTINCT = "ultra_instinct";
    public static final String ULTRA_EGO = "ultra_ego";
    public static final String MASTERED_ULTRA_INSTINCT = "mastered_ultra_instinct";

    public static final String SUPER_SAIYAN_ROSE = "super_saiyan_rose";
    public static final String SUPER_SAIYAN_EVOLVED_CORRUPTED = "super_saiyan_evolved_corrupted";

    public static final int EVIL_ALIGNMENT_MAX = 40;

    private static final String CONFIG_ROOT = "dragonminez";
    private static final String SKILLS_CONFIG = "skills";
    private static final String SAIYAN_CHARACTER_CONFIG = "races/saiyan/character";
    private static final String GOD_FORM_CONFIG = "races/saiyan/forms/" + GOD_GROUP;
    private static final String ULTRA_FORM_CONFIG = "races/saiyan/forms/" + ULTRA_GROUP;
    private static final String ALIGNMENT_FORM_CONFIG = "races/saiyan/forms/" + ALIGNMENT_GROUP;

    private static final String RED = "#DB182C";
    private static final String BLUE = "#0095FF";
    private static final String ROSE = "#F6A6FF";
    private static final String ROYAL_BLUE = "#244CFF";
    private static final String DARK_ROSE = "#9E092F";
    private static final String SILVER = "#D8E4F0";
    private static final String LIGHT_BLUE = "#7FFFFF";
    private static final String PURPLE = "#8A2BE2";
    private static final String WHITE = "#FFFFFF";
    private static final String SSJ2_LIGHTNING = "#A1FFF9";

    private static final Float[] DEFAULT_SAIYAN_SCALE = {0.9375F, 0.9375F, 0.9375F};
    private static final Float[] SSJ4_D_SCALE = {1.2F, 1.2F, 1.2F};

    private static final Set<String> GROUPS = Set.of(GOD_GROUP, ULTRA_GROUP, ALIGNMENT_GROUP);
    private static final Set<String> GOD_FORMS = Set.of(SUPER_SAIYAN_GOD, SUPER_SAIYAN_BLUE, SUPER_SAIYAN_EVOLVED);
    private static final Set<String> ULTRA_FORMS = Set.of(ULTRA_INSTINCT, ULTRA_EGO, MASTERED_ULTRA_INSTINCT);
    private static final Set<String> ALIGNMENT_FORMS = Set.of(SUPER_SAIYAN_ROSE, SUPER_SAIYAN_EVOLVED_CORRUPTED);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object INSTALL_LOCK = new Object();

    private static volatile boolean installed;

    private MultiverseForms() {
    }

    public static boolean isInstalled() {
        return installed;
    }

    /**
     * Creates missing form files, minimally merges the two DMZ progression
     * configs, reloads the touched configs, and makes their paths visible to
     * Dragon Mine Z's multiplayer config synchronization.
     */
    public static void install() {
        if (installed) {
            return;
        }

        synchronized (INSTALL_LOCK) {
            if (installed) {
                return;
            }

            try {
                Path dmzConfigRoot = FMLPaths.CONFIGDIR.get().resolve(CONFIG_ROOT);

                writeFormIfAbsent(dmzConfigRoot, GOD_FORM_CONFIG, createGodGroup());
                writeFormIfAbsent(dmzConfigRoot, ULTRA_FORM_CONFIG, createUltraGroup());
                writeFormIfAbsent(dmzConfigRoot, ALIGNMENT_FORM_CONFIG, createAlignmentGroup());

                mergeSkillsConfig(dmzConfigRoot.resolve(SKILLS_CONFIG + ".json"));
                mergeSaiyanCosts(dmzConfigRoot.resolve(SAIYAN_CHARACTER_CONFIG + ".json"));

                reload(SKILLS_CONFIG);
                reload(SAIYAN_CHARACTER_CONFIG);
                reload(GOD_FORM_CONFIG);
                reload(ULTRA_FORM_CONFIG);
                reload(ALIGNMENT_FORM_CONFIG);

                // ConfigManager returns its live cache here. Adding our paths
                // also covers installation after that cache was first scanned.
                List<String> syncPaths = ConfigManager.getAvailableConfigFiles();
                addPathIfMissing(syncPaths, GOD_FORM_CONFIG);
                addPathIfMissing(syncPaths, ULTRA_FORM_CONFIG);
                addPathIfMissing(syncPaths, ALIGNMENT_FORM_CONFIG);

                installed = true;
                DMZMultiverse.LOGGER.info("Installed Dragon Mine Z: Multiverse Saiyan form groups");
            } catch (Exception exception) {
                DMZMultiverse.LOGGER.error("Could not install Dragon Mine Z: Multiverse forms", exception);
            }
        }
    }

    public static boolean isMultiverseGroup(String groupId) {
        return GROUPS.contains(normalize(groupId));
    }

    public static boolean isMultiverseForm(String formId) {
        String normalized = normalize(formId);
        return GOD_FORMS.contains(normalized) || ULTRA_FORMS.contains(normalized) || ALIGNMENT_FORMS.contains(normalized);
    }

    public static boolean isMultiverseForm(String groupId, String formId) {
        String group = normalize(groupId);
        String form = normalize(formId);
        return switch (group) {
            case GOD_GROUP -> GOD_FORMS.contains(form);
            case ULTRA_GROUP -> ULTRA_FORMS.contains(form);
            case ALIGNMENT_GROUP -> ALIGNMENT_FORMS.contains(form);
            default -> false;
        };
    }

    public static boolean isAlignmentVariant(String groupId, String formId) {
        return ALIGNMENT_GROUP.equals(normalize(groupId)) && ALIGNMENT_FORMS.contains(normalize(formId));
    }

    /** Returns the canonical form id for a canonical or internal variant id. */
    public static String canonicalEquivalent(String formId) {
        return switch (normalize(formId)) {
            case SUPER_SAIYAN_ROSE -> SUPER_SAIYAN_BLUE;
            case SUPER_SAIYAN_EVOLVED_CORRUPTED -> SUPER_SAIYAN_EVOLVED;
            default -> normalize(formId);
        };
    }

    /** Returns the canonical group/form pair for sound and transition logic. */
    public static FormKey canonicalEquivalent(String groupId, String formId) {
        String group = normalize(groupId);
        String form = canonicalEquivalent(formId);
        if (ALIGNMENT_GROUP.equals(group) && ALIGNMENT_FORMS.contains(normalize(formId))) {
            group = GOD_GROUP;
        }
        return new FormKey(group, form);
    }

    /**
     * Alignment palette changes are presentation swaps, not transformations.
     * Callers can use this helper to suppress transformation sounds/effects.
     */
    public static boolean isSilentVariantSwap(String oldGroup, String oldForm, String newGroup, String newForm) {
        FormKey oldCanonical = canonicalEquivalent(oldGroup, oldForm);
        FormKey newCanonical = canonicalEquivalent(newGroup, newForm);
        return oldCanonical.equals(newCanonical)
                && (!normalize(oldGroup).equals(normalize(newGroup)) || !normalize(oldForm).equals(normalize(newForm)));
    }

    public record FormKey(String group, String form) {
    }

    private static void writeFormIfAbsent(Path root, String configPath, FormConfig formConfig) throws IOException {
        Path path = root.resolve(configPath + ".json");
        Files.createDirectories(path.getParent());
        try {
            Files.writeString(
                    path,
                    GSON.toJson(formConfig) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            DMZMultiverse.LOGGER.info("Created first-run Dragon Mine Z form config {}", path);
        } catch (FileAlreadyExistsException ignored) {
            // Existing files belong to the server owner and are never replaced.
        }
    }

    private static void mergeSkillsConfig(Path path) throws IOException {
        JsonObject root = readObject(path);
        boolean changed = false;

        JsonArray formSkills = getOrCreateArray(root, "formSkills", path);
        changed |= addStringIfMissing(formSkills, GOD_SKILL);
        changed |= addStringIfMissing(formSkills, ULTRA_SKILL);

        JsonObject skills = getOrCreateObject(root, "skills", path);
        if (!skills.has(INTERNAL_SKILL)) {
            JsonObject internal = new JsonObject();
            internal.add("costs", new JsonArray());
            JsonArray allowedRaces = new JsonArray();
            allowedRaces.add("__internal__");
            internal.add("allowedRaces", allowedRaces);
            skills.add(INTERNAL_SKILL, internal);
            changed = true;
        }

        if (changed) {
            writeObjectAtomically(path, root);
        }
    }

    private static void mergeSaiyanCosts(Path path) throws IOException {
        JsonObject root = readObject(path);
        JsonObject costs = getOrCreateObject(root, "formSkillsCosts", path);
        boolean changed = false;

        if (!costs.has(GOD_SKILL)) {
            costs.add(GOD_SKILL, formSkillCost(5_000_000, 10_000_000, 15_000_000));
            changed = true;
        }
        if (!costs.has(ULTRA_SKILL)) {
            costs.add(ULTRA_SKILL, formSkillCost(30_000_000, 50_000_000, 55_000_000));
            changed = true;
        }

        if (changed) {
            writeObjectAtomically(path, root);
        }
    }

    private static JsonObject formSkillCost(int... prices) {
        JsonObject cost = new JsonObject();
        cost.addProperty("buyFromMaster", false);
        JsonArray priceArray = new JsonArray();
        for (int price : prices) {
            priceArray.add(price);
        }
        cost.add("prices", priceArray);
        return cost;
    }

    private static JsonObject readObject(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Required Dragon Mine Z config does not exist: " + path);
        }
        try {
            JsonElement element = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                throw new IOException("Expected a JSON object in " + path);
            }
            return element.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Could not parse " + path, exception);
        }
    }

    private static JsonArray getOrCreateArray(JsonObject root, String key, Path source) throws IOException {
        if (!root.has(key)) {
            JsonArray created = new JsonArray();
            root.add(key, created);
            return created;
        }
        if (!root.get(key).isJsonArray()) {
            throw new IOException("Expected '" + key + "' to be an array in " + source);
        }
        return root.getAsJsonArray(key);
    }

    private static JsonObject getOrCreateObject(JsonObject root, String key, Path source) throws IOException {
        if (!root.has(key)) {
            JsonObject created = new JsonObject();
            root.add(key, created);
            return created;
        }
        if (!root.get(key).isJsonObject()) {
            throw new IOException("Expected '" + key + "' to be an object in " + source);
        }
        return root.getAsJsonObject(key);
    }

    private static boolean addStringIfMissing(JsonArray array, String value) {
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && element.getAsString().equalsIgnoreCase(value)) {
                return false;
            }
        }
        array.add(value);
        return true;
    }

    private static void writeObjectAtomically(Path path, JsonObject root) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".dmz_multiverse.tmp");
        try {
            Files.writeString(
                    temp,
                    GSON.toJson(root) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void reload(String configPath) throws IOException {
        ConfigManager.reloadSpecificConfig(configPath);
    }

    private static void addPathIfMissing(List<String> paths, String value) {
        for (String path : paths) {
            if (path.equalsIgnoreCase(value)) {
                return;
            }
        }
        paths.add(value);
    }

    private static FormConfig createGodGroup() {
        FormConfig group = group(GOD_GROUP, GOD_SKILL);
        Map<String, FormConfig.FormData> forms = new LinkedHashMap<>();

        forms.put(SUPER_SAIYAN_GOD, form(
                 SUPER_SAIYAN_GOD, 1, "base", RED, RED, RED, RED,
                DEFAULT_SAIYAN_SCALE, 5.00, 3.6250, 0.24,
                "", 0.0, false, "", false, "", "", 1.5,
                List.of()
        ));
        forms.put(SUPER_SAIYAN_BLUE, form(
                SUPER_SAIYAN_BLUE, 2, "ssj", BLUE, BLUE, BLUE, BLUE,
                DEFAULT_SAIYAN_SCALE, 5.50, 4.0000, 0.26,
                GOD_GROUP + "." + SUPER_SAIYAN_GOD, 100.0, false, "", false, "", "", 1.5,
                List.of()
        ));
        forms.put(SUPER_SAIYAN_EVOLVED, form(
                SUPER_SAIYAN_EVOLVED, 3, "ssj2", ROYAL_BLUE, ROYAL_BLUE, ROYAL_BLUE, BLUE,
                DEFAULT_SAIYAN_SCALE, 6.00, 4.3750, 0.28,
                GOD_GROUP + "." + SUPER_SAIYAN_BLUE, 100.0, true, SSJ2_LIGHTNING, false, "", "", 1.5,
                List.of()
        ));

        group.setForms(forms);
        return group;
    }

    private static FormConfig createUltraGroup() {
        FormConfig group = group(ULTRA_GROUP, ULTRA_SKILL);
        Map<String, FormConfig.FormData> forms = new LinkedHashMap<>();

        forms.put(ULTRA_INSTINCT, form(
                ULTRA_INSTINCT, 1, "base", "", SILVER, "", LIGHT_BLUE,
                DEFAULT_SAIYAN_SCALE, 7.50, 5.2500, 0.30,
                GOD_GROUP + "." + SUPER_SAIYAN_BLUE, 100.0, false, "", true, WHITE, WHITE, 3.7,
                List.of()
        ));
        forms.put(ULTRA_EGO, form(
                ULTRA_EGO, 2, "ssj2", PURPLE, PURPLE, PURPLE, PURPLE,
                SSJ4_D_SCALE, 9.00, 6.2500, 0.32,
                GOD_GROUP + "." + SUPER_SAIYAN_EVOLVED, 100.0, true, PURPLE, true, PURPLE, PURPLE, 3.7,
                List.of()
        ));
        forms.put(MASTERED_ULTRA_INSTINCT, form(
                MASTERED_ULTRA_INSTINCT, 3, "base", WHITE, SILVER, WHITE, WHITE,
                DEFAULT_SAIYAN_SCALE, 9.75, 6.7500, 0.34,
                ULTRA_GROUP + "." + ULTRA_INSTINCT, 100.0, false, "", true, WHITE, WHITE, 3.7,
                List.of()
        ));

        group.setForms(forms);
        return group;
    }

    private static FormConfig createAlignmentGroup() {
        FormConfig group = group(ALIGNMENT_GROUP, INTERNAL_SKILL);
        Map<String, FormConfig.FormData> forms = new LinkedHashMap<>();

        forms.put(SUPER_SAIYAN_ROSE, form(
                SUPER_SAIYAN_ROSE, 4, "ssj", ROSE, ROSE, ROSE, RED,
                DEFAULT_SAIYAN_SCALE, 5.50, 4.0000, 0.26,
                GOD_GROUP + "." + SUPER_SAIYAN_GOD, 100.0, false, "", false, "", "", 1.5,
                List.of()
        ));
        forms.put(SUPER_SAIYAN_EVOLVED_CORRUPTED, form(
                SUPER_SAIYAN_EVOLVED_CORRUPTED, 4, "ssj2", DARK_ROSE, DARK_ROSE, DARK_ROSE, RED,
                DEFAULT_SAIYAN_SCALE, 6.00, 4.3750, 0.28,
                GOD_GROUP + "." + SUPER_SAIYAN_BLUE, 100.0, true, SSJ2_LIGHTNING, false, "", "", 1.5,
                List.of()
        ));

        group.setForms(forms);
        return group;
    }

    private static FormConfig group(String groupName, String formType) {
        FormConfig group = new FormConfig();
        group.setConfigVersion(ConfigManager.CONFIG_VERSION);
        group.setGroupName(groupName);
        group.setFormType(formType);
        return group;
    }

    @SuppressWarnings("ParameterNumber")
    private static FormConfig.FormData form(
            String name,
            int unlockLevel,
            String hairType,
            String hairColor,
            String eyeColor,
            String tailColor,
            String auraColor,
            Float[] modelScaling,
            double offense,
            double defense,
            double energyDrain,
            String requisite,
            double requisiteMastery,
            boolean lightning,
            String lightningColor,
            boolean outlineEnabled,
            String outlinePrimary,
            String outlineSecondary,
            double outlineThickness,
            List<String> shareMasteryWith
    ) {
        FormConfig.FormData form = new FormConfig.FormData();
        form.setName(name);
        form.setUnlockOnSkillLevel(unlockLevel);
        form.setFormCombo("");
        form.setCustomModel("");
        form.setKeepBaseFormHeadBones(false);
        form.setTransformationAnimation("transf.generic");
        form.setBodyColor1("");
        form.setBodyColor2(tailColor);
        form.setBodyColor3("");
        form.setExtraFormLayer("");
        form.setExtraFormColor("");
        form.setHairType(hairType);
        form.setForcedHairCode("");
        form.setHairColor(hairColor);
        form.setEye1Color(eyeColor);
        form.setEye2Color(eyeColor);
        form.setAuraType("kakarot");
        form.setAuraLayer(0);
        form.setAuraColor(auraColor);
        form.setExtraAuraLayer(-1);
        form.setExtraAuraColor(WHITE);
        form.setExtraAuraType("kakarot");
        form.setHasLightnings(lightning);
        form.setLightningColor(lightningColor);
        form.setTintColor("#FF0000");
        form.setTintIntensity(0.0);
        form.setModelScaling(modelScaling.clone());

        form.setStrMultiplier(offense);
        form.setSkpMultiplier(offense);
        form.setStmMultiplier(1.0);
        form.setDefMultiplier(defense);
        form.setVitMultiplier(1.0);
        form.setPwrMultiplier(offense);
        form.setEneMultiplier(1.0);
        form.setSpeedMultiplier(1.0);
        form.setStaminaDrainMultiplier(1.0);
        form.setEnergyDrain(energyDrain);
        form.setStaminaDrain(0.0);
        form.setHealthDrain(0.0);
        form.setAttackSpeed(1.0);

        form.setMaxMastery(100.0);
        form.setMasteryPerHitDealt(0.04);
        form.setMasteryPerHitReceived(0.04);
        form.setPassiveMasteryEveryFiveSeconds(0.006);
        form.setMaxCostMultiplier(0.75);
        form.setMaxStatsMultiplier(1.5);
        form.setFormRequisite(requisite);
        form.setFormRequisiteType("all");
        form.setUnlockOnMastery(requisiteMastery);
        form.setStackOnMastery(25.0);
        form.setInstantTransformOnMastery(40.0);
        // Branching forms must be directly selectable once their explicit
        // prerequisite is met. A self-mastery gate here would make a fresh
        // MUI impossible without first following the Ultra Ego branch.
        form.setAllowFreeTransformOnMastery(0.0);
        form.setFormStackable(true);
        form.setStackDrainMultiplier(2.0);
        form.setIncompatibleWith(List.of("ultimate.ultimate"));
        form.setShareMasteryWith(shareMasteryWith);
        form.setShareMasteryMultiplier(1.0);

        FormConfig.FormData.OutlineShaderConfig outline = new FormConfig.FormData.OutlineShaderConfig();
        outline.setEnabled(outlineEnabled);
        outline.setPrimaryColor(outlinePrimary.isEmpty() ? LIGHT_BLUE : outlinePrimary);
        outline.setSecondaryColor(outlineSecondary.isEmpty() ? LIGHT_BLUE : outlineSecondary);
        outline.setOutlineThickness(outlineThickness);
        form.setOutlineShader(outline);
        form.setTriggerItemCosts(List.of());
        form.setDurationItemCosts(List.of());
        form.setMobEffects(List.of());
        return form;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
