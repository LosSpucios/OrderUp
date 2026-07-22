# Order Up! — Art Asset Checklist

The mod currently runs with placeholder model textures from vanilla Minecraft. These are the names I would use for the final custom art so the asset structure stays clean and predictable.

## Block textures

Create these under:

```text
src/main/resources/assets/orderup/textures/block/
```

Recommended files:

```text
restaurant_heart.png
restaurant_heart_core.png
restaurant_table.png
restaurant_chair.png
menu_board.png
open_sign.png
open_sign_open.png
open_sign_closed.png
```

Suggested direction:

- `restaurant_heart.png` — dark cozy stone/wood pedestal.
- `restaurant_heart_core.png` — warm glowing amber/red heart/core accent.
- `restaurant_table.png` — warm restaurant wood.
- `restaurant_chair.png` — matching wood/upholstery.
- `menu_board.png` — dark frame + parchment.
- `open_sign.png` — sign frame/base.
- `open_sign_open.png` — green/warm lit OPEN face.
- `open_sign_closed.png` — muted red CLOSED face.

16×16 works, but 32×32 would fit a cozy detailed style better.

## Block models

Runtime JSON models already exist here:

```text
assets/orderup/models/block/restaurant_heart.json
assets/orderup/models/block/restaurant_table.json
assets/orderup/models/block/restaurant_chair.json
assets/orderup/models/block/menu_board.json
assets/orderup/models/block/open_sign.json
```

For editable Blockbench source files, I would name them:

```text
design/blockbench/restaurant_heart.bbmodel
design/blockbench/restaurant_table.bbmodel
design/blockbench/restaurant_chair.bbmodel
design/blockbench/menu_board.bbmodel
design/blockbench/open_sign.bbmodel
```

The current JSON models are usable placeholders, so custom `.bbmodel` files are not required to launch the mod.

## Restaurant Heart GUI

Target folder:

```text
src/main/resources/assets/orderup/textures/gui/
```

Recommended files:

```text
restaurant_heart.png
menu.png
widgets.png
hud.png
thought_bubble.png
```

Suggested contents:

### `restaurant_heart.png`

A cozy wood/parchment panel containing the main Heart screen background.

### `menu.png`

An open-book/parchment background for the 4 Food + 2 Drinks screen.

### `widgets.png`

One atlas containing:

- plus button,
- confirm button,
- cancel/X button,
- question-mark placeholder head,
- empty ghost slot,
- selected ghost slot,
- XP bar frame/fill pieces.

### `hud.png`

One atlas containing:

- money background/icon,
- vertical restaurant XP frame,
- vertical XP fill.

### `thought_bubble.png`

A white comic-style thought bubble used above customers.

## Customer texture/model

No custom customer model is required right now: the renderer uses the vanilla villager-shaped model.

For a custom skin later, use:

```text
src/main/resources/assets/orderup/textures/entity/customer.png
```

For a fully custom model/animations later, I would keep Blockbench source as:

```text
design/blockbench/customer.bbmodel
```

A nice expansion would be several random customer skins:

```text
customer_0.png
customer_1.png
customer_2.png
customer_3.png
```

## Optional polish assets

```text
textures/particle/order_sparkle.png
textures/particle/angry_puff.png
sounds/customer_arrive.ogg
sounds/customer_happy.ogg
sounds/customer_angry.ogg
sounds/cash_register.ogg
```

These are not referenced by the current prototype yet.
