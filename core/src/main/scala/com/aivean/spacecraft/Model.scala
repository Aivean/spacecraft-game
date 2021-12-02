package com.aivean.spacecraft

object Model {

  trait Destructible {
    def destroyed: Boolean
    def detached: Boolean
    def damage(impulse: Float): Unit
    def healthPercent: Float
  }
}