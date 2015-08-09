package com.aivean

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, Screen}
import com.badlogic.gdx.scenes.scene2d.Stage

class StageScreen extends Stage(new ScreenViewport()) with Screen {

  override def render(delta: Float): Unit = {
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    act(Gdx.graphics.getDeltaTime)
    draw()
  }

  override def show(): Unit = {
    Gdx.input.setInputProcessor(this)
  }

  override def hide(): Unit = {
    Gdx.input.setInputProcessor(null)
  }

  override def resize(width: Int, height: Int): Unit = {
    getViewport.update(Gdx.graphics.getWidth, Gdx.graphics.getHeight, false)
    getCamera.position.set(0, 0, 0)
  }

  override def resume(): Unit = {}

  override def pause(): Unit = {}
}
