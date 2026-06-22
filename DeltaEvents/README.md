# DeltaEvents (Minecraft Event Manager Plugin)

This plugin is a flexible, modular framework for managing and running competitive multiplayer events (mini-games) on a Minecraft server. It is built for **Paper 1.21.1 (Paper API 26.1.2)** and compiled using **Java 25**.

---

## 🚀 Overview for AI Developer Agents (Google Jules)

If you are an AI developer agent (like Google Jules) connected to this repository, read this document to understand the codebase structure, current events, commands, and coding guidelines before editing or writing new features.

DeltaEvents uses a modular manager-based architecture. Each event (e.g., Sumo, KOTH, MrBeast, FiveM) is fully isolated in its own `Manager` class, has its own dedicated configuration file, and communicates with the main plugin class through event listeners and API helper methods.

---

## 🎮 Current Events & Mechanics

### 1. Sumo
- **Gameplay**: Players are registered inside a participant region and given a protected knockback stick ("Sumo Stick") pinned to slot 0. The floor blocks of the arena are temporarily removed for 2 seconds at the start of the event. Players are eliminated when they step on configured elimination blocks (e.g., stone bricks).
- **Editor**: Admins use the Sumo Editor wand to select:
  - `spawn`: Single-click location where players spawn when eliminated or when the event is stopped.
  - `participant`: Left/right-click region where players must stand to join.
  - `floor`: Left/right-click region of the floor blocks to be temporarily removed.

### 2. KOTH (King of the Hill)
- **Gameplay**: Players compete to stay inside the KOTH arena region. Staying in the region increments their capture time. The player with the highest capture time when the event duration ends is declared the winner. Supports PlaceholderAPI for active holding player names and time remaining.
- **Editor**: Admins use the KOTH Editor wand to select:
  - `spawn`: Single-click location.
  - `arena`: Left/right-click region defining the capture hill.

### 3. MrBeast (Color Event)
- **Gameplay**: Players stand on colored platforms. In each round, a platform color is chosen at random and eliminated. When the choosing timer expires, choices are locked, freezing players in place. The chosen platform is removed, and players standing on it (or not on any platform) are eliminated.
- **Editor**: Admins use the MrBeast Editor wand to set:
  - `spawn`: Single-click location.
  - `<platform_name>` (e.g. `red`, `green`, `blue`, `yellow`): Left/right-click region to define platform coordinates.

### 4. FiveM (Color Event)
- **Gameplay**: Similar to MrBeast but rounds accelerate. Players are given a target color to stand on. When the timer expires, all non-matching color blocks disappear. Players who fall below the minimum platform Y coordinate are eliminated. The choosing time decreases with each round.
- **Editor**: Admins use the FiveM Editor wand to set:
  - `spawn`: Single-click location.
  - `arena`: Left/right-click region covering all colored platforms.

---

## 🛠️ Command Reference

All administrative commands require the `DeltaEvents.admin` permission. Event-specific administrative actions check permissions like `DeltaEvents.sumo.admin` or `DeltaEvents.koth.admin`.

### Main Command: `/deltaevents` (Aliases: `/devents`, `/de`)
* `/deltaevents help` - Displays help and plugin information.
* `/deltaevents list` - Lists all currently registered event types.
* `/deltaevents reload` - Reloads all configurations (`config.yml`, `lang.yml`) and stops/reloads all active events.

### Sumo Command: `/sumo`
* `/sumo rules` - Broadcasts Sumo rules to the event world.
* `/sumo start` - Starts the countdown and begins the Sumo event.
* `/sumo round <number>` - Displays a title/subtitle warning players that round `<number>` is starting.
* `/sumo stop` - Forcefully stops the Sumo event.
* `/sumo getitems` - Gives the administrator the configured Sumo Stick in any world/slot for testing.
* `/sumo editor <spawn|participant|floor>` - Gives the administrator the region editor item for the selected parameter.
* `/sumo reload` - Stops, reloads, and restarts the Sumo module config.

### KOTH Command: `/koth`
* `/koth start` - Starts the KOTH event.
* `/koth stop` - Forcefully stops the KOTH event.
* `/koth editor <arena|spawn>` - Gives the KOTH editor item.
* `/koth reload` - Stops and reloads the KOTH configuration.

### Color Event Command: `/color` (Manages MrBeast & FiveM)
* `/color <mrbeast|fivem> start` - Starts the chosen color event.
* `/color <mrbeast|fivem> stop` - Stops the chosen color event.
* `/color <mrbeast|fivem> reload` - Reloads the chosen color event configuration.
* `/color mrbeast editor <spawn|platform_name>` - Gives the MrBeast editor wand.
* `/color fivem editor <spawn|arena>` - Gives the FiveM editor wand.

---

## 📜 Development Guidelines & Code Standards

When writing, extending, or maintaining code for this repository, you **must** follow these architectural rules:

### 1. Concurrency Safety & Task Chain Verification
- **The Issue**: Minecraft plugins often use scheduled `BukkitRunnable` chains. If an event is stopped and immediately restarted, old running schedulers from the previous session can continue executing, leading to double-ticking, accidental player elimination, or thread conflicts.
- **The Solution**: Every event module must use an instance-based tracking system.
  - Define `private UUID currentGameId = null;` as a class field.
  - When starting the countdown or event, assign `currentGameId = UUID.randomUUID();`.
  - When stopping the event, set `currentGameId = null;` and cancel active tasks.
  - In **every** scheduled runnable, capture the session's ID: `final UUID gameId = currentGameId;`.
  - At the very beginning of the `run()` block inside the task, check:
    ```java
    if (!active || currentGameId == null || !currentGameId.equals(gameId)) {
        cancel(); // or return;
        return;
    }
    ```

### 2. Event State Cleanups & Memory Management
- When an event is stopped (`stop()` method), you must:
  - Cancel all tasks (`countdownTask`, `gameTask`, etc.) and set them to null.
  - Restore all modified platform blocks immediately.
  - Clear state cache maps like `originalStates` or player location maps to prevent block references from leaking in memory.
  - Clear participants collection (`activePlayers.clear()`).

### 3. Safe Collection Modification during Rounds
- When eliminating players in a batch loop (e.g., at the end of a round), using direct modifications like `activePlayers.remove()` can throw a `ConcurrentModificationException`.
- **Standard Pattern**:
  - Use `Iterator<UUID> it = activePlayers.iterator();` to traverse the list.
  - If a player fails the check, call `it.remove()` to safely remove them from the collection.
  - Call a custom method `eliminatePlayerNoRemove(Player)` to handle the game effects (teleportation, broadcast messages) without attempting to modify the collection.
  - Check if a winner exists (`activePlayers.size() <= 1`) *after* the iterator loop completes.

### 4. Code Consistency for Region Editors
- Region editor items must follow the Sumo editor wand pattern:
  - Check permissions on use (`PlayerInteractEvent`).
  - Cancel block interactions (`event.setCancelled(true)`) to prevent building/breaking.
  - Right-click registers point 2, left-click registers point 1.
  - Save configurations immediately back to the event's configuration file (e.g. `MrBeast.yml`, `FiveM.yml`) and invoke `reload()` to apply.
  - Provide fallback display names, materials, and Bulgarian lore (e.g. `"&7Ляв клик: Точка 1"`, `"&7Десен клик: Точка 2"`) dynamically if not defined in the configuration.

### 5. World-Level PvP Lifecycle
- Event worlds are PvP-disabled by default.
- When an event starts, enable PvP using: `world.setPVP(config.getBoolean("pvp", true));`.
- When an event stops, disable PvP using: `world.setPVP(false);`.

### 6. Pinned & Protected Event Items
- Event items (like the Sumo stick) must be put on slot 0 (`p.getInventory().setItem(0, item);`) and the slot selected (`p.getInventory().setHeldItemSlot(0);`).
- Add appropriate listeners to prevent these items from being dropped, dragged in inventories, crafted, or swapped to the offhand.

---

## 📦 How to Build & Package

The project uses Maven. Compile and package the shaded JAR using:

```bash
mvn clean package
```

The compiled shaded output will be located in the `target/` directory:
- `target/DeltaEvents-1.0.0.jar`
