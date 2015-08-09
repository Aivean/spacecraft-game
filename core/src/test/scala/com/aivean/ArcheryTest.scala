package com.aivean

import archery.{Box, Entry, RTree}
import org.scalatest.FunSuite

class ArcheryTest extends FunSuite {
  test("R-tree happy path") {

    val e1 = Entry(Box(0, 0, 1, 1), "0")
    val tree = RTree(
      e1
    )

    assert(tree.searchIntersection(Box(0.5f, 0.5f, 0.5f, 0.6f)) === Seq(e1))
    assert(tree.searchIntersection(Box(1.1f, 0.5f, 1.6f, 0.6f)) === Seq())
  }
}
