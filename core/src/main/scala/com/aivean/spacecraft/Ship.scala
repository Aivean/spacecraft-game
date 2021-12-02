package com.aivean.spacecraft

import com.aivean.spacecraft.Model.Destructible
import com.aivean.spacecraft.Ship.{Cell, CellUserData}
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.{MathUtils, Vector2}
import com.badlogic.gdx.physics.box2d.{FixtureDef, PolygonShape}
import com.gdxscala._

class Ship(var cells: List[CellUserData[Cell]])

object Ship {

  case class CellUserData[+T <: Cell](x: Float, y: Float, cell: T) {

    def getDestructible = cell match {
      case c: Destructible => Some(c)
      case _ => None
    }
  }

  abstract class Cell(val maxDurability: Float) extends Destructible with Cloneable {
    type Self <: Cell

    var durability = maxDurability

    override def detached: Boolean = durability <= maxDurability / 3

    override def destroyed: Boolean = durability <= 0.0001f

    override def damage(impulse: Float): Unit = {
      durability = (durability - impulse) max 0
    }

    override def healthPercent: Float = MathUtils.clamp(durability / maxDurability, 0, 1)

    override final def clone(): Cell = super.clone().asInstanceOf[Self]
  }

  class Hull(maxDurability: Float) extends Cell(maxDurability) {
    override type Self = this.type
  }

  class Thruster(val maxPower: Float, maxDurability: Float) extends Cell(maxDurability) {
    var active = false
    override type Self = this.type
  }

  object ShipRenderer {
    /**
     * Renders the given cell using ShapeRenderer
     * Assumes that cell center is 0.0 and cell occupies square with
     * width and height of 1 (one)
     *
     * @param shape
     */
    def render(shape: ShapeRenderer, cell: Cell): Unit = {
      cell match {
        case h: Hull =>
          shape.setColor(Color.LIGHT_GRAY)
          shape.set(ShapeType.Filled)
          shape.rect(-0.5F, -0.5F, 1, 1)
        case t: Thruster =>
          shape.setColor(new Color(0.5F, 0, 0, 1F))
          shape.set(ShapeType.Filled)
          shape.triangle(0, 0.5F, 0.5f, -0.5F, -0.5F, -0.5F)

          if (t.active) {
            shape.setColor(Color.RED)
            shape.triangle(0.3f, -0.5f, 0, -3f, -0.3f, -0.5f)
          }
      }
    }
  }

  object ShipPhysics {

    /**
     * Returns fixture def for rectangle cell of 1x1 size pointing upwards
     *
     * @param cell
     * @return
     */
    def createFixture(x: Float, y: Float, cell: Cell): FixtureDef = {
      val f = new FixtureDef
      f.density = 1
      cell match {
        case h: Hull =>
          val p = new PolygonShape()
          p.setAsBox(0.5f, 0.5f, (x, y), 0)
          f.shape = p
        case t: Thruster =>
          val p = new PolygonShape()
          p.set(Array[Vector2]((0, 0.5), (-0.5, -0.5), (0.5, -0.5)).map {
            case v => v.cpy().add(x, y)
          })
          f.shape = p
      }
      f
    }
  }

}