package com.aivean.spacecraft

import com.aivean.spacecraft.Model.Destructible
import com.aivean.spacecraft.Ship.{Cell, CellUserData, Thruster}
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.graphics.{Color, GL20, OrthographicCamera}
import com.badlogic.gdx.math.{MathUtils, Matrix4, Vector2, Vector3}
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType
import com.badlogic.gdx.physics.box2d._
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.{Gdx, ScreenAdapter}
import com.gdxscala._
import net.dermetfan.gdx.physics.box2d

import scala.collection.mutable
import scala.util.Random

object GameScreen extends ScreenAdapter {

  var worldTime: Float = 0
  implicit val world = new World(new Vector2(0, 0), true)
  val debugRenderer = //new Box2DDebugRenderer(true, true, false, true, true, true)
    new Box2DDebugRenderer()
  val shapes = new ShapeRenderer()
  val spriteBatch = new SpriteBatch()
  val skin = new Skin(Gdx.files.internal("skin/uiskin.json"))
  val font = skin.getFont("default-font")
  font.getData.setScale(0.05f)
  font.setUseIntegerPositions(false);

  val stars: Array[(Float, Float)] = (1 to 4000).map {
    _ => (Random.nextFloat() * 200 - 100, Random.nextFloat() * 200 - 100)
  }.toArray

  def getDestructible(f: Fixture): Option[Destructible] = f.getUserData match {
    case null => None
    case c: CellUserData[Cell] => c.getDestructible
    case d: Destructible => Some(d)
    case _ => None
  }

  WorldInitialization.createAsteroids(world)

  case class Break(p: Seq[Vector2], f: Fixture, force: Float)

  val breaks = new GdxWrappedArray[Break]()

  world.setContactListener(new ContactListener {
    override def postSolve(contact: Contact, impulse: ContactImpulse): Unit = {
      val p = contact.getWorldManifold.getPoints
      val i = impulse.getNormalImpulses.map(math.abs).sum

      for (f <- Array(contact.getFixtureA, contact.getFixtureB); d <- getDestructible(f)) {
        breaks += Break(p, f, i)
      }
    }

    override def endContact(contact: Contact): Unit = {}

    override def beginContact(contact: Contact): Unit = {}

    override def preSolve(contact: Contact, oldManifold: Manifold): Unit = {}
  })

  val camera = new OrthographicCamera(48, 48f * Gdx.graphics.getHeight / Gdx.graphics.getWidth)
  camera.position.set(0, 16, 0)
  //Init world

  val ship = new {
    val body = {
      val body = world.createBody {
        val bodyDef = new BodyDef
        bodyDef.`type` = BodyType.DynamicBody
        bodyDef.allowSleep = false
        bodyDef.bullet = true
        bodyDef.linearDamping = 0.6f
        bodyDef.angularDamping = 0.6f
        bodyDef
      }
      body
    }

    var torpedoTimer = 0f

    var meta: Ship = _

    val thrusters = new {
      var all: Seq[CellUserData[Thruster]] = _

      private def lr = {
        val centerX = body.getLocalCenter.x
        all.map {
          case ud@CellUserData(x, y, t) =>
            (x > centerX, CellUserData(x, y, t))
        }.partition(_._1) match {
          case (l, r) => (l.map(_._2), r.map(_._2))
        }
      }

      def left = lr._1

      def right = lr._2
    }

    def forwardVector = Vector2.Y.cpy().rotateRad(body.getAngle)
  }

  override def show(): Unit = {
    ship.body.getFixtureList.toList.foreach(f => f.getBody.destroyFixture(f))

    for (f <- world.fixtures; d <- Option(f.getUserData)) {
      d match {
        case CellUserData(_, _, c) =>
          f.setUserData(new Cell(c.maxDurability) {
            durability = c.durability
          })
        case _ =>
      }
    }

    ship.meta = new Ship(ConstructorScreen.cells.toList.map {
      case ((x, y), c) => CellUserData(x, y, c)
    })

    ship.meta.cells.foreach {
      case CellUserData(x, y, c) =>
        c.durability = c.maxDurability
        val f = Ship.ShipPhysics.createFixture(x, y, c)
        val fx = ship.body.createFixture(f)
        fx.setUserData(CellUserData(x, y, c))

        f.shape.dispose()
    }

    ship.thrusters.all = ship.meta.cells.collect {
      case ud: CellUserData[Thruster] if ud.cell.isInstanceOf[Thruster] => ud
    }
  }

  override def render(delta: Float): Unit = {
    stepWorld(delta)

    camera.position.x = MathUtils.clamp(camera.position.x,
      ship.body.getPosition.x - 10, ship.body.getPosition.x + 10)
    camera.position.y = MathUtils.clamp(camera.position.y,
      ship.body.getPosition.y - 5, ship.body.getPosition.y + 5)

    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    camera.update()

    drawStars()

    debugRenderer.render(world, camera.combined)

    drawShip()
    drawHealth2()
    drawHealth()
    drawFps()
    drawHints()

    if (Gdx.input.isTouched) {
      val p = camera.unproject((Gdx.input.getX, Gdx.input.getY, 0))
      val sp = ship.body.getWorldCenter
      world.rayClosest(sp, p, _.getBody != ship.body).foreach {
        case (f, p) =>
          shapes.setProjectionMatrix(camera.combined)
          shapes.begin(ShapeType.Line)
          shapes.setColor(Color.RED)
          shapes.identity()

          shapes.setColor(Color.YELLOW)
          shapes.line(sp, p)
          shapes.circle(p.x, p.y, 0.1f, 6)
          breaks += Break(Seq(p), f, 1f)
          shapes.end()
      }
    }

    processBreaks(delta)

    ship.thrusters.all.foreach(_.cell.active = false)
    if (Gdx.input.isKeyPressed(Keys.UP) || Gdx.input.isKeyPressed(Keys.W)) {
      ship.thrusters.all.foreach(thrust)
    } else if (Gdx.input.isKeyPressed(Keys.LEFT) || Gdx.input.isKeyPressed(Keys.A)) {
      ship.thrusters.left.foreach(thrust)
    } else if (Gdx.input.isKeyPressed(Keys.RIGHT) || Gdx.input.isKeyPressed(Keys.D)) {
      ship.thrusters.right.foreach(thrust)
    }

    if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
      SpaceCraftGame.menu()
    }

    if (Gdx.input.isKeyPressed(Keys.SPACE) && ship.torpedoTimer <= 0f) {
      val cx = ship.body.getLocalCenter.x
      ship.body.getFixtureList.map(_.getShape.center).sortBy(p => (-p.y, math.abs(cx - p.x))).headOption.foreach {
        p =>
          p.add(0, 1.1f)
          val w = ship.body.getWorldPoint(p)
          WorldInitialization.createTorpedo(w, ship.body.getAngle)
      }
      ship.torpedoTimer = 0.3f
    } else {
      ship.torpedoTimer = (ship.torpedoTimer - delta) max 0f
    }
  }

  private def normalProjection = new Matrix4().setToOrtho2D(0, 0, camera.viewportWidth, camera.viewportHeight)

  def drawFps(): Unit = {
    spriteBatch.begin()
    spriteBatch.setProjectionMatrix(normalProjection)
    font.setColor(Color.LIGHT_GRAY)
    font.draw(spriteBatch, "FPS=" + Gdx.graphics.getFramesPerSecond, 0, camera.viewportHeight)
    spriteBatch.end()
  }

  def drawHints(): Unit = {
    spriteBatch.begin()
    spriteBatch.setProjectionMatrix(normalProjection)
    font.setColor(Color.LIGHT_GRAY)
    font.draw(spriteBatch,
      "Arrows: move, Spacebar: railgun, Mouse: laser, Enter: dock", 0, font.getLineHeight)
    spriteBatch.end()
  }

  def drawStars(): Unit = {
    shapes.begin(ShapeType.Point)
    shapes.setColor(Color.WHITE)
    shapes.setProjectionMatrix(camera.combined)

    stars.foreach {
      case (x, y) if camera.frustum.pointInFrustum(x, y, 0) =>
        shapes.point(x, y, 0)
      case _ =>
    }
    shapes.end()
  }

  def processBreaks(d: Float): Unit = {
    shapes.setProjectionMatrix(camera.combined)
    shapes.begin(ShapeType.Line)
    shapes.setColor(Color.RED)
    shapes.identity()

    val splitCandidates = mutable.Set[Body]()

    def extractFixtures(fs: Traversable[Fixture]) = {
      val b = world.createBody(box2d.Box2DUtils.createDef(fs.head.getBody))
      fs.foreach { f =>
        box2d.Box2DUtils.clone(f, b, true)
        f.getBody.destroyFixture(f)
      }
      b
    }

    breaks.groupBy(_.f).foreach {
      case (f, bks) =>
        val force = bks.map(_.force).sum
        bks.flatMap(_.p).foreach(p =>
          shapes.circle(p.x, p.y, math.sqrt(force + 0.1f).toFloat min 1f)
        )
        for (d <- getDestructible(f)) {
          d.damage(force)
          val fixturesSize = f.getBody.getFixtureList.size
          if (d.destroyed) {
            if (fixturesSize <= 1) {
              splitCandidates -= f.getBody
              world.destroyBody(f.getBody)
            } else {
              f.getBody.destroyFixture(f)
              splitCandidates += f.getBody
            }
          } else if (d.detached && fixturesSize > 1) {
            extractFixtures(Seq(f))
            splitCandidates += f.getBody
          }
        }
    }
    shapes.end()
    breaks.clear()

    splitCandidates.map(b => splitFixtures(b.getFixtureList)).filter(_.size > 1).foreach(
      _.toList.sortBy(_.size).reverse.tail.foreach(extractFixtures)
    )
  }

  def drawCells(shapeType: ShapeType = ShapeType.Filled)(draw: (ShapeRenderer, CellUserData[Cell]) => Unit): Unit = {
    shapes.setProjectionMatrix(camera.combined)
    shapes.begin(ShapeType.Filled)
    shapes.setAutoShapeType(true)

    val p = ship.body.getPosition
    for (f <- ship.body.getFixtureList if f != null) {
      val ud@CellUserData(x, y, cell) = f.getUserData.asInstanceOf[CellUserData[Cell]]
      shapes.identity()
      shapes.translate(p.x, p.y, 0)
      shapes.rotate(0, 0, 1, MathUtils.radiansToDegrees * ship.body.getAngle)
      shapes.translate(x, y, 0)

      draw(shapes, ud)
    }
    shapes.identity()
    shapes.end()
  }

  def drawShip(): Unit = {
    drawCells() {
      (shape, cell) =>
        Ship.ShipRenderer.render(shape, cell.cell)
    }
  }

  def drawHealth(): Unit = {
    drawCells() {
      (shape, cell) =>
        shape.setColor(new Color(0, 0.5f, 0, 0.3f))
        shape.arc(0, 0, 0.3f, 0, 360f * cell.cell.healthPercent, 10)
    }
  }

  implicit val fixtureBuffer = new GdxArray[Fixture](1024)

  def drawHealth2(): Unit = {
    shapes.setProjectionMatrix(camera.combined)
    shapes.begin(ShapeType.Filled)
    shapes.setAutoShapeType(true)
    shapes.identity()

    val fixtures = {
      val (coordsX, coordsY) = Seq[Vector2](
        (0, 0), (Gdx.graphics.getWidth, 0), (Gdx.graphics.getWidth, Gdx.graphics.getHeight), (0, Gdx.graphics.getHeight))
        .map(x => x: Vector3).map(camera.unproject).map(x => (x.x, x.y)).unzip

      world.findFixtures(coordsX.min, coordsY.min, coordsX.max, coordsY.max)
    }

    for (f <- fixtures; d <- getDestructible(f) if f.getBody != ship.body) {
      val vs = f.getShape.vertices.map(f.getBody.getWorldPoint(_).cpy())
      if (vs.size >= 3 && vs.exists(v => camera.frustum.pointInFrustum(v.x, v.y, 0))) {
        shapes.setColor((1f - d.healthPercent) * 0.5f, d.healthPercent * 0.5f, 0, 0.3f)
        shapes.filledPoly(vs)
      }
    }
    shapes.identity()
    shapes.end()
  }

  def thrust(t: CellUserData[Thruster]): Unit = if (!t.cell.detached) {
    val p = ship.body.getWorldPoint((t.x, t.y))
    ship.body.applyForce(ship.forwardVector.scl(t.cell.maxPower), p, true)
    t.cell.active = true
  }

  def stepWorld(delta: Float): Unit = {
    val step = 1 / 60f
    worldTime += delta
    while (worldTime >= step) {
      world.step(step, 6, 2)
      worldTime -= step
    }
  }

  override def resize(width: Int, height: Int): Unit = {
    camera.viewportHeight = 48f * Gdx.graphics.getHeight / Gdx.graphics.getWidth
  }
}