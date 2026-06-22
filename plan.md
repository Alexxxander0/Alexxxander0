1. **Analyze Requirements:**
   - Synchronize subcommands for Color MrBeast, Color FiveM, and KOTH to be the same as Sumo.
   - For `MrBeast` and `FiveM` commands, the subcommands should be: `start`, `stop`, `editor` (similar logic as sumo editor with `giveitems`), and `reload`. Add dynamic usage message fetching based on `plugin.msg` instead of hardcoded strings in `sendUsage`.
   - Add `getitems` to ColorCommand for items (e.g. stick equivalents if there are any, or just align usage structure with Sumo if not).
   - Ensure KOTH logic bypasses world checks only where needed. Currently `KothCommand.java` doesn't enforce world checks in `onCommand`. "Koth трябва да остане изолиран откъм проверка на world" essentially means DO NOT add a strict `world.getName().equalsIgnoreCase(worldName)` check for KOTH in the main command logic like it exists in Sumo rules (which is good since KOTH doesn't have it). Keep KOTH as it is regarding world checking.
   - Add localized message keys to `lang.yml` for MrBeast and FiveM. Add corresponding `usage_header`, `usage_title`, `usage_start`, `usage_stop`, `usage_editor`, `usage_reload` under their respective sections. Add `editor_give_success` translations for MrBeast and FiveM.
   - For KOTH: Ensure `usage` translates exactly like Sumo and `editor` works cleanly.

2. **Update `ColorCommand.java`:**
   - Change `sendUsage(CommandSender sender)` to print using `plugin.msg("mrbeast.usage_...")` when called for MrBeast and similar for FiveM. But since we need to show both, maybe unified `color.usage_...` or just print based on eventType if provided, otherwise general. Actually, the original sends combined usage. We will combine them into `lang.yml` under `color:` or keep as is but localized. Let's make it localized.
   - For `editor`, it currently gives items properly. Ensure messages are using `plugin.msg("mrbeast.editor_give_success")` instead of hardcoded `&cСамо за играчи!`. Replace `&cСамо за играчи!` with `plugin.msg("mrbeast.only_players")`.

3. **Update `KothCommand.java`:**
   - The command currently is mostly fine but we need to ensure all messages match Sumo's style. Replace any hardcoded usage text with proper localized ones. (It already uses `plugin.msg("koth.usage_...")`).
   - If Koth requires `getitems` (the prompt says "и самия айтъм на отделна събкоманда... която да е giveitems"), check if Koth has a specific item. If not, add an item or skip if it doesn't make sense. Actually, Sumo has a "stick". KOTH doesn't have a specific event item to give (unless it's just the editor items). The prompt says: "трябва ми и самия айтъм на отделна събкоманда (ако има такъв), която да е giveitems". We will add `getitems`/`giveitems` to Color and KOTH if they have custom items in their configs. We can check `Koth.yml`, `FiveM.yml`, `MrBeast.yml` to see if they have non-editor items. None of them have a `stick` or similar item defined. I will add a dummy command `giveitems` that might just say "няма предмети" or I will check if there's any logic I missed.

4. **Update `lang.yml`:**
   - Add `usage_header`, `usage_title`, etc. for `color`, `mrbeast` and `fivem`.
   - Add `editor_give_success` and `only_players` for them.

5. **Pre-commit Checks:**
   - Run Maven build to ensure code compiles perfectly.

6. **Submit:**
   - Commit and push.
