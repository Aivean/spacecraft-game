package com.aivean

import com.badlogic.gdx.math.Vector3
import com.gdxscala

class PackageTest extends org.scalatest.FunSuite {

  test("gdxScala") {
    import gdxscala._

    val arr = new GdxArray[Vector3]()
    arr.add((1, 2, 3))

    val arr2: GdxArray[Vector3] = List[Vector3]((1, 2, 3))

    assert(arr.toSet === arr2.toSet)
  }
}
