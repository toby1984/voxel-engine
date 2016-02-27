package de.codesourcery.voxelengine.engine;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

public class Player 
{
    public static float PLAYER_HEIGHT = 2*World.WORLD_CHUNK_BLOCK_SIZE;
    public static float MAX_VELOCITY= 5f;
    
    // position of player feet
    private final Vector3 playerPosition = new Vector3();
    private final Vector3 cameraPosition = new Vector3();
    
    public final Vector3 velocity= new Vector3();
    
    public final Vector3 up = new Vector3(0,1,0);
    public final Vector3 direction = new Vector3(0,0,-1); // normal vector
    
    private final Vector3 tmpVec = new Vector3();
    
    private final World world;
    
    public Player(World world) {
        this.world = world;
    }
    
    public void update(float delta) 
    {
        final Chunk chunk = world.getWorldChunk( playerPosition );
        final BlockKey block = chunk.getBlockKey( playerPosition );
        
//        position.add( velocity.x * delta , velocity.y * delta , velocity.z * delta );
        
        // update camera
        final PerspectiveCamera camera = world.camera;
        updateCameraPosition();
        camera.direction.set( direction );
        camera.update(true);
    }
    
    public void setPosition(float x, float y, float z) {
        this.playerPosition.set(x,y,z);
        updateCameraPosition();
    }      
    
    public void setPosition(Vector3 pos) {
        setPosition(pos.x,pos.y,pos.z);
    }
    
    public Vector3 cameraPosition() {
        return cameraPosition;
    }
    
    public Vector3 playerPosition() {
        return playerPosition;
    }
    
    private void updateCameraPosition() 
    {
        float y = playerPosition.y + PLAYER_HEIGHT *0.75f;
        world.camera.position.set( playerPosition.x , y , playerPosition.z );
    }
    
    public void rotate(Vector3 axis,float angle) 
    {
        direction.rotate( axis , angle );
        direction.nor();
        up.rotate( axis , angle );
    }
    
    public void rotateLook(Vector3 axis, float angle) 
    {
        direction.rotate(axis , angle );
        direction.nor();
    }
    
    public void translate(Vector3 v)
    {
        playerPosition.add( v );
        updateCameraPosition();
    }
    
    public void translate(float dx,float dy,float dz)
    {
        playerPosition.add( dx,dy,dz );
        updateCameraPosition();
    }
    
    public void lookAt (float x, float y, float z) 
    {
        tmpVec.set(x, y, z).sub( playerPosition ).nor();
        if (!tmpVec.isZero()) {
            float dot = tmpVec.dot(up); // up and direction must ALWAYS be orthonormal vectors
            if (Math.abs(dot - 1) < 0.000000001f) {
                // Collinear
                up.set(direction).scl(-1);
            } else if (Math.abs(dot + 1) < 0.000000001f) {
                // Collinear opposite
                up.set(direction);
            }
            direction.set(tmpVec);
            normalizeUp();
        }
    }

    public void lookAt (Vector3 target) {
        lookAt(target.x, target.y, target.z);
    }   
    
    public void normalizeUp () {
        tmpVec.set(direction).crs(up).nor();
        up.set(tmpVec).crs(direction).nor();
    }
}
