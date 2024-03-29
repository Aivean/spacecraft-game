package com.aivean.spacecraft

import com.aivean.StageScreen
import com.aivean.spacecraft.GameScreen.{camera, font, normalProjection, spriteBatch}
import com.aivean.spacecraft.Ship.{Cell, Hull, Laser, Railgun, Thruster}
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.Pixmap.Format
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.graphics.{Color, GL20, Pixmap, Texture}
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.ui.{Image, Skin, Table, TextButton}
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent
import com.badlogic.gdx.utils.Align
import com.gdxscala._

object ConstructorScreen extends StageScreen {

  private val shape = new ShapeRenderer()
  private val cellSize = 30

  sealed trait CellType {
    def name: String

    def cell: Cell

    def cellFactory: Cell = cell.clone()
  }

  object CellType {
    case object HULL extends CellType {
      override val name: String = "Hull"
      override val cell: Cell = new Hull(100)
    }

    case object THRUSTER extends CellType {
      override val name: String = "Thruster"
      override val cell: Cell = new Thruster(20, 30)
    }

    case object LASER extends CellType {
      override val name: String = "Laser"
      override val cell: Cell = new Laser(1f, 30)
    }

    case object RAILGUN extends CellType {
      override val name: String = "Railgun"
      override val cell: Cell = new Railgun(0.3f, 50, 30)
    }

    val values = Seq(HULL, THRUSTER, LASER, RAILGUN)
  }

  var cursor: Option[CellType] = Some(CellType.HULL)

  var cells: Map[(Int, Int), Ship.Cell] = Map(
    (-1, 2) -> CellType.LASER,
    (0, 2) -> CellType.LASER,
    (-2, 0) -> CellType.RAILGUN,
    (1, 0) -> CellType.RAILGUN,
    (-1, -1) -> CellType.HULL,
    (-1, -2) -> CellType.HULL,
    (-1, -3) -> CellType.THRUSTER,
    (-1, 0) -> CellType.HULL,
    (-1, 1) -> CellType.HULL,
    (-2, -1) -> CellType.HULL,
    (-2, -2) -> CellType.THRUSTER,
    (0, -1) -> CellType.HULL,
    (0, -2) -> CellType.HULL,
    (0, -3) -> CellType.THRUSTER,
    (0, 0) -> CellType.HULL,
    (0, 1) -> CellType.HULL,
    (1, -1) -> CellType.HULL,
    (1, -2) -> CellType.THRUSTER
  ).mapValues(_.cellFactory)


  private val skin = {
    val skin = new Skin()

    def getTexture(color: Color) = {
      // Generate a 1x1 white texture and store it in the skin named "white".
      val pixmap = new Pixmap(1, 1, Format.RGBA8888)
      pixmap.setColor(color)
      pixmap.fill()
      new Texture(pixmap)
    }

    skin.add("white", getTexture(Color.WHITE))
    //    skin.add("red", getTexture(Color.RED))
    skin.add("dark_gray", getTexture(Color.DARK_GRAY))

    // Store the default libGDX font under the name "default".
    skin.add("default", new BitmapFont)
    val textButtonStyle = new TextButton.TextButtonStyle
    textButtonStyle.up = skin.newDrawable("white", Color.DARK_GRAY)
    textButtonStyle.down = skin.newDrawable("white", Color.DARK_GRAY)
    textButtonStyle.checked = skin.newDrawable("white", Color.BLUE)
    textButtonStyle.over = skin.newDrawable("white", Color.LIGHT_GRAY)
    textButtonStyle.font = skin.getFont("default")
    textButtonStyle.font.setUseIntegerPositions(false)
    skin.add("default", textButtonStyle)

    val labelStyle = new LabelStyle(skin.getFont("default"), Color.WHITE)
    skin.add("default", labelStyle)
    skin
  }

  private val (table, labels) = {
    val table = new Table(skin)
    table.setBackground(skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 0.9f)))
    addActor(table)

    def hr() = {
      table.add(new Image(skin.newDrawable("white", Color.BLACK)))
        .size(table.getWidth, 2)
        .pad(10)

      table.row()
    }

    table.pad(40).padRight(20)
    table.setSize(180, 160 + CellType.values.size * 40)

    table.add("Component Library").pad(10).align(Align.center)
    table.row()
    hr()

    val labels: Map[Option[CellType], Actor] = {
      (CellType.values.map { ct =>
        ct.name -> Some(ct)
      } :+ ("Eraser" -> None))
        .map {
          case (text, cellType) =>
            val button = new TextButton(text, skin)
            button.pad(10).padLeft(40).padRight(20)

            button.addListener(new ChangeListener() {
              override def changed(event: ChangeEvent, actor: Actor): Unit = {
                cursor = cellType
                event.cancel()
              }
            })
            val actor = table.add(button).align(Align.left).getActor
            table.row()
            cellType -> actor
        }.toMap
    }

    hr()

    val engageButton = new TextButton("Engage! [Enter]", skin).pad(10)
    table.add(engageButton)
    engageButton.addListener(new ChangeListener {
      override def changed(event: ChangeEvent, actor: Actor): Unit = {
        event.cancel()
        SpaceCraftGame.game()
      }
    })

    (table, labels)
  }

  override def resize(width: Int, height: Int): Unit = {
    super.resize(width, height)
    table.setPosition(getWidth / 2 - table.getWidth, getHeight / 2 - table.getHeight)
  }

  override def render(delta: Float): Unit = {
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

    drawGrid()
    drawCells()

    val tableHit = {
      val (tx, ty) = table.screenToLocalCoordinates(Gdx.input.getX, Gdx.input.getY).tuple
      table.hit(tx, ty, false) != null
    }

    if (Gdx.input.isTouched && !tableHit) {
      val key: (Int, Int) = getCursorCellKey

      cursor match {
        case Some(ct) => cells += key -> ct.cellFactory
        case None => cells = cells - key
      }
    }

    if (!tableHit) {
      drawCellCoords()
      drawCursor()
    }

    if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
      SpaceCraftGame.game()
    }

    act(Gdx.graphics.getDeltaTime)
    draw()

    drawLabels()
  }

  def getCursorCellKey = {
    val w = getViewport.unproject(Gdx.input.getX, Gdx.input.getY)
    val key = (math.floor(w.x / cellSize).toInt, math.floor(w.y / cellSize).toInt)
    key
  }

  def drawCells(): Unit = {
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

  def drawCellType(c: Option[CellType]): Unit = {
    c match {
      case Some(c) =>
        Ship.ShipRenderer.render(shape, c.cellFactory)
      case None =>
        // draw cross (eraser)
        shape.setColor(Color.RED)
        shape.rectLine(-0.5f, -0.5f, 0.5f, 0.5f, 0.2f)
        shape.rectLine(-0.5f, 0.5f, 0.5f, -0.5f, 0.2f)
    }
  }

  def drawCursor(): Unit = {
    shape.begin(ShapeType.Filled)
    shape.setAutoShapeType(true)
    shape.identity()
    shape.setProjectionMatrix(getCamera.combined)
    val coords = getViewport.unproject(Gdx.input.getX, Gdx.input.getY)
    shape.translate(coords.x, coords.y, 0)
    shape.scale(cellSize, cellSize, 1)
    drawCellType(cursor)
    shape.identity()
    shape.end()
  }

  def drawCellCoords(): Unit ={
    spriteBatch.begin()
    spriteBatch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, camera.viewportWidth, camera.viewportHeight))
    font.setColor(Color.LIGHT_GRAY)
    font.draw(spriteBatch, getCursorCellKey.toString(), 0, camera.viewportHeight)
    spriteBatch.end()
  }

  def drawLabels(): Unit = {
    shape.begin(ShapeType.Filled)
    shape.setAutoShapeType(true)
    shape.setProjectionMatrix(getCamera.combined)

    labels.foreach {
      case (cellType, imgActor) =>
        shape.identity()
        val coords = getViewport.unproject(stageToScreenCoordinates(
          imgActor.localToStageCoordinates(imgActor.getHeight / 2, imgActor.getHeight / 2)
        ))

        shape.translate(coords.x, coords.y, 0)
        shape.scale(cellSize * 0.8f, cellSize * 0.8f, 1)
        drawCellType(cellType)
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
