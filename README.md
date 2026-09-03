# Chiseled Infusion

> **Diegetic, deterministic enchanting for Minecraft Fabric 26.2!**  
> Modern spiritual port and overhaul of *Re_Enchanting v2*.

---

## ✨ Features

### 📖 Diegetic In-World Enchanting
- **No Clunky GUIs**: Place any enchantable item (weapons, armor, tools, or plain/enchanted books) directly onto the **Chiseled Infusion Table** with a right-click. It will float gracefully above the table.
- **Easy Retrieval**: Right-click with an empty hand (or any non-catalyst item) to retrieve your item at any time. Sneak-click with items in hand allows placing blocks adjacent or on top without taking the item.
- **Fail-safe Item Security**: Items cannot be picked up accidentally, despawn, or get destroyed. If the table is broken or chunks unload/reload, the item is completely safe and drops when the block is mined.

---

### 📚 Chiseled Bookshelf Scanning & Item Compatibility
- The table scans all nearby **Chiseled Bookshelves** within a customizable radius (default 3 horizontal, 2 vertical).
- **Strict Item Compatibility**: Enchantments are only offered if the item on the table naturally supports them (e.g. *Protection* on armor, *Sharpness* on weapons). Books accept any enchantment!
- Any enchanted books stored in the bookshelves are read dynamically to apply and combine upgrades onto your item.
- Supports cumulative upgrades (e.g. Sharpness I ➔ V).

---

### 👁️ Gaze-Activated Action Bar Preview
- Simply **look at the table** while an item is resting on it to see the impending enchantments and cost in your action bar:
  - *Example:* `Sharpness V • 15 Levels, 2x Lapis Lazuli`
- **Dynamic Color Feedback**:
  - 🟢 **Green**: You can afford the infusion (XP levels, catalyst count, and bookshelf power are satisfied).
  - 🔴 **Red**: Unaffordable or blocked (insufficient XP, catalyst, bookshelves, or requiring Crying Obsidian).
- **Smooth Readability**: After interacting with the table, the result feedback remains on screen for ~2.25 seconds before the stare preview resumes.

---

### ⚖️ Fair Delta XP Cost
- Level costs are calculated fairly based on the **upgrade delta**:  
  `cost = (targetLevel - currentLevel) * xpMultiplierPerLevel`.
  - Upgrading an unenchanted sword to *Sharpness V* costs `(5 - 0) * 3 = 15` levels.
  - Upgrading from *Sharpness IV* to *Sharpness V* costs only `(5 - 4) * 3 = 3` levels!

---

### 🏛️ Bookshelf Power System
- Surround the Infusion Table with **standard Bookshelves** (up to 15 in the classic 5×5 perimeter, heights -1 to +2).
- Each bookshelf provides **+2 levels** of maximum enchantment power:
  - 1 Bookshelf ➔ Max level 2
  - 8 Bookshelves ➔ Max level 16
  - 15 Bookshelves ➔ **Unlocks unlimited level capacity (>30)**!
- Full support for corner bookshelves and unobstructed line-of-sight.
- Shows clear progress: `"Not enough bookshelves (X/Y)"`.

---

### 💎 Catalyst & Inventory Support
- Uses **Lapis Lazuli** as the infusion catalyst by default (customizable via config).
- Catalyst items are tallied across your **entire inventory**—you only need to hold the catalyst in your main hand to trigger the infusion!
- Consumes catalysts first from the held hand, then from the rest of your inventory.

---

### 🔮 Crying Obsidian Pedestal (Late-Game Unlocks)
Place a block of **Crying Obsidian directly underneath** the Chiseled Infusion Table to unlock forbidden enchantments:
1. **Combine Incompatible Enchantments**:
   - Merge enchantments normally impossible together (e.g. *Sharpness* + *Bane of Arthropods* + *Smite*, or *Fortune* + *Silk Touch*, or all *Protection* variants).
2. **Over-Cap Enchantment Levels**:
   - Apply enchantments past their vanilla caps (e.g. *Sharpness VI+*, *Efficiency VI+*).
3. **Cosmetic Visuals**:
   - Emits swirling Nether portal particles around the floating item.
4. **Safety & Clarity**:
   - If incompatible or over-cap books are detected *without* Crying Obsidian underneath, the table prevents the operation and alerts:  
     `"Requires Crying Obsidian below"` / `"Serve Ossidiana Piangente sotto"` in red.

---

### 🎨 Visual, Audio & Performance Polish
- Runic enchantment glyphs stream from contributing bookshelves directly into the floating item during infusion.
- Custom particle bursts and spatial sound effects (varied bookshelf interactions and enchanting sounds).
- Built-in **micro-caching** for scan results, keeping server tick times near zero even with multiple players staring at tables.
- Configurable anvil repair cost reset (`clearRepairCost`).

---

## ⚙️ Configuration (`config/chiseledinfusion.json`)

```json
{
  "scanRadiusHorizontal": 3,
  "scanRadiusVertical": 2,
  "xpMultiplierPerLevel": 3,
  "lapisCostPerUpgrade": 1,
  "catalystItemId": "minecraft:lapis_lazuli",
  "clearRepairCost": true,
  "maxXpCost": 30
}
```

- `scanRadiusHorizontal`: Horizontal search radius for chiseled bookshelves (default: `3`).
- `scanRadiusVertical`: Vertical search radius upwards (default: `2`).
- `xpMultiplierPerLevel`: Levels of player XP required per enchantment level upgrade (default: `3`).
- `lapisCostPerUpgrade`: Catalysts consumed per upgrade applied (default: `1`).
- `catalystItemId`: Identifier of the catalyst item (default: `minecraft:lapis_lazuli`).
- `clearRepairCost`: Resets accumulated anvil repair cost penalty (default: `true`).
- `maxXpCost`: Cap for single infusion cost, `0` to disable (default: `30`).

---

## 🌐 Localization
- 🇬🇧 English (`en_us`)
- 🇮🇹 Italian (`it_it`)

---

## 📜 License
Available under the CC0 license.
