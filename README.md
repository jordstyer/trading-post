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
