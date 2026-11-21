package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

/**
 * One planned kitty drop: pure data, no entity. The shower keeps a queue of these and turns each one into a real
 * meteor only when its time comes.
 *
 * @param due      ticks after the shower started at which this kitty should drop
 * @param tx       target (landing) column, block x
 * @param tz       target (landing) column, block z
 * @param angleDeg entry angle in degrees from straight down
 * @param yawDeg   horizontal heading of the fall, Minecraft yaw degrees
 * @param size     meteor size; KittySpawner clamps it to 1..3 again before use
 */
public record KittyDrop(int due, double tx, double tz, float angleDeg, float yawDeg, byte size) {
}
