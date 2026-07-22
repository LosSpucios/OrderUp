# Order Up! — NeoForge 1.21.1

A restaurant-management mod prototype for Minecraft 1.21.1 using NeoForge.

This workspace is based on the original OrderUp MDK setup and uses:

- Minecraft `1.21.1`
- NeoForge `21.1.241`
- Java `21`
- ModDevGradle `2.0.142`
- Parchment `2024.11.17`

## Run it

1. Open this folder as a Gradle project in IntelliJ IDEA.
2. Make sure the project JDK is Java 21.
3. Run:

```bash
./gradlew runClient
```

On Windows:

```bat
gradlew.bat runClient
```

To build the mod jar:

```bash
./gradlew build
```

The built jar will appear in `build/libs/`.

## Gameplay implemented in this prototype

### Restaurant Heart

- Place a `Restaurant Heart` to create a restaurant.
- The player who places it becomes the owner and first crew member.
- Restaurant area radius:
  - Level 1: 8 blocks in every horizontal direction
  - Level 2: 12 blocks
  - Level 3: 16 blocks
  - Every next level adds 4 blocks
- Right-click: opens the Restaurant Heart GUI.
- Shift + right-click: toggles a white restaurant boundary preview.
- The GUI includes:
  - editable restaurant name for the owner,
  - restaurant level and XP progress,
  - restaurant money,
  - owner/crew player heads and names,
  - adding crew by player name,
  - removing crew members by the owner.

### Restaurant furniture restrictions

`Restaurant Table`, `Restaurant Chair`, `Menu`, and `Open Sign` can only be placed inside a restaurant.

A chair:

- can only be placed directly next to a Restaurant Table,
- cannot exist without its attached table,
- is limited to one chair per table,
- shows an English action-bar error when placement is invalid.

### Menu

- Right-click a placed Menu to open its GUI.
- It has 4 Food slots and 2 Drink slots.
- Menu slots are ghost slots: selecting an inventory item does not consume it.
- Right-click a menu slot to clear it.
- Food slots accept edible items.
- Drink slots accept items using the drink animation or items in the `orderup:drinks` item tag.
- The placed Menu renders its configured items in-world.
- Customers only start arriving when all 6 menu slots are filled.

Direct JEI drag-and-drop is intentionally not hard-linked yet, so JEI is not a required dependency. The core ghost-slot system is already separated so a JEI integration layer can be added later.

### Automatic menu pricing

Menu prices are calculated recursively from crafting/cooking ingredients.

Currently checked recipe types:

- crafting,
- smelting,
- smoking,
- campfire cooking.

Explicit ingredient values override calculated values. The file is generated on first server start at:

```text
config/orderup/ingredient_prices.json
```

Example:

```json
{
  "unknown_ingredient_price": 1,
  "values": {
    "minecraft:beef": 5,
    "minecraft:wheat": 1,
    "#orderup:vegetables": 2
  }
}
```

Both direct item IDs and `#item_tag` IDs are supported.

### Customers

Customers are custom villager-like entities controlled by the restaurant system.

They:

- spawn only while the restaurant is open,
- require a completely filled Menu,
- require an available chair,
- test whether they can path to the chair,
- walk to their assigned chair,
- sit using an invisible marker seat,
- display an animated thinking state,
- choose one random food and sometimes one drink,
- only expose the thought/order display to the relevant restaurant crew while they are in that restaurant,
- accept food only from restaurant crew,
- pay the calculated order value after a correct complete order,
- award restaurant XP after a successful order,
- show happy villager particles after successful service.

If the player gives a wrong item:

- the customer becomes angry,
- leaves without paying,
- becomes damageable,
- pays 50% of the original order value if killed by restaurant crew before escaping.

Normal customers reject damage. Customers also leave/disappear if their chair becomes invalid, no route can be maintained, or they remain stuck too long.

### Open Sign

- Only one Open Sign can be linked to a restaurant.
- Right-click toggles `OPEN` / `CLOSED`.
- When closed:
  - no new customers spawn,
  - customers still walking toward seats turn around and leave,
  - already seated customers remain until served.
- Removing the sign returns the restaurant to open state.

### Restaurant HUD

Crew members standing inside their restaurant see:

- restaurant money near the hotbar,
- a vertical restaurant XP bar on the right side of the screen.

The HUD disappears shortly after leaving the restaurant area.

## Important prototype notes

This is a large first-pass vertical slice, not a finished release. The architecture is split into blocks, block entities, restaurant management, networking, client screens/renderers, customers, and pricing so the next mechanics can be added without rewriting the whole mod.

The current block models intentionally use vanilla Minecraft textures as placeholders, so the workspace can run before custom art exists. See `ART_ASSET_CHECKLIST.md` for the exact texture/model names prepared for the final art pass.

See `IMPLEMENTATION_STATUS.md` for what is complete and what is intentionally left for the next pass.
