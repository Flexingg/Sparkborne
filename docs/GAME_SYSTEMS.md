# Sparkborne — Complete Game Systems & Architecture Reference

Sparkborne is a 100% deterministic, rule-based Android roguelike RPG driven by your real-world steps, lifting volume, sleep, hydration, nutrition/macros, and bodyweight trends. There is **no AI Game Master** and **no LLM randomness** anywhere in the engine—every stat, drop, skill level, and encounter is transparent, reproducible, and unit-tested.

---

## 1. Module Architecture

| Module | Role | Key Files |
| :--- | :--- | :--- |
| **`:core-telemetry`** | Pure Kotlin/JVM API client, normalizer, idempotency hash, and dual-source merger | `SparkyFitnessApiClient.kt`, `TelemetryNormalizer.kt`, `SnapshotIdempotency.kt`, `TelemetrySnapshotMerger.kt`, `BodyweightTrendCalculator.kt` |
| **`:core-game`** | Pure Kotlin/JVM deterministic RPG engine (zero Android dependencies, fully unit-tested) | `TelemetryRulesEngine.kt`, `StepBankManager.kt`, `WorldManager.kt`, `SkillEngine.kt`, `CollectibleEngine.kt`, `ToolbeltEngine.kt`, `CraftingEngine.kt`, `CombatResolver.kt`, `ExpeditionManager.kt`, `SparkpediaEngine.kt` |
| **`:app`** | Android Jetpack Compose + Room + WorkManager + Health Connect + Hardware Step Sensor UI | `MainActivity.kt`, `SparkborneViewModel.kt`, `HealthConnectReader.kt`, `LiveStepTracker.kt`, `SparkyBackgroundSyncWorker.kt`, `WorldMapScreen.kt`, `HeroSheetScreen.kt`, `BeastSanctuaryScreen.kt`, `TelemetryInspectorScreen.kt` |

---

## 2. Dual-Source Telemetry Pipeline (Zero Double-Counting)

Sparkborne fuses **three live data sources** using strict per-day high-water marks:

1. **Phone Hardware Step Counter (`LiveStepTracker.kt`):** Uses Android `Sensor.TYPE_STEP_COUNTER` (handles midnight rollovers & device reboots) to credit walking steps instantaneously into your **Step Bank**.
2. **Local Android Health Connect (`HealthConnectReader.kt`):** Reads `StepsRecord`, `SleepSessionRecord` (Deep/REM/Light/Awake), `HydrationRecord`, `NutritionRecord`, and `WeightRecord` directly on-device with zero server round-trip. Supports **Background Reads** (`READ_HEALTH_DATA_IN_BACKGROUND`) and **Multi-Year History** (`READ_HEALTH_DATA_HISTORY`, 90-day batched chunks via `aggregateGroupByPeriod`).
3. **SparkyFitness Server (`SparkyFitnessApiClient.kt`):** Authoritative cloud backup and sole source of **Daily Goals** (`TelemetryGoals`) and **Liftosaur per-muscle-group lifting volume**. Also fires upstream `POST /api/integrations/garmin/sync` and `POST /api/integrations/liftosaur/sync` on manual/background sync.

### Idempotency & Reconciliation
- Every `DailyTelemetrySnapshot` has a canonical SHA-256 `contentHash` (excluding `syncedAtEpochMs` and `metricSources`).
- `TelemetrySnapshotMerger` merges Health Connect and SparkyFitness per metric via `maxOf` so lagging cloud syncs never regress local Health Connect progress.
- `GameState.dailyCreditedSteps[date]` tracks the highest step total credited for each day across Phone Sensor + Health Connect + SparkyFitness so **steps are never double-counted**.
- **Historical Backfill (`1 Year`, `3 Years`, `All-Time 5y`):** Populates Room's historical snapshots (powering `7D` / `ALL` Health Averages & Bodyweight Trends) and seeds baseline Muscle Strength XP without dumping years of old steps into today's Step Bank or auto-completing quests.

---

## 3. Fitness Action Point Banks (`ActionBankManager.kt`, `StepBankManager.kt`)

All synced real-world exercise, hydration, and calorie burn accumulates into **5 dedicated Fitness Action Point Banks** that you spend on matching skills and combat whenever you sit down to play. **The more you level up the skills tied to a bank, the larger that bank's capacity grows:**

| Bank | Real-World Source | Fuels | Base Cap + Skill Scaling |
| :--- | :--- | :--- | :--- |
| **🥾 Steps** | Phone Pedometer + Health Connect + SparkyFitness | World Travel, Foraging, Archaeology, Trade, Delve Node unlocking | `50,000` + Hero Lv + Muscle Ranks + Foraging/Archaeology/Trade (`+15,000`/lvl) |
| **💪 Push Volume (`kg`)** | Chest, Shoulders, Triceps lifting volume | Mining & Smithing (`250 kg` base/cycle) | `25,000 kg` + Mining/Smithing (`+5,000 kg`/lvl + quadratic bonus) |
| **🪓 Pull & Legs (`kg`)** | Back, Biceps, Forearms, Quads, Hamstrings, Glutes, Calves, Core | Woodcutting, Fishing & Salvage (`250 kg` base/cycle) | `25,000 kg` + Woodcutting/Fishing/Salvage (`+4,500 kg`/lvl + quadratic bonus) |
| **💧 Hydration (`mL`)** | Health Connect `HydrationRecord` + SparkyFitness water | Diving / Swimming & Alchemy (`250 mL` base/cycle) | `15,000 mL` + Diving/Alchemy (`+3,000 mL`/lvl + quadratic bonus) |
| **🔥 Energy Burn (`kcal`)** | Health Connect `ActiveCaloriesBurnedRecord` (fallback: `steps×0.04 + liftKg×0.025`) | Fighting Monsters in Delves (`50 kcal` skirmish / `100 kcal` boss) & Runecrafting (`100 kcal` base/cycle) | `10,000 kcal` + Runecrafting (`+2,500 kcal`/lvl + quadratic) + Hero Level (`+1,500 kcal`/lvl) |

- **Work Efficiency Applies Everywhere:** Skill levels, Tools, Collectibles, and Trailblazer Brew reduce the Action Point cost per cycle for every bank (down to a `35%` floor).
- **Starter Reserves:** New heroes start with modest reserves (`500 kg` Push, `500 kg` Pull/Legs, `1,000 mL` Hydration, `300 kcal` Active Burn) so every skill and first delve is playable right away.

---

## 4. World Map & Eastern Frontier (`WorldCatalog.kt`, `WorldMapScreen.kt`)

A seamless **`3 × 3` stitched pixel-art continent** (9 modular `384×384` tiles rendered with `FilterQuality.None` nearest-neighbor crispness):

| Region | Town | Shopkeeper | Key Specialties |
| :--- | :--- | :--- | :--- |
| Western Highlands | **Emberhearth Outpost** | Bram Ironbrow | Starting Safe Haven, Basalt Mining, Forge Bellows, Intro Quest |
| Central Forest | **Verdant Crossing** | Sylva Briarwood | Moonleaf Foraging, Ironwood Logging, Strider Greaves, Twin Blades |
| Southern Coast | **Tidebreak Harbor** | Capt. Orin Saltwake | Fishing, Pearl Diving, *Tidal Mend* spell, Abyssal Tide Eggs |
| Eastern Bog | **Mirefen Village** | Vexis Bogbrew | Alchemy, Salvage, *Iron Aegis* spell, Emberbound Relic |
| Northern Peaks | **Stormspire Citadel** | Archivist Lyra Volt | Volt Runecrafting, *Tempest Nova*, Titanforged Warhammer |
| Caldera | **Obsidian Crag** | Kaelen the Ash-Forged | Abyssal Excavation, Vanguard Plate, Embersteel Greatsword |
| **Eastern Frontier** | **Frostpeak Bastion** | Torvald Rimebeard | Glacial Mining, Ice-Fishing, *Frostfang Halberd*, *Glacier Crown*, *Glacial Ward* |
| **Eastern Frontier** | **Sunspire Oasis** | Zahra Sunweaver | Archaeology, Caravan Trade, *Solaris Scimitars*, *Dune Greaves*, Radiant Sun Egg |
| **Eastern Frontier** | **Ashen Sanctum** | Malakor the Starbound | Astral Smithing, Rift Communion, *Starforged Maul*, *Astral Relic*, *Solar Cataclysm* |

---

## 5. Skill Levels (1–99) & Skill Chests (`SkillEngine.kt`)

There are **11 trainable skills**: *Mining, Smithing, Foraging, Woodcutting, Fishing, Diving, Alchemy, Salvage, Runecrafting, Archaeology, Trade*.

- **XP Curve:** $\text{XP}(\text{lvl}) = 25(\text{lvl}-1)^2 + 25(\text{lvl}-1)$ (Lv 1 = 0, Lv 50 = 60,000, Lv 99 = 240,100).
- **Work Efficiency:** Every **2 Skill Levels** grants `+1% Work Efficiency` (up to `+49%` at Lv 99), reducing steps required per activity cycle:
  $$\text{EffectiveSteps} = \max\left(0.35 \times \text{BaseSteps},\; \text{BaseSteps} \times \frac{100}{100 + \text{TotalEff\%}}\right)$$
- **Skill Chests:** Every **5 activity completions** awards a **Skill Chest** for that skill (open in **Hero → Skills**). Chests grant level-scaled Gold, Iron, Shards, and bonus Skill XP, and **every 5th chest opened guarantees a rare Skill-Themed Monster Egg**.

---

## 6. Rare Activity Collectibles & Collection Log (`CollectibleEngine.kt`)

12 permanent account-wide relics unlocked deterministically by reaching milestone completions (4–6 cycles) in regional Town Activities:

- **Work Efficiency:** *Ironwood Compass* (`+4%`), *Glacial Pocket Watch* (`+6%`)
- **Gold Yield:** *Bram's Ember Seal* (`+4%`), *Gilded Silverfin Lure* (`+5%`), *Fossilized Sunstone* (`+6%`)
- **Iron & Shard Yield:** *Basalt Heartstone* (`+4%`), *Obsidian Caldera Core* (`+6%`)
- **Egg Incubation Speed:** *Moonleaf Wreath* (`+5%`), *Abyssal Pearl Lens* (`+5%`)
- **Combat Power (ATK & Spell):** *Luminous Spore Vial* (`+4%`), *Stormspire Capacitor* (`+5%`), *Starforged Astrolabe* (`+6%`)

---

## 7. Toolbelt Slots & 1-Tap Gear Set Presets (`ToolbeltEngine.kt`)

- **Toolbelt Capacity:** Starts with **2 slots** (`Lv 0–4`), unlocks **3rd slot at Lv 5**, and **4th slot at Lv 10**.
- **6 Craftable Skilling Tools:** *Iron Pickaxe*, *Moonleaf Sickle*, *Tidecaller Rod*, *Mire Alembic*, *Sunstone Trowel*, and *Starbound Multi-Tool* (applies to all skills). Equipped tools stack `+Work Efficiency` and `+Bonus Yield` on matching activities.
- **Gear Set Presets:** Save up to **3 full loadouts** (*Combat*, *Skilling*, *Boss*) storing Weapon, 4 Armor pieces, Attuned Spells, and Equipped Tools for 1-tap swapping in **Hero → Gear**.

---

## 8. Crafting, Cooking & Consumables (`CraftingEngine.kt`)

Every Town Activity drops matching raw **Materials** (*Basalt Ore, Moonleaf Herb, Silverfin Fish, Mire Spore, Sunstone Dust, Astral Essence*).

- **5 Craftable Consumables** (in **Hero → Craft**):
  - **Trailblazer Brew:** `+25% Work Efficiency` for your next 10 Town Activity cycles.
  - **Hearty Silverfin Stew:** Restores `+45 HP` in an active Delve.
  - **Spore Mana Tonic:** Restores `+18 Mana` in an active Delve.
  - **Warmth & Dew Elixir:** Immediately adds `+180m Sleep Warmth` & `+900mL Dew` to your first incubating Egg (auto-hatching if ready!).
  - **Astral Surge Flask:** Full HP & Mana restore + `20 Shield` in an active Delve.
- **Masterwork Quality Tiers:** Scale deterministically with the recipe's Skill Level:
  - **Standard** (Lv 1): `1.0×` potency
  - **Fine** (Lv 5): `1.15×` potency
  - **Superior** (Lv 12): `1.30×` potency
  - **Masterwork** (Lv 25): `1.50×` potency + **2× crafted quantity**!

---

## 9. "While You Were Away" Chronicle (`AwaySummary`)

Whenever Health Connect, SparkyFitness, or your phone pedometer banks new progress while you were away, Sparkborne accumulates it into `GameState.pendingAwaySummary` and greets you with a dismissible **🌙 While You Were Away** modal summarizing Steps Banked (AP), Lifting Volume added, Sleep/Hydration infused, and Hatched Companions.
