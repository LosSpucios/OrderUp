# Order Up! — Implementation Status

## Implemented

- Restaurant Heart registration, ownership, crew membership, name, money, XP and leveling.
- Restaurant radius progression: 8 / 12 / 16 / +4 blocks.
- Shift-right-click restaurant border visualization.
- Restaurant Heart GUI with owner-only rename and crew management.
- Restaurant-restricted placement for tables, chairs, menus and open signs.
- One chair per table and chair survival validation.
- One Open Sign per restaurant.
- Open/Closed restaurant state and customer behavior changes.
- Six-slot ghost Menu GUI: 4 Food + 2 Drinks.
- Server-side validation of menu slot contents.
- Recursive recipe-based price calculation.
- JSON-configurable ingredient and item-tag values.
- Custom Customer entity with restaurant/chair assignment.
- Path validation before customer admission.
- Walking, sitting, thinking, waiting, leaving and angry states.
- Correct/wrong food handling.
- Money and restaurant XP rewards.
- Angry-customer 50% recovery reward.
- Customer invulnerability before angry state.
- Stuck-customer cleanup.
- Menu item rendering on the placed block.
- Open/Closed text rendering on the sign.
- Restaurant money + XP HUD.
- Client/server custom payload networking.
- English language entries.
- Placeholder block/item models and recipes/loot tables.

## Intentionally left for a later pass

### JEI native drag-and-drop

The Menu already uses non-consuming ghost slots. Directly dragging a recipe ingredient from JEI into those slots needs an optional JEI-specific client integration and a pinned compatible JEI API dependency. It is not included in the core build, so Order Up does not require JEI to launch.

### Final artwork

Current GUIs are drawn with Minecraft primitives and current block models use vanilla textures. The exact target asset names are listed in `ART_ASSET_CHECKLIST.md`.

### Deeper restaurant progression

The XP/level system and radius expansion work, but level-specific unlocks, upgrades, furniture limits and balancing are not defined yet.

### Multiple independent Menu objects per restaurant

A restaurant currently tracks the most recently placed Menu as its active menu. This keeps customer logic deterministic for the first prototype.

### Offline profile lookup edge cases

Adding crew works immediately for online players and uses the server profile cache for known offline players. A player never seen by that server may need to join once before their name is available from the local profile cache.

## Testing priorities

1. Launch a dedicated development client with `gradlew runClient`.
2. Place Restaurant Heart and verify owner initialization.
3. Open Heart GUI and test rename/add/remove member packets.
4. Place one table + one chair and confirm a second chair is rejected.
5. Fill all six Menu slots.
6. Confirm customers do not appear while the restaurant is closed.
7. Re-open and watch pathfinding to the chair.
8. Test correct food, correct food+drink and wrong item outcomes.
9. Reload the world and confirm restaurant/menu data persists.
10. Test with two players to verify crew permissions and HUD visibility.
