package com.aivean.spacecraft

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.graphics.{Color, GL20, OrthographicCamera}
import com.badlogic.gdx.math.{MathUtils, Vector2}
import com.badlogic.gdx.physics.box2d
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType
import com.badlogic.gdx.physics.box2d._
import com.badlogic.gdx.physics.box2d.joints.{MouseJoint, MouseJointDef, RevoluteJoint, RevoluteJointDef}
import com.badlogic.gdx.{Game, Gdx, ScreenAdapter}
import com.gdxscala._

import scala.util.Random

object SpaceCraftGame extends Game {

  def instance = this

  def menu(): Unit = {
    setScreen(ConstructorScreen)
  }

  def game(): Unit = {
    setScreen(GameScreen)
  }

  override def create(): Unit = {
    menu()
  }
}
