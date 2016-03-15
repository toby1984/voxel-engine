package de.codesourcery.voxelengine.engine;

/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;

import de.codesourcery.voxelengine.model.Player;

public class PlayerController extends InputAdapter 
{
    private static final Vector3 tmpV1 =new Vector3();
    
    public static final float ROTATION_ANGLE = 1.5f*360f;
    
    public static final float TRANSLATION_UNITS = 8f; 
    
    public static final int FORWARD = Keys.W;
    public static final int BACK = Keys.S;
    
    public static final int LEFT = Keys.A;
    public static final int RIGHT = Keys.D;
    
    public static final int ROTATE_RIGHT = Keys.E;
    public static final int ROTATE_LEFT = Keys.Q;
    
    /**
     * When the user keeps a mouse button pressed down, 
     * we'll register this as individual mouse clicks every X seconds. 
     */
    private static final float BUTTON_PRESS_REPEAT_INTERVAL_SECS = 0.3f;
    
    private int toolbarSlotPressed=-1;
    
    private boolean leftShiftPressed;
    private boolean forwardPressed;
    private boolean backwardPressed;
    
    private boolean leftPressed;
    private boolean rightPressed;
    
    private boolean rotateRightPressed;
    protected boolean rotateLeftPressed;
    
    private float buttonPressDuration;
    private boolean buttonPressRegistered;
    private boolean leftButtonPressed;
    private boolean rightButtonPressed;
    
    private final Vector3 tmp = new Vector3();

    private final Player player;
    
    private final Camera camera;
    
    public PlayerController (final Player player) 
    {
        this.player = player;
        camera = player.world.camera;
    }
    
    /**
     * Tells the controller that we've registered (and possibly acted upon)
     * any mouse button presses that were reported.
     */
    public void buttonPressRegistered() 
    {
        buttonPressRegistered = true;
    }
    
    /**
     * Returns whether the left mouse button has been pressed.
     * 
     * @return
     */
    public boolean leftButtonPressed() {
        return leftButtonPressed && ! buttonPressRegistered;
    }
    
    /**
     * Returns whether the right mouse button has been pressed.
     * 
     * @return
     */    
    public boolean rightButtonPressed() {
        return rightButtonPressed && ! buttonPressRegistered;
    }    
    
    /**
     * Returns whether either the left or right mouse button has been pressed.
     * 
     * @return
     */     
    public boolean buttonPressed() {
        return leftButtonPressed() || rightButtonPressed();
    }    

    /**
     * Apply user input.
     * 
     * @param delta
     */
    public void update (float delta) 
    {
        if ( buttonPressRegistered ) 
        {
            buttonPressDuration += delta;
            if ( buttonPressDuration >= BUTTON_PRESS_REPEAT_INTERVAL_SECS ) {
                buttonPressDuration = 0;
                buttonPressRegistered = false;
            }
        }
        
        if ( toolbarSlotPressed != -1 ) 
        {
            player.toolbar.setSelectedSlot( toolbarSlotPressed );
            toolbarSlotPressed = -1;
        } 
        
        if (rotateRightPressed) {
            player.rotate(-delta * ROTATION_ANGLE);
        } 
        else if (rotateLeftPressed) {
            player.rotate(delta * ROTATION_ANGLE);
        } else if ( Gdx.input.isCursorCatched() ) {
            mouseLook( Gdx.input.getDeltaX() , Gdx.input.getDeltaY() );
        }
        
        if ( leftPressed ) {
            tmp.set( camera.direction ).crs( camera.up ).scl( -delta * TRANSLATION_UNITS );
            if ( ! Player.CAMERA_MODE_FLYING) {
                tmp.y = 0;
            }
            player.translate( tmp );
        } else if ( rightPressed ) {
            tmp.set( camera.direction ).crs( camera.up ).scl( delta * TRANSLATION_UNITS );
            if ( ! Player.CAMERA_MODE_FLYING) {
                tmp.y = 0;
            }
            player.translate( tmp );
        }
        if (forwardPressed) 
        {
            tmp.set(camera.direction).scl(delta * TRANSLATION_UNITS);
            if ( ! Player.CAMERA_MODE_FLYING) {
                tmp.y = 0;
            }
            player.translate(tmp);
        } else if (backwardPressed) {
            tmp.set(camera.direction).scl(-delta * TRANSLATION_UNITS);
            if ( ! Player.CAMERA_MODE_FLYING) {
                tmp.y = 0;
            }
            player.translate( tmp );
        }
    }
    
    protected void mouseLook(int dx,int dy) 
    {
        final float deltaX = dx / (float) Gdx.graphics.getWidth();
        final float deltaY = dy / (float) Gdx.graphics.getHeight();

        tmpV1.set(camera.direction).crs(camera.up).y = 0f;

        // rotation around X axis
        final float rotXAxis = -deltaY * ROTATION_ANGLE;
        player.rotateAround(camera.position, tmpV1.nor(), rotXAxis);

        // rotation around Y axis
        final float rotYAxis = deltaX * -ROTATION_ANGLE;
        player.rotateAround(camera.position, Vector3.Y, rotYAxis);        
    }
    
    @Override
    public boolean touchDown (int screenX, int screenY, int pointer, int button) 
    {
        if ( ! Gdx.input.isCursorCatched() ) 
        {
            return true;
        }
        System.out.println("touchDown: "+button);
        // note: I don't register pressing both buttons at the same time because it makes no sense (create block + delete block ?)
        if ( button == Buttons.LEFT && ! (leftButtonPressed || rightButtonPressed ) ) {
            leftButtonPressed = true;
            buttonPressDuration = 0;
        } else if ( button == Buttons.RIGHT && ! (rightButtonPressed || leftButtonPressed ) ) {
            rightButtonPressed = true;
            buttonPressDuration = 0;
        }        
        return true;
    }
    
    @Override
    public boolean touchUp (int screenX, int screenY, int pointer, int button) 
    {
        if ( ! Gdx.input.isCursorCatched() ) 
        {
            if ( button == Buttons.LEFT ) {
                Gdx.input.setCursorCatched( true );
            }
            return true;
        }
        if ( button == Buttons.LEFT ) {
            leftButtonPressed = false;
            buttonPressDuration = 0;
        } 
        else if ( button == Buttons.RIGHT ) 
        {
            rightButtonPressed = false;
            buttonPressDuration = 0;
        }
        return true;
    }

    @Override
    public boolean touchDragged (int screenX, int screenY, int pointer) {
        return true;
    }

    @Override
    public boolean scrolled (int amount) {
        return true;
    }

    @Override
    public boolean keyDown (int keycode) 
    {
    	if ( keycode == Keys.SHIFT_LEFT ) {
    		leftShiftPressed = true;
    	} else if ( keycode == Keys.ESCAPE ) {
            Gdx.input.setCursorCatched( false );
        } else if (keycode == LEFT ) {
            leftPressed = true;
        } else if ( keycode == RIGHT ) {
            rightPressed = true;
        } else if (keycode == FORWARD) {
            forwardPressed = true;
        } else if (keycode == BACK) {
            backwardPressed = true;
        } else if (keycode == ROTATE_RIGHT) {
            rotateRightPressed = true;
        } else if (keycode == ROTATE_LEFT) {
            rotateLeftPressed = true;
        } else if ( keycode == Keys.NUM_1 ) {
            toolbarSlotPressed = 0;
        } else if ( keycode == Keys.NUM_2 ) {
            toolbarSlotPressed = 1;
        } else if ( keycode == Keys.NUM_3 ) {
            toolbarSlotPressed = 2;
        }
        return true;
    }

    @Override
    public boolean keyUp (int keycode) 
    {
    	if ( keycode == Keys.SHIFT_LEFT ) {
    		leftShiftPressed = false;
    	} else if (keycode == LEFT ) {
            leftPressed = false;
        } else if ( keycode == RIGHT ) {
            rightPressed = false;
        } else if (keycode == FORWARD) {
            forwardPressed = false;
        } else if (keycode == BACK) {
            backwardPressed = false;
        } else if (keycode == ROTATE_RIGHT) {
            rotateRightPressed = false;
        } else if (keycode == ROTATE_LEFT) {
            rotateLeftPressed = false;
        } else if ( keycode == Keys.NUM_1 || keycode == Keys.NUM_2 || keycode == Keys.NUM_3 ) {
            toolbarSlotPressed = -1;
        }
        return true;
    }
}