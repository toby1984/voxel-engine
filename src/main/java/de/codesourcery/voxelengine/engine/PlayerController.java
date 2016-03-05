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
    
    public static final float TRANSLATION_UNITS = 4f; 
    
    public static final int FORWARD = Keys.W;
    public static final int BACK = Keys.S;
    
    public static final int LEFT = Keys.A;
    public static final int RIGHT = Keys.D;
    
    public static final int ROTATE_RIGHT = Keys.E;
    public static final int ROTATE_LEFT = Keys.Q;
    
    private boolean forwardPressed;
    private boolean backwardPressed;
    
    private boolean leftPressed;
    private boolean rightPressed;
    
    private boolean rotateRightPressed;
    protected boolean rotateLeftPressed;
    
    private final Vector3 tmp = new Vector3();

    private final Player player;
    
    private final Camera camera;
    
    public PlayerController (final Player player) 
    {
        this.player = player;
        camera = player.world.camera;
    }

    public void update (float delta) 
    {
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
    public boolean touchDown (int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp (int screenX, int screenY, int pointer, int button) 
    {
        if ( button == Buttons.LEFT && ! Gdx.input.isCursorCatched() ) 
        {
            Gdx.input.setCursorCatched( true );
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
        if ( keycode == Keys.ESCAPE ) {
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
        }
        return true;
    }

    @Override
    public boolean keyUp (int keycode) 
    {
        if (keycode == LEFT ) {
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
        }
        return true;
    }
}