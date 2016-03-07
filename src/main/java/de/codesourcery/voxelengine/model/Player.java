package de.codesourcery.voxelengine.model;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Vector3;

/**
 * Holds all data associated with the player (position,velocity,current chunk, etc).
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Player 
{
    /**
     * Height of player's bounding box (player footprint is always one block).
     */
    public static float PLAYER_HEIGHT = 1.5f*World.BLOCK_SIZE;
    public static float MAX_VELOCITY= 5f;
    
    private final Vector3 feetPosition2 = new Vector3();
    
    // used to hold temporary calculation results without needing to
    // allocate an object (and thus increase GC pressure) 
    private static final BlockKey block = new BlockKey();
    
    /**
     * Used to disable gravity during player movement.
     */
    public static final boolean CAMERA_MODE_FLYING = true;
    
    /**
     * Player Y position is adjusted by deltaTime * COLLISION_Y_ADJUST
     * when he/she is either floating in the air or either head or feet are inside a block.
     */
    public static final float COLLISION_Y_ADJUST = 5f;
    
    
    // the player's current velocity
    public final Vector3 velocity= new Vector3();
    
    // matrix used to transform normal vectors into view space
    // (=inverse transpose of camera view matrix)
    private final Matrix3 normalMatrix = new Matrix3();
    
    public final World world;
    
    public final Camera camera;
    
    public boolean playerTranslated = true; // set to true to force initialization
    public boolean playerRotated = true; // set to true to force initialization
    
    public long cameraChunkID;
    
    public final PlayerToolbar toolbar = new PlayerToolbar();
    
    /**
     * Create player instance.
     * 
     * @param world the world the player is a part of.
     * @see World#player
     */
    public Player(World world,Camera camera) {
        this.world = world;
        this.camera = camera;
    }
    
    /**
     * "Tick" player.
     * 
     * This method currently handles the player's movement physics
     * and updates the camera according to the player's movement.
     * 
     * @param delta
     */
    public void update(float delta) 
    {
        if ( ! CAMERA_MODE_FLYING ) {
            handleGravity(delta);
        }
        
        if ( playerRotated || playerTranslated ) 
        {
            camera.update(true);
            normalMatrix.set( camera.view ).inv().transpose();
            
            if ( playerTranslated ) {
                cameraChunkID = ChunkKey.getChunkID( camera.position );
            }
        }
    }
    
    /**
     * Resets the internal flags used to keep track of whether
     * the player (camera) got translated and/or rotated since the previous frame.
     */
    public void resetMovementFlags() {
        playerRotated = playerTranslated = false;
    }
    
    /**
     * Returns the camera's normal matrix (=inverse transpose of the view matrix).
     * @return
     */
    public Matrix3 normalMatrix() {
        return normalMatrix;
    }

    /**
     * Checks whether the player's bounding box is on top of a solid chunk (=ok), 
     * collides with a chunk (forces the player to move upwards) or is on top of
     * an "air" chunk (forces the player to fall downwards).
     * 
     * @param delta
     */
    private void handleGravity(float delta) 
    {
        final Vector3 headPosition = camera.position;
        final Vector3 feetPosition = feetPosition();
        final Chunk headChunk = world.getWorldChunk( headPosition );
        int blockType = headChunk.getBlockType( headChunk.blockIndex( headPosition ) );
        if ( blockType != BlockType.AIR ) { // head is blocked, move up
            headPosition.y += delta * 5f;
            playerTranslated=true;
            return;
        } 
        
        Chunk feetChunk = world.getWorldChunk( feetPosition );
        feetChunk.getBlockKey( feetPosition , block );
        
        blockType = feetChunk.getBlockType( feetChunk.blockIndex( feetPosition ) );
        if ( blockType != BlockType.AIR ) { // feet are blocked, move up
            final float blockTopY = feetChunk.center.y - World.CHUNK_HALF_WIDTH + block.y * World.BLOCK_SIZE;
            float deltaY = Math.abs( feetPosition.y - blockTopY );
            if ( deltaY > 0.05f) {
                headPosition.y += delta * COLLISION_Y_ADJUST;
            } else {
                headPosition.y = blockTopY;
            }
            playerTranslated=true;
            return;
        } 
        
        // check type of block below us
        if ( block.y-1 >= 0 ) {
            block.y -= 1;
        } else {
            block.y = World.CHUNK_SIZE-1;
            feetChunk = feetChunk.bottomNeighbour;
        }
        blockType = feetChunk.getBlockType( block.x , block.y , block.z );
        if ( blockType == BlockType.AIR ) { // oops, need to fall down
            headPosition.y -= delta * COLLISION_Y_ADJUST;
            if ( headPosition.y < PLAYER_HEIGHT ) {
                headPosition.y = PLAYER_HEIGHT;
            }
            playerTranslated=true;
        } 
        else 
        { 
            // solid block, make sure we're standing right on the top surface
            // TODO: Adjust Y gradually.
            final float blockTopY = feetChunk.center.y - World.CHUNK_HALF_WIDTH + block.y * World.BLOCK_SIZE;
            if ( feetPosition.y != blockTopY ) {
                feetPosition.y = blockTopY;
                playerTranslated=true;
            }
        }
    }
    
    /**
     * Returns the position of the player's head (which is equal to the camera position).
     * 
     * @return
     */
    public Vector3 headPosition() {
        return camera.position;
    }
    
    /**
     * Returns the position of the player's feet.
     * 
     * @return
     */
    public Vector3 feetPosition() {
        final Vector3 headPosition = camera.position;
        return feetPosition2.set(headPosition.x , headPosition.y - PLAYER_HEIGHT , headPosition.z );
    }
    
    /**
     * Set the player's position.
     * 
     * @param pos
     */
    public void setPosition(Vector3 pos) {
        setPosition(pos.x,pos.y,pos.z);
    }    
    
    /**
     * Set the player's position.
     * 
     * @param x
     * @param y
     * @param z
     */    
    public void setPosition(float x, float y, float z) 
    {
        camera.position.set(x,y,z);
        playerTranslated = true;
    }     
    
    /**
     * Rotate the player around the 'up' axis (=in the XZ plane only).
     * 
     * @param x
     * @param y
     * @param z
     */    
    public void rotate(float angle) 
    {
        camera.rotate( camera.up , angle );
        playerRotated=true;
    }
    
    /**
     * Translate player position.
     * 
     * @param v
     */
    public void translate(Vector3 v)
    {
        translate(v.x,v.y,v.z);
    }
    
    /**
     * Translate player position.
     * 
     * @param dx
     * @param dy
     * @param dz
     */
    public void translate(float dx,float dy,float dz)
    {
        camera.translate(dx,dy,dz);
        playerTranslated = true;
    }
    
    /**
     * Makes the player look at a specific point (in world coordinates).
     * 
     * @param x
     * @param y
     * @param z
     */
    public void lookAt (float x, float y, float z) 
    {
        camera.lookAt( x , y, z );
        playerRotated = true;
    }

    /**
     * Makes the player look at a specific point (in world coordinates).     
     * @param target
     */
    public void lookAt (Vector3 target) {
        lookAt(target.x, target.y, target.z);
    }   
    
    public void rotateAround (Vector3 point, Vector3 axis, float angle) 
    {
        camera.rotateAround(point, axis, angle);
        playerRotated = true;
    }    
}