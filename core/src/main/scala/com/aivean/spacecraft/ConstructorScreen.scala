package com.aivean.spacecraft

import com.aivean.StageScreen
import com.aivean.spacecraft.Ship.{Hull, Thruster}
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.gdxscala._

object ConstructorScreen extends StageScreen {

  val shape = new ShapeRenderer()
  val cellSize = 30

  var cells = Map[(Int, Int), Ship.Cell]()

  override def render(delta: Float): Unit = {
    super.render(delta)

    drawGrid()
    drawCells()

    if (Gdx.input.justTouched()) {
      val w = getViewport.unproject((Gdx.input.getX, Gdx.input.getY))
      val key = (math.floor(w.x / cellSize).toInt, math.floor(w.y / cellSize).toInt)

      val replace = cells.get(key) match {
        case Some(x:Thruster) =>
          None
        case Some(x:Hull) =>
          Some(new Thruster(20, 30))
        case None =>
          Some(new Hull(100))
      }

      replace match {
        case Some(x) => cells += (key -> x)
        case None => cells -= key
      }
    }

    if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
      SpaceCraftGame.game()
    }
  }

  def drawCells(): Unit ={
    shape.begin(ShapeType.Filled)
    shape.setAutoShapeType(true)
    shape.setProjectionMatrix(getCamera.combined)

    cells.foreach {
      case ((x, y), cell) =>
        shape.identity()
        shape.scale(cellSize, cellSize, 1)
        shape.translate(x + 0.5f, y + 0.5f, 0)
        Ship.ShipRenderer.render(shape, cell)
    }
    shape.identity()
    shape.end()
  }

  def drawGrid(): Unit = {
    shape.setProjectionMatrix(getCamera.combined)

    shape.begin(ShapeType.Line)
    shape.setColor(new Color(1f, 1f, 1f, 0.5f))
    ((-getWidth / cellSize / 2).toInt to (getWidth / cellSize / 2).toInt).foreach { x =>
      shape.line(x * cellSize, -getHeight / 2, x * cellSize, getHeight / 2)
    }
    ((-getHeight / cellSize / 2).toInt to (getHeight / cellSize / 2).toInt).foreach { y =>
      shape.line(-getWidth / 2, y * cellSize, getWidth / 2, y * cellSize)
    }

    shape.setColor(Color.RED)
    shape.line(-5, 0, 5, 0)
    shape.line(0, -5, 0, 5)

    shape.end()
  }

  override def dispose(): Unit = {
    super.dispose()
    shape.dispose()
  }
}
