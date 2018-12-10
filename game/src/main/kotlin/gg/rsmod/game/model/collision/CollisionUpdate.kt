package gg.rsmod.game.model.collision

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import com.google.common.collect.Multimaps
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import net.runelite.cache.definitions.ObjectDefinition

/**
 * @author Tom <rspsmods@gmail.com>
 */
class CollisionUpdate private constructor(val type: Type, val flags: Multimap<Tile, DirectionFlag>) {

    enum class Type {
        ADDING,
        REMOVING
    }

    class Builder {

        private val flags: Multimap<Tile, DirectionFlag> = MultimapBuilder.hashKeys().hashSetValues().build()

        private var type: Type? = null

        fun build(): CollisionUpdate {
            check(type != null) { "Type has not been set." }
            return CollisionUpdate(type!!, Multimaps.unmodifiableMultimap(flags))
        }

        fun setType(type: Type) {
            check(this.type == null) { "Type has already been set." }
            this.type = type
        }

        fun putTile(tile: Tile, impenetrable: Boolean, vararg directions: Direction) {
            check(directions.isNotEmpty()) { "Directions must not be empty." }
            directions.forEach { dir -> flags.put(tile, DirectionFlag(dir, impenetrable)) }
        }

        fun putWall(tile: Tile, impenetrable: Boolean, orientation: Direction) {
            putTile(tile, impenetrable, orientation)
            putTile(tile.step(1, orientation), impenetrable, orientation.getOpposite())
        }

        fun putLargeCornerWall(tile: Tile, impenetrable: Boolean, orientation: Direction) {
            val directions = orientation.getDiagonalComponents()
            putTile(tile, impenetrable, *directions)

            directions.forEach { dir ->
                putTile(tile.step(1, dir), impenetrable, dir.getOpposite())
            }
        }

        fun putObject(definitions: DefinitionSet, obj: GameObject) {
            val def = definitions.get(ObjectDefinition::class.java, obj.id)
            val type = obj.type
            val tile = obj.tile

            if (!unwalkable(def, type)) {
                return
            }

            val x = obj.tile.x
            val z = obj.tile.z
            val height = obj.tile.height
            var width = def.sizeX
            var length = def.sizeY
            val impenetrable = def.isBlocksProjectile
            val orientation = obj.rot

            if (orientation == 1 || orientation == 3) {
                width = def.sizeY
                length = def.sizeX
            }

            if (type == ObjectType.FLOOR_DECORATION.value) {
                if (def.isInteractive() && def.isSolidObj()) {
                    putTile(Tile(x, z, height), impenetrable, *Direction.NESW)
                }
            } else if (type >= ObjectType.DIAGONAL_WALL.value && type < ObjectType.FLOOR_DECORATION.value) {
                for (dx in 0 until width) {
                    for (dy in 0 until length) {
                        putTile(Tile(x + dx, z + dy, height), impenetrable, *Direction.NESW)
                    }
                }
            } else if (type == ObjectType.LENGTHWISE_WALL.value) {
                putWall(tile, impenetrable, Direction.WNES[orientation])
            } else if (type == ObjectType.TRIANGULAR_CORNER.value || type == ObjectType.RECTANGULAR_CORNER.value) {
                putWall(tile, impenetrable, Direction.WNES_DIAGONAL[orientation])
            } else if (type == ObjectType.WALL_CORNER.value) {
                putLargeCornerWall(tile, impenetrable, Direction.WNES_DIAGONAL[orientation])
            }
        }

        private fun unwalkable(definition: ObjectDefinition, type: Int): Boolean {
            val isSolidFloorDecoration = type == ObjectType.FLOOR_DECORATION.value && definition.isInteractive()
            val isRoof = type > ObjectType.DIAGONAL_INTERACTABLE.value && type < ObjectType.FLOOR_DECORATION.value

            val isWall = type >= ObjectType.LENGTHWISE_WALL.value && type <= ObjectType.RECTANGULAR_CORNER.value || type == ObjectType.DIAGONAL_WALL.value

            val isSolidInteractable = (type == ObjectType.DIAGONAL_INTERACTABLE.value || type == ObjectType.INTERACTABLE.value) && definition.isSolid

            return isWall || isRoof || isSolidInteractable || isSolidFloorDecoration
        }

        private fun ObjectDefinition.isInteractive(): Boolean = ((interactType and 0xFF) == 1)

        private fun ObjectDefinition.isSolidObj(): Boolean = isSolid
    }

}