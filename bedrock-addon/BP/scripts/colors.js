// A marker color for a block, from words in its name (Bedrock scripts can't read a block's map color before it's
// placed). Close enough to tell wood from stone from glass at a glance.
const WORDS = [
  ['white', [0.95, 0.95, 0.95]], ['light_gray', [0.62, 0.62, 0.6]], ['silver', [0.62, 0.62, 0.6]], ['gray', [0.35, 0.35, 0.38]], ['black', [0.12, 0.12, 0.14]],
  ['brown', [0.45, 0.3, 0.18]], ['red', [0.75, 0.2, 0.18]], ['orange', [0.92, 0.5, 0.15]], ['yellow', [0.95, 0.85, 0.2]], ['lime', [0.5, 0.8, 0.2]],
  ['green', [0.3, 0.5, 0.15]], ['cyan', [0.15, 0.55, 0.6]], ['light_blue', [0.45, 0.7, 0.95]], ['blue', [0.2, 0.3, 0.75]], ['purple', [0.5, 0.25, 0.7]],
  ['magenta', [0.75, 0.3, 0.7]], ['pink', [0.95, 0.6, 0.7]],
  ['glass', [0.7, 0.9, 1.0]], ['ice', [0.6, 0.8, 1.0]], ['leaves', [0.25, 0.55, 0.2]], ['grass', [0.4, 0.65, 0.25]], ['moss', [0.35, 0.5, 0.2]],
  ['cherry', [0.9, 0.65, 0.7]], ['crimson', [0.55, 0.2, 0.3]], ['warped', [0.2, 0.55, 0.5]], ['mangrove', [0.55, 0.25, 0.2]], ['bamboo', [0.8, 0.75, 0.35]],
  ['pale_oak', [0.85, 0.8, 0.75]], ['dark_oak', [0.3, 0.2, 0.1]], ['darkoak', [0.3, 0.2, 0.1]], ['spruce', [0.4, 0.28, 0.16]], ['birch', [0.85, 0.78, 0.55]],
  ['jungle', [0.65, 0.45, 0.3]], ['acacia', [0.75, 0.4, 0.2]], ['oak', [0.7, 0.55, 0.33]], ['wood', [0.65, 0.5, 0.3]], ['log', [0.45, 0.33, 0.2]], ['plank', [0.7, 0.55, 0.33]],
  ['deepslate', [0.3, 0.3, 0.33]], ['blackstone', [0.2, 0.18, 0.22]], ['basalt', [0.35, 0.35, 0.38]], ['andesite', [0.55, 0.55, 0.55]], ['diorite', [0.85, 0.85, 0.85]],
  ['granite', [0.6, 0.42, 0.35]], ['red_sandstone', [0.75, 0.4, 0.2]], ['sandstone', [0.85, 0.8, 0.6]], ['sand', [0.88, 0.83, 0.6]], ['gravel', [0.5, 0.48, 0.47]],
  ['quartz', [0.92, 0.9, 0.86]], ['prismarine', [0.4, 0.7, 0.65]], ['purpur', [0.65, 0.48, 0.65]], ['end_stone', [0.88, 0.88, 0.65]], ['nether_brick', [0.3, 0.12, 0.15]],
  ['netherrack', [0.5, 0.2, 0.2]], ['brick', [0.6, 0.3, 0.25]], ['mud', [0.4, 0.33, 0.28]], ['terracotta', [0.6, 0.38, 0.28]], ['copper', [0.8, 0.5, 0.35]],
  ['iron', [0.85, 0.85, 0.85]], ['gold', [0.95, 0.8, 0.25]], ['diamond', [0.4, 0.9, 0.9]], ['emerald', [0.2, 0.8, 0.4]], ['lapis', [0.15, 0.3, 0.75]],
  ['redstone', [0.8, 0.1, 0.1]], ['snow', [0.97, 0.97, 1.0]], ['dirt', [0.5, 0.36, 0.25]], ['clay', [0.62, 0.65, 0.72]], ['water', [0.2, 0.4, 0.9]],
  ['lava', [1.0, 0.45, 0.1]], ['torch', [1.0, 0.8, 0.3]], ['lantern', [1.0, 0.75, 0.3]], ['wool', [0.9, 0.9, 0.9]], ['stone', [0.5, 0.5, 0.5]], ['cobble', [0.45, 0.45, 0.45]],
];
const cache = new Map();
export function colorOf(name) {
  let c = cache.get(name);
  if (c) return c;
  const n = name.replace('minecraft:', '');
  c = [0.75, 0.75, 0.8];
  for (const [w, rgb] of WORDS) if (n.includes(w)) { c = rgb; break; }
  cache.set(name, c);
  return c;
}
