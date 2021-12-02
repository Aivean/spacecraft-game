package com

import archery.{Box, Entry, RTree}
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.{Intersector, Rectangle, Vector2, Vector3}
import com.badlogic.gdx.physics.box2d._

import scala.collection.mutable

package object gdxscala {

  type GdxArray[T] = com.badlogic.gdx.utils.Array[T]

  @inline implicit def tupleToVector2[P1, P2](t: (P1, P2))(implicit n1: Numeric[P1], n2: Numeric[P2]): Vector2 =
    new Vector2(n1.toFloat(t._1), n2.toFloat(t._2))

  @inline implicit def tupleToVector3[P1, P2, P3](t: (P1, P2, P3))
                                                 (implicit n1: Numeric[P1], n2: Numeric[P2], n3: Numeric[P3]): Vector3 =
    new Vector3(n1.toFloat(t._1), n2.toFloat(t._2), n3.toFloat(t._3))


  @inline implicit def vector3ToVector2(v: Vector3): Vector2 = new Vector2(v.x, v.y)

  @inline implicit def vector2ToVector3(v: Vector2): Vector3 = new Vector3(v.x, v.y, 0)

  implicit def iterableAsGdxArray[T](it: TraversableOnce[T]): GdxArray[T] = it match {
    case w: GdxWrappedArray[T] => w.array
    case _ => val r = new GdxArray[T]()
      it.foreach(el => r.add(el))
      r
  }

  @inline implicit def wrapGdxArray[T](arr: GdxArray[T]): GdxWrappedArray[T] = new GdxWrappedArray(arr)

  class GdxWrappedArray[T](val array: GdxArray[T]) extends mutable.AbstractSeq[T]
  with mutable.IndexedSeq[T]
  with mutable.ArrayLike[T, GdxWrappedArray[T]]
  with mutable.Builder[T, GdxWrappedArray[T]] {

    def this() = this(new GdxArray[T]())

    override def update(idx: Int, elem: T): Unit = array.set(idx, elem)

    override def length: Int = array.size

    override def apply(idx: Int): T = array.get(idx)

    override def clone(): GdxWrappedArray[T] = new GdxWrappedArray(new GdxArray[T](array))

    override protected[this] def newBuilder: mutable.Builder[T, GdxWrappedArray[T]] = new GdxWrappedArray[T]()

    override def +=(elem: T): this.type = {
      array.add(elem)
      this
    }

    override def result(): GdxWrappedArray[T] = this

    override def clear(): Unit = {
      array.clear()
    }
  }

  def centeroid(m: collection.Seq[Vector2]): Vector2 = {
    val (x, y) = m.map(v => (v.x, v.y)).unzip
    new Vector2(x.sum / m.size, y.sum / m.size)
  }

  @inline implicit class RichBox2DShape(val s: Shape) extends AnyVal {
    private def fetchVertices(n: Int, f: (Int, Vector2) => Unit) = (0 until n).map {
      i => val v = new Vector2()
        f(i, v)
        v
    }

    def vertices = s match {
      case s: CircleShape => Seq(s.getPosition.cpy())
      case s: PolygonShape =>
        fetchVertices(s.getVertexCount, s.getVertex)
      case s: ChainShape =>
        fetchVertices(s.getVertexCount, s.getVertex)
      case s: EdgeShape =>
        fetchVertices(2, (i, v) => collection.Seq(s.getVertex1 _, s.getVertex2 _)(i)(v))
    }

    def aabb = s match {
      case s:CircleShape =>
        new Rectangle(s.getPosition.x - s.getRadius,
          s.getPosition.y - s.getRadius, s.getRadius * 2, s.getRadius * 2)
      case _ =>
      val (vx, vy) = vertices.map(v => (v.x, v.y)).unzip
      val (vxMin, vyMin) = (vx.min, vy.min)
      val (w, h) = (vx.max - vxMin, vy.max - vyMin)
      new Rectangle(vx.min, vy.min, w, h)
    }

    def center = vertices match {
      case collection.Seq() => new Vector2()
      case collection.Seq(p) => p
      case x => centeroid(x)
    }
  }

  @inline implicit class RichWorld(val world: World) extends AnyVal {

    def findFixtures(x: Float, y: Float): TraversableOnce[Fixture] =
      findFixtures(x, y, x, y, _.testPoint(x,y))

    def findFixtures(x1: Float, y1: Float, x2:Float, y2:Float): TraversableOnce[Fixture] =
      findFixtures(x1, y1, x2, y2, _ => true)

    def findFixtures(x1: Float, y1: Float, x2:Float, y2:Float,
                     predicate:Fixture => Boolean): TraversableOnce[Fixture] = {
      val arr = new GdxWrappedArray[Fixture]()
      world.QueryAABB(new QueryCallback {
        override def reportFixture(fixture: Fixture): Boolean = {
          if (predicate(fixture)) {
            arr += fixture
          }
          true
        }
      }, x1, y1, x2, y2)
      arr
    }

    def rayClosest(p1:Vector2, p2:Vector2, f:Fixture => Boolean):Option[(Fixture, Vector2)] = {
      val res = new GdxArray[(Float, Fixture, Vector2)]
      world.rayCast(new RayCastCallback {
        override def reportRayFixture(fixture: Fixture, point: Vector2, normal: Vector2, fraction: Float): Float = {
          if (f(fixture)) {
            res += ((fraction, fixture, point.cpy()))
            fraction
          } else -1
        }
      }, p1, p2)
      res.sortBy(_._1).headOption.map {
        case (_, f, p) => (f, p)
      }
    }

    def fixtures:TraversableOnce[Fixture] = {
      val arr = new GdxArray[Fixture]
      world.getFixtures(arr)
      arr
    }

    def bodies:TraversableOnce[Body] = {
      val arr = new GdxArray[Body]
      world.getBodies(arr)
      arr
    }

    def occupied(x1:Float, y1:Float, x2:Float, y2:Float) = {
      var f = false
      world.QueryAABB(new QueryCallback {
        override def reportFixture(fixture: Fixture): Boolean = {
          f = true
          true
        }
      }, x1, y1, x2, y2)
      f
    }
  }

  @inline implicit class RichShapeRenderer(val r:ShapeRenderer) extends AnyVal {
    def filledPoly(vertices:collection.Seq[Vector2]) = {
      val h = vertices.head
      vertices.tail.sliding(2).foreach {
        case collection.Seq(a, b) =>
          r.triangle(h.x, h.y, a.x, a.y, b.x, b.y)
      }
    }
  }

  def splitFixtures(fixtures:Seq[Fixture]):Set[Set[Fixture]] = {
    val idx = fixtures.map {
      f =>
        val r = f.getShape.aabb
        (f, Box(r.getX, r.getY, r.getX + r.getWidth, r.getY + r.getHeight))
    }.toMap

    val tree = RTree[Fixture]().insertAll(idx.map {
      case (f, b) => Entry(b, f)
    })

    def vArr(f:Fixture) = f.getShape.vertices.flatMap(f => Seq(f.x, f.y)).toArray
    def touch(a:Fixture)(b:Fixture) = Intersector.overlapConvexPolygons(vArr(a), vArr(b), null)
    def neighbours(f:Fixture) = tree.searchIntersection(idx(f)).map(_.value).toSet

    type FSet = Set[Fixture]

    def bfs(q:List[Fixture], marked:FSet, accum:List[Fixture]):List[Fixture] = q match {
      case Nil => accum
      case h::tail =>
        val t = neighbours(h).diff(marked).filter(touch(h))
        bfs(t.toList ::: tail.filterNot(t.contains), t ++ marked, t.toList ::: accum)
    }

    def rec(unmarked:FSet, accum:Set[FSet]):Set[FSet] = if (unmarked.isEmpty) accum else {
      val component = bfs(List(unmarked.head), Set.empty, Nil).toSet
      rec(unmarked -- component, accum + component)
    }

    rec(fixtures.toSet, Set.empty)
  }

  @inline implicit class RichVector2(val v:Vector2) extends AnyVal {
    @inline def tuple = (v.x, v.y)
  }
}