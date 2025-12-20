# QTC Core

A PaperMC 1.21.8 plugin implementing custom RPG mechanics for Minecraft servers.

## Features

### Resource Gathering (Wynncraft-style)
- HP-based trees and ores that players mine over multiple hits
- Per-player node states - multiple players can mine the same node simultaneously
- Tool tier progression affects damage dealt
- Configurable regeneration timers
- Client-side rendering via PacketEvents (no world modification)

### Weapon & Armor Fusion
- Combine items to increase level and proficiency (max 100 each)
- Same type fusion increases proficiency
- Different type fusion increases level
- MythicLib NBT integration for stat storage

### Magic Elements
- Player-selectable magic elements
- Element progression and leveling
- MMOCore attribute integration

### Professions
- **Herbalism**: Rare bird nest drops with custom seeds
- **Fishing**: Zone-based fishing with PyroFishing integration
- **Hunting**: Track and trap system for capturing mobs

## Dependencies

| Plugin | Required | Purpose |
|--------|----------|---------|
| Paper 1.21.8 | Yes | Server platform |
| PacketEvents | Yes | Client-side block rendering |
| MythicLib | Yes | NBT storage, damage calculations |
| MMOCore | Yes | Profession XP, attributes |
| PlaceholderAPI | No | Placeholder support |
| PyroFishing | No | Fishing integration |

## Commands

```
/qtc reload              - Reload configurations
/qtc node place <type>   - Place a resource node
/qtc node remove         - Remove nearest node
/qtc node list           - List all nodes
/qtc node info           - Get node information
```

## Building

```bash
./gradlew build
```

Output JAR: `build/libs/QTCCore-*.jar`

## Configuration

See `.docs/PROJECT_PLAN.md` for detailed configuration examples.

## License

Proprietary - Riftal Studios
