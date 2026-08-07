# Trading Post

A Minecraft **Forge 1.20.1** mod. Craft a Trading Post, order goods from six distant specialist
colonies, and a cargo plane flies the route and airdrops your crate by parachute.

<p align="center">
  <img src="src/main/resources/logo.png" width="128" alt="Trading Post">
</p>

## What it does

- **A living market, not a shop.** Six colonies (Woodcutters, Desert Traders, Stonemasons, Miners'
  Guild, Farmers' Collective, Ocean Traders) each hold stock that depletes when you buy and
  regenerates over time. Prices move against supply: buy a colony down toward its reserve floor and
  the price climbs; sell into a glut and it falls. Large orders are priced progressively, so you pay
  more at the margin.
- **Deliveries actually arrive.** Purchases aren't teleported into your inventory. A freight plane
  flies a straight pass overhead, releases a parachute crate at the midpoint, and the crate lands
  near you as a real block you loot. It removes itself once emptied, so it never becomes clutter.
- **The plane routes around terrain.** Before it spawns, the game samples terrain along eight
  candidate headings and picks the flattest one, then flies high enough to clear it — including over
  chunks that aren't loaded yet.
- **Colonies trade with each other.** In the background, colonies import from one another along
  hand-authored demand links, and occasionally place a large one-off order for construction
  materials. Prices you see are shaped by an economy that runs whether or not you're watching.
- **Modpack-friendly by design.** The catalog is tag-driven, not a hardcoded item list. Any mod that
  tags its ingot `forge:ingots` or its wood `minecraft:logs` shows up automatically, priced from its
  own rarity. No per-item configuration needed.

## Getting started

Craft a Trading Post:

```
  E        E = Emerald
 PCP       C = Chest
 PPP       P = Any planks
```

Place it, right-click to open the market, and buy something. Emeralds are the currency — you can
sell into the same market to earn them.

## Configuration

`config/trading_post-common.toml` covers market tuning (regeneration rate, price band, reserve
floor/ceiling), the background colony economy, and delivery behaviour (flight speed, altitude and
terrain clearance, parachute fall time, landing search radius).

One knob is worth knowing about: the plane's flight length is automatically clamped to the server's
simulation distance. Minecraft only ticks entities near players, so a plane spawned beyond that
would freeze mid-air and never deliver. Raise `simulation-distance` if you want longer approaches.

### Adding or repricing items

The catalog can be extended with an ordinary **datapack** — no recompiling, no Java. Drop a JSON
file anywhere under `data/trading_post/market_overrides/` in a datapack (world-specific, or bundled
in another mod/modpack) and it merges in automatically on server start or `/reload`:

```json
{
  "overrides": [
    { "colony": "farmers_collective", "item": "minecraft:bamboo", "price": 3.0, "stock": 512 },
    { "colony": "miners_guild", "item": "examplemod:mithril_ingot", "price": 1.5, "stock": 64 }
  ]
}
```

`colony` must be one of `woodcutters`, `desert_traders`, `stonemasons`, `miners_guild`,
`farmers_collective`, `ocean_traders`. `price` is emeralds per single unit (for reference, the
built-in catalog runs roughly 0.06 for common blocks up to 1.5+ for the rarest items — see
`MarketDefaults.Rarity` for the full ladder). Adding an item the colony doesn't sell yet needs both
`price` and `stock`; repricing one it already sells can omit either to leave that value as-is. Bad
entries (unknown colony, unknown item, or a new item missing a required field) are logged and
skipped rather than breaking the whole file.

A world only picks up a *world-added* datapack after you `/datapack enable` it once — standard
Minecraft behavior, not specific to this mod. A pack bundled inside another mod's jar is active
immediately.

One thing this can't do: retroactively change a price a world has already saved. The mod never
overwrites an item's price/stock once a player's world has seen it (this is also why balance patches
don't silently reset your economy) — an override only affects items a save encounters for the first
time after the datapack is added.

## Building

Requires **JDK 17**.

```bash
./gradlew build
```

The jar lands in `build/libs/`.

Textures and the mod icon are generated, not hand-painted — `python scripts/gen_textures.py`
regenerates every one of them from a single palette. It needs Pillow.

## License

MIT — see [LICENSE](LICENSE). You're free to include this in modpacks without asking.
