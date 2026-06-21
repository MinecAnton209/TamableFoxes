# Common + Adapters Refactoring Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate 26 duplicated `EntityTamableFox.java` files (~600 lines each) by extracting shared logic into a common abstract class and making each version adapter a thin NMS bridge.

**Architecture:** One `AbstractEntityTamableFox` in common holds all shared logic (~300 lines). Each version's `EntityTamableFox extends Fox` shrinks to ~150 lines of pure NMS-specific code. Pathfinding goals stay in version modules (they depend on NMS `Goal` class) but reference the common interface `ITamableFox` instead of the concrete class.

**Tech Stack:** Java 21/25, Maven multi-module, Spigot/Paper NMS, reflection for cross-version compatibility.

---

## Architecture Overview

```
Utility/ (existing - renamed to Common/)
  src/main/java/com/minecanton209/tamablefoxes/
    TamableFoxes.java                    # Plugin main (moved from Plugin/)
    commands/                            # Commands (moved from Plugin/)
    listeners/                           # Event listeners (moved from Plugin/)
    util/                                # Existing utils
      NMSInterface.java                  # Existing adapter interface
      ITamableFox.java                   # NEW: Interface for fox entity
      AbstractEntityTamableFox.java      # NEW: Shared logic base
      Config.java, Utils.java, etc.      # Existing
    versions/
      FoxType.java                       # Moved from each module

1_21_R8/ (example adapter)
  src/main/java/com/minecanton209/tamablefoxes/
    versions/version_1_21_11_R1/
      EntityTamableFox.java              # SLIM: ~150 lines, extends Fox, delegates to AbstractEntityTamableFox
      NMSInterface_1_21_11_R1.java       # Existing, unchanged
      NMSUtil.java                       # Existing, unchanged
      pathfinding/                       # Existing, unchanged (references ITamableFox)
```

---

## Step 1: Create ITamableFox interface in common

**Files:**
- Create: `Utility/src/main/java/com/minecanton209/tamablefoxes/util/ITamableFox.java`

This interface declares all methods that pathfinding goals and other code need from the fox entity. It uses only Bukkit API types (no NMS).

- [ ] **Step 1.1: Create ITamableFox.java**

```java
package com.minecanton209.tamablefoxes.util;

import java.util.UUID;
import javax.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Interface for tamed fox entities. Used by pathfinding goals and utility code
 * to reference fox entities without depending on NMS.
 */
public interface ITamableFox {

    // Taming
    boolean isTamed();
    void setTamed(boolean tamed);

    // Owner
    @Nullable UUID getOwnerUUID();
    void setOwnerUUID(@Nullable UUID uuid);
    @Nullable LivingEntity getOwner();

    // Sit/Sleep state
    boolean isOrderedToSit();
    void setOrderedToSit(boolean sit);
    boolean isOrderedToSleep();
    void setOrderedToSleep(boolean sleep);

    // Combat
    boolean isOwnedBy(LivingEntity entity);
    boolean wantsToAttack(LivingEntity target, LivingEntity owner);
    boolean isDefending();
    void setDefending(boolean defending);

    // Navigation (Bukkit-level)
    Location getLocation();
    void teleport(Location loc);
}
```

- [ ] **Step 1.2: Verify it compiles**

Run: `mvn compile -pl Utility -q`
Expected: BUILD SUCCESS

- [ ] **Step 1.3: Commit**

```bash
git add Utility/src/main/java/com/minecanton209/tamablefoxes/util/ITamableFox.java
git commit -m "Add ITamableFox interface for common fox entity contract"
```

---

## Step 2: Create AbstractEntityTamableFox in common

**Files:**
- Create: `Utility/src/main/java/com/minecanton209/tamablefoxes/util/AbstractEntityTamableFox.java`

This abstract class holds ALL shared logic from EntityTamableFox. It does NOT extend any NMS class. Version adapters extend Fox AND delegate to this class's methods.

- [ ] **Step 2.1: Create AbstractEntityTamableFox.java**

The class contains these shared methods extracted from EntityTamableFox (all versions, normalized names):

```java
package com.minecanton209.tamablefoxes.util;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.minecanton209.tamablefoxes.util.io.Config;
import com.minecanton209.tamablefoxes.util.io.LanguageConfig;
import com.minecanton209.tamablefoxes.util.io.sqlite.SQLiteHelper;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 * Shared logic for tamed fox entities. Version-specific code lives in
 * each adapter's EntityTamableFox which extends this.
 */
public abstract class AbstractEntityTamableFox implements ITamableFox {

    protected boolean tamed;
    @Nullable protected UUID ownerUUID;

    // === ITamableFox implementation ===

    @Override
    public boolean isTamed() {
        return this.tamed && (ownerUUID != null && !ownerUUID.equals(new UUID(0, 0)));
    }

    @Override
    public void setTamed(boolean tamed) {
        this.tamed = tamed;
        reassessTameGoals();
        if (tamed) {
            onTamed();
        } else {
            onUntamed();
        }
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() { return this.ownerUUID; }

    @Override
    public void setOwnerUUID(@Nullable UUID uuid) { this.ownerUUID = uuid; }

    @Override
    public boolean isOwnedBy(LivingEntity entity) {
        return entity == this.getOwner();
    }

    // === Abstract methods (NMS-specific, implemented by adapter) ===

    /** Called when fox is tamed - set attributes */
    protected abstract void onTamed();
    /** Called when fox is untamed - set attributes */
    protected abstract void onUntamed();
    /** Remove untamed-specific goals */
    protected abstract void reassessTameGoals();

    // === Shared rename logic ===

    public void rename(Player player) {
        try {
            new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    String text = stateSnapshot.getText();
                    if (slot == AnvilGUI.Slot.OUTPUT && !text.isEmpty()) {
                        String foxName = LanguageConfig.getFoxNameFormat(text, player.getDisplayName());
                        org.bukkit.entity.Entity tamableFox = this.getBukkitEntity();
                        tamableFox.setCustomName(foxName);
                        tamableFox.setCustomNameVisible(true);
                        if (!LanguageConfig.getTamingChosenPerfect(text).equalsIgnoreCase("disabled")) {
                            stateSnapshot.getPlayer().sendMessage(
                                Config.getPrefix() + ChatColor.GREEN + LanguageConfig.getTamingChosenPerfect(text));
                        }
                    } else if (!LanguageConfig.getTamingChosenPerfect(text).equalsIgnoreCase("disabled")) {
                        stateSnapshot.getPlayer().sendMessage(
                            Config.getPrefix() + ChatColor.GRAY + "The fox was not named");
                    }
                    return java.util.Arrays.asList(AnvilGUI.ResponseAction.close());
                })
                .text("Fox")
                .title(LanguageConfig.getRenameGuiText())
                .plugin(Utils.tamableFoxesPlugin)
                .open(player);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // === Abstract methods for Bukkit entity access ===

    protected abstract org.bukkit.entity.Entity getBukkitEntity();
}
```

- [ ] **Step 2.2: Verify it compiles**

Run: `mvn compile -pl Utility -q`
Expected: BUILD SUCCESS

- [ ] **Step 2.3: Commit**

```bash
git add Utility/src/main/java/com/minecanton209/tamablefoxes/util/AbstractEntityTamableFox.java
git commit -m "Add AbstractEntityTamableFox with shared entity logic"
```

---

## Step 3: Make EntityTamableFox in each version extend AbstractEntityTamableFox

This is the main task. For each of the 26 version modules, the EntityTamableFox is modified to:
1. Extend `Fox` (NMS) AND extend `AbstractEntityTamableFox` (not possible - single inheritance)

**Problem:** Java single inheritance. EntityTamableFox must extend Fox (NMS) for the entity system, but AbstractEntityTamableFox is a separate class.

**Solution:** Use composition instead of inheritance for the abstract class. EntityTamableFox extends Fox and holds a reference to a helper that delegates to AbstractEntityTamableFox logic.

**Revised approach:** Make AbstractEntityTamableFox methods static/protected, and EntityTamableFox calls them directly.

```java
// EntityTamableFox.java in each version (SLIM VERSION)
package com.minecanton209.tamablefoxes.versions.version_1_21_11_R1;

import com.minecanton209.tamablefoxes.util.AbstractEntityTamableFox;

public class EntityTamableFox extends Fox {
    private final AbstractEntityTamableFox foxLogic = new AbstractEntityTamableFox() {
        // Implement abstract methods by delegating to Fox NMS methods
    };
    // ... delegates
}
```

This is too complex. Let me simplify.

**SIMPLER APPROACH:** Make AbstractEntityTamableFox an interface with default methods (Java 8+).

Wait, Java interfaces can have state since Java 8 with default methods, but they can't have instance fields. We need fields.

**SIMPLEST PRACTICAL APPROACH:**

Keep EntityTamableFox in each version module. Make it extend Fox. Extract shared logic into static utility methods in `TamableFoxUtil` (common module). Each version's EntityTamableFox calls these utility methods.

This is the approach most Bukkit plugins use. It's not perfect OOP, but it works and eliminates duplication.

### Step 3 (revised): Extract shared logic to TamableFoxUtil in common

**Files:**
- Create: `Utility/src/main/java/com/minecanton209/tamablefoxes/util/TamableFoxUtil.java`

```java
package com.minecanton209.tamablefoxes.util;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.bukkit.entity.LivingEntity;
import com.minecanton209.tamablefoxes.util.io.Config;
import com.minecanton209.tamablefoxes.util.io.sqlite.SQLiteHelper;

/**
 * Static utility methods shared by all version-specific EntityTamableFox classes.
 */
public final class TamableFoxUtil {

    private TamableFoxUtil() {}

    /** Check if the given tamed state + UUID represents a valid tamed fox. */
    public static boolean isTamed(boolean tamed, @Nullable UUID ownerUUID) {
        return tamed && (ownerUUID != null && !ownerUUID.equals(new UUID(0, 0)));
    }

    /** Validate owner UUID is non-null and non-zero. */
    public static boolean isValidOwner(@Nullable UUID uuid) {
        return uuid != null && !uuid.equals(new UUID(0, 0));
    }

    /** Calculate max fox tames from config + SQLite. Returns true if limit exceeded. */
    public static boolean isTameLimitExceeded(java.util.UUID playerUuid) {
        if (Config.getMaxPlayerFoxTames() <= 0) return false;
        SQLiteHelper db = SQLiteHelper.getInstance(Utils.tamableFoxesPlugin);
        int current = db.getPlayerFoxAmount(playerUuid);
        return current >= Config.getMaxPlayerFoxTames();
    }

    /** Increment the player's fox count in SQLite. */
    public static void incrementTameCount(java.util.UUID playerUuid) {
        if (Config.getMaxPlayerFoxTames() > 0) {
            SQLiteHelper db = SQLiteHelper.getInstance(Utils.tamableFoxesPlugin);
            db.addPlayerFoxAmount(playerUuid, 1);
        }
    }

    /** Decrement the player's fox count in SQLite. */
    public static void decrementTameCount(java.util.UUID playerUuid) {
        if (Config.getMaxPlayerFoxTames() > 0) {
            SQLiteHelper db = SQLiteHelper.getInstance(Utils.tamableFoxesPlugin);
            db.removePlayerFoxAmount(playerUuid, 1);
        }
    }

    /** Parse UUID from NBT string with fallback. */
    @Nullable
    public static UUID parseOwnerUUID(String uuidString) {
        try {
            UUID uuid = UUID.fromString(uuidString);
            return isValidOwner(uuid) ? uuid : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
```

- [ ] **Step 3.1: Create TamableFoxUtil.java**

- [ ] **Step 3.2: Verify it compiles**

Run: `mvn compile -pl Utility -q`

- [ ] **Step 3.3: Commit**

```bash
git add Utility/src/main/java/com/minecanton209/tamablefoxes/util/TamableFoxUtil.java
git commit -m "Add TamableFoxUtil with shared entity logic methods"
```

---

## Step 4: Refactor each EntityTamableFox to use TamableFoxUtil

For each of the 26 modules, replace duplicated logic with TamableFoxUtil calls.

**Files to modify (all EntityTamableFox.java):**
- `1_14_R1/src/.../EntityTamableFox.java`
- `1_15_R1/src/.../EntityTamableFox.java`
- `1_16_R1/src/.../EntityTamableFox.java`
- `1_16_R2/src/.../EntityTamableFox.java`
- `1_16_R3/src/.../EntityTamableFox.java`
- `1_17_R1/src/.../EntityTamableFox.java`
- `1_17_1_R1/src/.../EntityTamableFox.java`
- `1_18_R1/src/.../EntityTamableFox.java`
- `1_18_R2/src/.../EntityTamableFox.java`
- `1_18_1_R1/src/.../EntityTamableFox.java`
- `1_19_R1/src/.../EntityTamableFox.java`
- `1_19_1_R1/src/.../EntityTamableFox.java`
- `1_19_2_R1/src/.../EntityTamableFox.java`
- `1_19_3_R1/src/.../EntityTamableFox.java`
- `1_20_R1/src/.../EntityTamableFox.java`
- `1_20_R3/src/.../EntityTamableFox.java`
- `1_21_R1/src/.../EntityTamableFox.java`
- `1_21_R3/src/.../EntityTamableFox.java`
- `1_21_R4/src/.../EntityTamableFox.java`
- `1_21_R5/src/.../EntityTamableFox.java`
- `1_21_R6/src/.../EntityTamableFox.java`
- `1_21_R7/src/.../EntityTamableFox.java`
- `1_21_R8/src/.../EntityTamableFox.java`
- `26_R1/src/.../EntityTamableFox.java`

- [ ] **Step 4.1: Refactor 26_R1 EntityTamableFox** (the newest, reference implementation)

Replace these methods with TamableFoxUtil calls:

```java
// BEFORE (in EntityTamableFox.java):
public boolean isTamed() {
    UUID ownerUuid = getOwnerUUID();
    return this.tamed && (ownerUuid != null && !ownerUuid.equals(new UUID(0, 0)));
}

// AFTER:
public boolean isTamed() {
    return TamableFoxUtil.isTamed(this.tamed, getOwnerUUID());
}
```

```java
// BEFORE:
// In readAdditionalSaveData:
String uuidString = input.getStringOr("OwnerUUID", "00000000-0000-0000-0000-000000000000");
try {
    ownerUuid = UUID.fromString(uuidString);
} catch (IllegalArgumentException e) {
    ownerUuid = null;
}
if (ownerUuid != null && !ownerUuid.equals(new UUID(0, 0))) {
    this.setOwnerUUID(ownerUuid);
    this.setTamed(true);
}

// AFTER:
UUID ownerUuid = TamableFoxUtil.parseOwnerUUID(
    input.getStringOr("OwnerUUID", "00000000-0000-0000-0000-000000000000"));
if (ownerUuid != null) {
    this.setOwnerUUID(ownerUuid);
    this.setTamed(true);
}
```

```java
// BEFORE:
// In tame():
SQLiteHelper sqliteHelper = SQLiteHelper.getInstance(Utils.tamableFoxesPlugin);
sqliteHelper.addPlayerFoxAmount(owner.getUUID(), 1);

// AFTER:
TamableFoxUtil.incrementTameCount(owner.getUUID());
```

```java
// BEFORE:
// In die():
if (Config.getMaxPlayerFoxTames() > 0) {
    SQLiteHelper sqliteHelper = SQLiteHelper.getInstance(Utils.tamableFoxesPlugin);
    sqliteHelper.removePlayerFoxAmount(owner.getUUID(), 1);
}

// AFTER:
TamableFoxUtil.decrementTameCount(owner.getUUID());
```

```java
// BEFORE:
// In mobInteract() taming check:
SQLiteHelper sqliteHelper = SQLiteHelper.getInstance(Utils.tamableFoxesPlugin);
int foxTames = sqliteHelper.getPlayerFoxAmount(entityhuman.getUUID());
if (Config.getMaxPlayerFoxTames() > 0 && foxTames >= Config.getMaxPlayerFoxTames()) {
    // limit reached
}

// AFTER:
if (TamableFoxUtil.isTameLimitExceeded(entityhuman.getUUID())) {
    // limit reached
}
```

Add import: `import com.minecanton209.tamablefoxes.util.TamableFoxUtil;`

- [ ] **Step 4.2: Verify 26_R1 compiles**

Run: `mvn compile -pl 26_R1,Utility -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4.3: Refactor 1_21_R8 EntityTamableFox**

Same changes as Step 4.1 but adapted for 1_21_R8 API (ValueInput, EntityReference, etc.).

- [ ] **Step 4.4: Verify 1_21_R8 compiles**

Run: `mvn compile -pl 1_21_R8,Utility -am -q`

- [ ] **Step 4.5: Refactor remaining 1_21_R* modules** (R1-R7)

Same pattern, adapted for each version's API.

- [ ] **Step 4.6: Verify 1_21_R* compile**

Run: `mvn compile -pl 1_21_R1,1_21_R3,1_21_R4,1_21_R5,1_21_R6,1_21_R7,Utility -am -q`

- [ ] **Step 4.7: Refactor 1_17_R1 through 1_20_R3 modules** (8 modules)

Same pattern for middle-era modules (1_17_R1, 1_17_1_R1, 1_18_R1, 1_18_R2, 1_18_1_R1, 1_19_R1, 1_19_1_R1, 1_19_2_R1, 1_19_3_R1, 1_20_R1, 1_20_R3).

Note: These use `CompoundTag` (not ValueInput/ValueOutput). The `parseOwnerUUID` and `incrementTameCount`/`decrementTameCount` calls are the same.

- [ ] **Step 4.8: Verify 1_17-1_20 compile**

Run: `mvn compile -pl 1_17_R1,1_17_1_R1,1_18_R1,1_18_R2,1_18_1_R1,1_19_R1,1_19_1_R1,1_19_2_R1,1_19_3_R1,1_20_R1,1_20_R3,Utility -am -q`

- [ ] **Step 4.9: Refactor 1_14_R1 through 1_16_R3 modules** (5 modules)

These use obfuscated names. The `isTamed()` uses DataWatcher bitmask, so TamableFoxUtil.isTamed() won't work directly for 1_14_R1. For these, only apply `incrementTameCount`/`decrementTameCount`/`isTameLimitExceeded` changes.

- [ ] **Step 4.10: Verify 1_14-1_16 compile**

Run: `mvn compile -pl 1_14_R1,1_15_R1,1_16_R1,1_16_R2,1_16_R3,Utility -am -q`

- [ ] **Step 4.11: Full build verification**

Run: `mvn install -pl Utility -q && mvn compile -pl 26_R1,1_21_R8,1_21_R7,1_21_R6,1_21_R5,1_21_R4,1_21_R3,1_21_R1,1_20_R1,1_20_R3,1_19_R1,1_19_1_R1,1_19_2_R1,1_19_3_R1,1_18_R1,1_18_R2,1_18_1_R1,1_17_R1,1_17_1_R1,1_16_R1,1_16_R2,1_16_R3,1_15_R1,1_14_R1 -am -q`

- [ ] **Step 4.12: Commit all refactored modules**

```bash
git add -A
git commit -m "Refactor all 26 EntityTamableFox to use TamableFoxUtil shared methods"
```

---

## Step 5: Rename pathfinding goals to use ITamableFox where possible

The pathfinding goals currently reference the concrete `EntityTamableFox` class from their version module. If we make them reference `ITamableFox` instead, we can potentially move them to common in the future.

**Files to modify:** All `FoxPathfinderGoal*` files in all 26 modules.

- [ ] **Step 5.1: Update FoxPathfinderGoalSitWhenOrdered in 26_R1**

Change constructor parameter from `EntityTamableFox` to `ITamableFox`. Change all `this.mob.` calls to use interface methods.

Actually, since pathfinding goals use NMS methods (`getNavigation()`, `setSitting()` on the Fox entity), they can't fully use the interface. This step is optional/future work.

**Skip this step for now** - pathfinding goals must stay version-specific because they call NMS methods on the entity.

---

## Step 6: Clean up and final verification

- [ ] **Step 6.1: Full build**

Run: `mvn install -pl Utility -q && mvn compile -am -q`
Expected: All modules compile

- [ ] **Step 6.2: Package the plugin**

Run: `mvn package -pl Plugin -am -q`

- [ ] **Step 6.3: Final commit**

```bash
git add -A
git commit -m "Complete Common+Adapters refactoring: shared TamableFoxUtil across all 26 versions"
```

---

## Summary of Changes

| What | Before | After |
|------|--------|-------|
| EntityTamableFox.java | 26 × ~600 lines | 26 × ~580 lines (shared logic extracted to util) |
| TamableFoxUtil.java | N/A | 1 × ~80 lines |
| ITamableFox.java | N/A | 1 × ~40 lines |
| Total duplicated code | ~15,600 lines | ~15,080 lines + 120 lines shared |
| Duplication reduction | 0% | ~5% (first pass) |

**Note:** This is a conservative first pass. The real win comes from the architecture enabling future work:
- All 26 modules now call `TamableFoxUtil` for shared operations
- Adding new shared logic requires editing ONE file instead of 26
- Future: Move more logic to common as NMS API differences are abstracted away
