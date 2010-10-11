// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import com.sk89q.worldedit.*;

/**
 * This class can wrap all block editing operations into one "edit session" that
 * stores the state of the blocks before modification. This allows for easy
 * undo or redo. In addition to that, this class can use a "queue mode" that
 * will know how to handle some special types of items such as signs and
 * torches. For example, torches must be placed only after there is already
 * a block below it, otherwise the torch will be placed as an item.
 *
 * @author sk89q
 */
public class EditSession {
    /**
     * Stores the original blocks before modification.
     */
    private HashMap<Point,Integer> original = new HashMap<Point,Integer>();
    /**
     * Stores the current blocks.
     */
    private HashMap<Point,Integer> current = new HashMap<Point,Integer>();
    /**
     * Queue.
     */
    private HashMap<Point,Integer> queue = new HashMap<Point,Integer>();
    /**
     * The maximum number of blocks to change at a time. If this number is
     * exceeded, a MaxChangedBlocksException exception will be
     * raised. -1 indicates no limit.
     */
    private int maxBlocks = -1;
    /**
     * Indicates whether some types of blocks should be queued for best
     * reproduction.
     */
    private boolean queued = false;
    /**
     * List of object types to queue.
     */
    private static final HashSet<Integer> queuedBlocks = new HashSet<Integer>();

    static {
        queuedBlocks.add(50); // Torch
        queuedBlocks.add(37); // Yellow flower
        queuedBlocks.add(38); // Red rose
        queuedBlocks.add(39); // Brown mushroom
        queuedBlocks.add(40); // Red mushroom
        queuedBlocks.add(59); // Crops
        queuedBlocks.add(63); // Sign
        queuedBlocks.add(75); // Redstone torch (off)
        queuedBlocks.add(76); // Redstone torch (on)
        queuedBlocks.add(84); // Reed
    }

    /**
     * Default constructor. There is no maximum blocks limit.
     */
    public EditSession() {
    }

    /**
     * Construct the object with a maximum number of blocks.
     */
    public EditSession(int maxBlocks) {
        if (maxBlocks < -1) {
            throw new IllegalArgumentException("Max blocks must be >= -1");
        }
        this.maxBlocks = maxBlocks;
    }

    /**
     * Sets a block without changing history.
     * 
     * @param pt
     * @param blockType
     * @return Whether the block changed
     */
    private boolean rawSetBlock(Point pt, int blockType) {
        return etc.getMCServer().e.d(pt.getBlockX(), pt.getBlockY(),
                pt.getBlockZ(), blockType);
    }
    
    /**
     * Sets the block at position x, y, z with a block type. If queue mode is
     * enabled, blocks may not be actually set in world until flushQueue()
     * is called.
     *
     * @param x
     * @param y
     * @param z
     * @param blockType
     * @return Whether the block changed -- not entirely dependable
     */
    public boolean setBlock(int x, int y, int z, int blockType)
        throws MaxChangedBlocksException {
        return setBlock(new Point(x, y, z), blockType);
    }

    /**
     * Sets the block at position x, y, z with a block type. If queue mode is
     * enabled, blocks may not be actually set in world until flushQueue()
     * is called.
     *
     * @param pt
     * @param blockType
     * @return Whether the block changed -- not entirely dependable
     */
    public boolean setBlock(Point pt, int blockType)
        throws MaxChangedBlocksException {
        if (!original.containsKey(pt)) {
            original.put(pt, getBlock(pt));

            if (maxBlocks != -1 && original.size() > maxBlocks) {
                throw new MaxChangedBlocksException(maxBlocks);
            }
        }

        current.put(pt, blockType);

        return smartSetBlock(pt, blockType);
    }

    /**
     * Actually set the block. Will use queue.
     * 
     * @param pt
     * @param blockType
     * @return
     */
    private boolean smartSetBlock(Point pt, int blockType) {        
        if (queued) {
            if (blockType != 0 && queuedBlocks.contains(blockType)
                    && rawGetBlock(pt.add(0, -1, 0)) == 0) {
                queue.put(pt, blockType);
                return getBlock(pt) != blockType;
            } else if (blockType == 0
                    && queuedBlocks.contains(rawGetBlock(pt.add(0, 1, 0)))) {
                rawSetBlock(pt.add(0, 1, 0), 0); // Prevent items from being dropped
            }
        }

        return rawSetBlock(pt, blockType);
    }

    /**
     * Gets the block type at a position x, y, z.
     *
     * @param pt
     * @return Block type
     */
    public int getBlock(Point pt) {
        // In the case of the queue, the block may have not actually been
        // changed yet
        if (queued) {
            if (current.containsKey(pt)) {
                return current.get(pt);
            }
        }
        return etc.getMCServer().e.a(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ());
    }

    /**
     * Gets the block type at a position x, y, z.
     *
     * @param x
     * @param y
     * @param z
     * @return Block type
     */
    public int getBlock(int x, int y, int z) {
        return getBlock(new Point(x, y, z));
    }

    /**
     * Gets the block type at a position x, y, z.
     *
     * @param pt
     * @return Block type
     */
    public int rawGetBlock(Point pt) {
        return etc.getMCServer().e.a(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ());
    }

    /**
     * Restores all blocks to their initial state.
     */
    public void undo() {
        for (Map.Entry<Point,Integer> entry : original.entrySet()) {
            Point pt = (Point)entry.getKey();
            smartSetBlock(pt, (int)entry.getValue());
        }
        flushQueue();
    }

    /**
     * Sets to new state.
     */
    public void redo() {
        for (Map.Entry<Point,Integer> entry : current.entrySet()) {
            Point pt = (Point)entry.getKey();
            smartSetBlock(pt, (int)entry.getValue());
        }
        flushQueue();
    }

    /**
     * Get the number of changed blocks.
     * 
     */
    public int size() {
        return original.size();
    }

    /**
     * Get the maximum number of blocks that can be changed. -1 will be
     * returned if disabled.
     *
     * @return
     */
    public int getBlockChangeLimit() {
        return maxBlocks;
    }

    /**
     * Set the maximum number of blocks that can be changed.
     * 
     * @param maxBlocks -1 to disable
     */
    public void setBlockChangeLimit(int maxBlocks) {
        if (maxBlocks < -1) {
            throw new IllegalArgumentException("Max blocks must be >= -1");
        }
        this.maxBlocks = maxBlocks;
    }

    /**
     * Returns queue status.
     * 
     * @return
     */
    public boolean isQueueEnabled() {
        return queued;
    }

    /**
     * Queue certain types of block for better reproduction of those blocks.
     */
    public void enableQueue() {
        queued = true;
    }

    /**
     * Disable the queue. This will flush the queue.
     */
    public void disableQueue() {
        if (queued != false) {
            flushQueue();
        }
        queued = false;
    }

    /**
     * Finish off the queue.
     */
    public void flushQueue() {
        if (!queued) { return; }
        
        for (Map.Entry<Point,Integer> entry : queue.entrySet()) {
            Point pt = (Point)entry.getKey();
            rawSetBlock(pt, (int)entry.getValue());
        }
    }

    /**
     * Fills an area recursively in the X/Z directions.
     *
     * @param x
     * @param z
     * @param origin
     * @param blockType
     * @param radius
     * @param depth
     * @return
     */
    public int fillXZ(int x, int z, Point origin, int blockType, int radius, int depth)
            throws MaxChangedBlocksException {
        double dist = Math.sqrt(Math.pow(origin.getX() - x, 2) + Math.pow(origin.getZ() - z, 2));
        int minY = origin.getBlockY() - depth + 1;
        int affected = 0;

        if (dist > radius) {
            return 0;
        }

        if (getBlock(new Point(x, origin.getY(), z)) == 0) {
            affected = fillY(x, (int)origin.getY(), z, blockType, minY);
        } else {
            return 0;
        }

        affected += fillXZ(x + 1, z, origin, blockType, radius, depth);
        affected += fillXZ(x - 1, z, origin, blockType, radius, depth);
        affected += fillXZ(x, z + 1, origin, blockType, radius, depth);
        affected += fillXZ(x, z - 1, origin, blockType, radius, depth);

        return affected;
    }

    /**
     * Recursively fills a block and below until it hits another block.
     *
     * @param x
     * @param cy
     * @param z
     * @param blockType
     * @param minY
     * @throws MaxChangedBlocksException
     * @return
     */
    private int fillY(int x, int cy, int z, int blockType, int minY)
        throws MaxChangedBlocksException {
        int affected = 0;

        for (int y = cy; y >= minY; y--) {
            Point pt = new Point(x, y, z);
            
            if (getBlock(pt) == 0) {
                setBlock(pt, blockType);
                affected++;
            } else {
                break;
            }
        }

        return affected;
    }

    /**
     * Remove blocks above.
     * 
     * @param pos
     * @param size,
     * @param height
     * @return
     */
    public int removeAbove(Point pos, int size, int height) throws
            MaxChangedBlocksException {
        int maxY = Math.min(127, pos.getBlockY() + height - 1);
        size--;
        int affected = 0;

        for (int x = (int)pos.getX() - size; x <= (int)pos.getX() + size; x++) {
            for (int z = (int)pos.getZ() - size; z <= (int)pos.getZ() + size; z++) {
                for (int y = (int)pos.getY(); y <= maxY; y++) {
                    Point pt = new Point(x, y, z);

                    if (getBlock(pt) != 0) {
                        setBlock(pt, 0);
                        affected++;
                    }
                }
            }
        }

        return affected;
    }

    /**
     * Remove blocks below.
     *
     * @param pos
     * @param size,
     * @param height
     * @return
     */
    public int removeBelow(Point pos, int size, int height) throws
            MaxChangedBlocksException {
        int minY = Math.max(0, pos.getBlockY() - height);
        size--;
        int affected = 0;

        for (int x = (int)pos.getX() - size; x <= (int)pos.getX() + size; x++) {
            for (int z = (int)pos.getZ() - size; z <= (int)pos.getZ() + size; z++) {
                for (int y = (int)pos.getY(); y >= minY; y--) {
                    Point pt = new Point(x, y, z);

                    if (getBlock(pt) != 0) {
                        setBlock(pt, 0);
                        affected++;
                    }
                }
            }
        }

        return affected;
    }

    /**
     * Sets all the blocks inside a region to a certain block type.
     *
     * @param region
     * @param blockType
     * @return
     * @throws MaxChangedBlocksException
     */
    public int setBlocks(Region region, int blockType)
            throws MaxChangedBlocksException {
        int affected = 0;

        if (region instanceof CuboidRegion) {
            // Doing this for speed
            Point min = region.getMinimumPoint();
            Point max = region.getMaximumPoint();

            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                    for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                        Point pt = new Point(x, y, z);

                        if (setBlock(pt, blockType)) {
                            affected++;
                        }
                    }
                }
            }
        } else {
            for (Point pt : region) {
                if (setBlock(pt, blockType)) {
                    affected++;
                }
            }
        }

        return affected;
    }

    /**
     * Replaces all the blocks of a type inside a region to another block type.
     *
     * @param region
     * @param fromBlockType -1 for non-air
     * @param toBlockType
     * @return
     * @throws MaxChangedBlocksException
     */
    public int replaceBlocks(Region region, int fromBlockType, int toBlockType)
            throws MaxChangedBlocksException {
        int affected = 0;

        if (region instanceof CuboidRegion) {
            // Doing this for speed
            Point min = region.getMinimumPoint();
            Point max = region.getMaximumPoint();

            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                    for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                        Point pt = new Point(x, y, z);
                        int curBlockType = getBlock(pt);

                        if (fromBlockType == -1 && curBlockType != 0 ||
                                curBlockType == fromBlockType) {
                            if (setBlock(pt, toBlockType)) {
                                affected++;
                            }
                        }
                    }
                }
            }
        } else {
            for (Point pt : region) {
                int curBlockType = getBlock(pt);

                if (fromBlockType == -1 && curBlockType != 0 ||
                        curBlockType == fromBlockType) {
                    if (setBlock(pt, toBlockType)) {
                        affected++;
                    }
                }
            }
        }

        return affected;
    }

    /**
     * Make faces of the region (as if it was a cuboid if it's not).
     * 
     * @param region
     * @param blockType
     * @return
     * @throws MaxChangedBlocksException
     */
    public int makeCuboidFaces(Region region, int blockType)
            throws MaxChangedBlocksException {
        int affected = 0;

        Point min = region.getMinimumPoint();
        Point max = region.getMaximumPoint();
        
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                if (setBlock(x, y, min.getBlockZ(), blockType)) { affected++; }
                if (setBlock(x, y, max.getBlockZ(), blockType)) { affected++; }
                affected++;
            }
        }

        for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
            for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                if (setBlock(min.getBlockX(), y, z, blockType)) { affected++; }
                if (setBlock(max.getBlockX(), y, z, blockType)) { affected++; }
            }
        }

        for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
            for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
                if (setBlock(x, min.getBlockY(), z, blockType)) { affected++; }
                if (setBlock(x, max.getBlockY(), z, blockType)) { affected++; }
            }
        }

        return affected;
    }

    /**
     * Overlays a layer of blocks over a cuboid area.
     * 
     * @param region
     * @param upperY
     * @param lowerY
     * @param blockType
     * @return
     * @throws MaxChangedBlocksException
     */
    public int overlayCuboidBlocks(Region region, int blockType)
            throws MaxChangedBlocksException {
        Point min = region.getMinimumPoint();
        Point max = region.getMaximumPoint();
        
        int upperY = Math.min(127, max.getBlockY() + 1);
        int lowerY = Math.max(0, min.getBlockY()- 1);

        int affected = 0;

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                for (int y = upperY; y >= lowerY; y--) {
                    if (y + 1 <= 127 && getBlock(x, y, z) != 0 && getBlock(x, y + 1, z) == 0) {
                        if (setBlock(x, y + 1, z, blockType)) {
                            affected++;
                        }
                        break;
                    }
                }
            }
        }

        return affected;
    }

    public int stackCuboidRegion(Region region, int xm, int ym, int zm,
            int count, boolean copyAir)
            throws MaxChangedBlocksException {
        int affected = 0;

        Point min = region.getMinimumPoint();
        Point max = region.getMaximumPoint();
        int xs = region.getWidth();
        int ys = region.getHeight();
        int zs = region.getLength();

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                    int blockType = getBlock(new Point(x, y, z));

                    if (blockType != 0 || copyAir) {
                        for (int i = 1; i <= count; i++) {
                            if (setBlock(x + xs * xm * i, y + ys * ym * i,
                                    z + zs * zm * i, blockType)) {
                                affected++;
                            }
                        }
                    }
                }
            }
        }

        return affected;
    }
}