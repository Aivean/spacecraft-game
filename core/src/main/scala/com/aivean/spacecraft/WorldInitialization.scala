package com.aivean.spacecraft

import com.aivean.spacecraft.Ship.Cell
import com.badlogic.gdx.math.{Intersector, Vector2}
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType
import com.badlogic.gdx.physics.box2d.{BodyDef, Fixture, PolygonShape, World}
import com.gdxscala._

import scala.collection.mutable
import scala.util.Random

object WorldInitialization {

  def createAsteroids(implicit world:World): Unit = {
    (0 to 100).foreach { _ =>
      val r = Random.nextFloat() * 5 + 1.5f
      def pos(n:Int): Option[Vector2] = if (n <= 0) None else {
        val x = Random.nextFloat() * 100 - 50
        val y = Random.nextFloat() * 100 - 50

        if (!world.occupied(x - r, y - r, x + r, y + r) &&
          (x < -10 || x > 10 || y < -10 || y > 10)
        ) Some((x, y)) else pos(n - 1)
      }

      pos(100) match {
        case Some(p) =>
          val body = world.createBody {
            val bd = new BodyDef
            bd.`type` = BodyType.DynamicBody
            bd.allowSleep = true
            bd.linearDamping = 0.1f
            bd.angularDamping = 0.1f
            bd.angularVelocity = Random.nextFloat() - 0.5f
            bd.position.set(p)
            bd
          }

          def genRing(sAngleDeg:Float, sections:Int, outerR:Float, innerR:Float) = {
            val a = 360f / sections
            (0 until sections).map { s =>
              Array(
                (a * s, innerR),
                (a * s, outerR),
                (a * (s + 1), outerR),
                (a * (s + 1), innerR)
              ).map {
                case (a, r) => Vector2.X.cpy().scl(r).rotate(sAngleDeg + a)
              }
            }
          }

          val h = 1f
          (0.1f to (r, h)).sliding(2).collect {
            case Seq(r1, r2) =>
              val n = math.ceil(2 * math.Pi * r2 / h).toInt
              genRing(Random.nextFloat * 360, n, r2, r1)
          }.flatten.foreach {
            ring =>
              val s = new PolygonShape()
              s.set(ring)
              val f = body.createFixture(s, 1f)
              f.setUserData(new Cell(10) {
              })
              s.dispose()
          }

        case _ =>
      }
    }
  }

  def createTorpedo(p:Vector2, angle:Float)(implicit world:World): Unit = {
    val body = world.createBody {
      val bd = new BodyDef
      bd.`type` = BodyType.DynamicBody
      bd.allowSleep = true
      bd.bullet = true
      bd.linearDamping = 0.1f
      bd.angularDamping = 0.1f
      bd.linearVelocity.set((0, 50f).rotateRad(angle))
      bd.angle = angle
      bd.position.set(p)
      bd
    }

    val s = new PolygonShape()
    s.setAsBox(0.1f, 0.5f)
    val f = body.createFixture(s, 2f)
    f.setUserData(new Cell(50) {})
    s.dispose()
  }
}
