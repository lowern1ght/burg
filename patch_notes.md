## 🏘️ Once Upon a Town v0.0.3 [Alpha]
### Heavily improved the Town map:
- The town map now show what the buildings are producing
- There is different colors depending what's the building type, green for gardens, yellow for towncenter, red for jobs and brown for houses
- Improved UI appearance
### New village orientations and settlements:
- Once the village spawns it start as the 'settlement' tier, the lowest. The settlement produces a bit of ressources to start the village: logs, planks, cobblestone and apples
- Each settlement has a dedicated starting orientation: food/stone/wood . The orientation gives to the village a free building following it's orientation, and instantly build it at the beginning

**Orientations:**
- Wood: tree_field
- Stone: stone_deposit
- Food: [One of the following] pig_field, cow_field, farm_field
### Building system improvements:
- The roads are now going to use planks instead of dirt path when they are placed near water, as vanilla villages are already doing
- The NPC is going to prioritize the building locations closer from the center instead of always expending the roads, it means, the village should be less stuck and expend less too far. The villages should be more dense
- Added a few extra rules to optimize the NPC building choice and order
### New buildings:
- Added new road types, job buildings and house variations
