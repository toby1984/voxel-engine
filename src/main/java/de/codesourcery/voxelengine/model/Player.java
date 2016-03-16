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
    
    private final Vector3 feetPosition2 = new Vector3();
    
    /**
     * Used to disable gravity during player movement.
     */
    public static final boolean CAMERA_MODE_FLYING = true;
    
    private static final Vector3 GRAVITY = new Vector3(0,-5f,0);
    private static final Vector3 INV_GRAVITY = new Vector3(0,-GRAVITY.y,0);
    
    private static final float SPEED_LIMIT = 5f;
    
    private final Vector3 tmp = new Vector3();
    private final Vector3 tmp2 = new Vector3();
    private final Vector3 tmp3 = new Vector3();
    
    // the player's current velocity
    public final Vector3 velocity= new Vector3();
    
    public final Vector3 playerAcceleration = new Vector3();
    public final Vector3 gravityCompensation = new Vector3( INV_GRAVITY );
    
    // matrix used to transform normal vectors into view space
    // (=inverse transpose of camera view matrix)
    private final Matrix3 normalMatrix = new Matrix3();
    
    public final World world;
    
    public final Camera camera;
    
    public boolean playerTranslated = true; // set to true to force initialization
    public boolean playerRotated = true; // set to true to force initialization
    
    public boolean isFallingDown;
    
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
    	// check whether the block 1/2 blocks below the current feet position
    	// is empty (and thus we need to fall down)
    	tmp.set( feetPosition() );
    	tmp.y -= World.HALF_BLOCK_SIZE;
        
        final Chunk chunk = world.getWorldChunk( tmp );
        final boolean feetBlockEmpty = chunk.isBlockEmpty( tmp );  
        
    	if ( feetBlockEmpty ) 
    	{
    		// block below player feet is empty, disable gravity compensation
    		// force so player starts falling down
    		gravityCompensation.setZero();
    		isFallingDown = true;
    	}
    	else 
    	{
    		// block below feet is not empty, 
    		// enable gravity compensation and adjust player Y 
    		// so that he/she rests on top of the block
    		final float yAcceleration = playerAcceleration.y + gravityCompensation.y + GRAVITY.y;
    		if ( yAcceleration <= 0 ) { // we only want to force the player to land if he/she is actually falling down
    			if ( isFallingDown ) 
    			{
	    			isFallingDown = false;
	    			gravityCompensation.set( INV_GRAVITY );
	    			playerAcceleration.setZero();
	    			velocity.setZero();
	    			final long chunkId = chunk.chunkKey.toID();
	    			
	    			final Vector3 blockCenter = BlockKey.getBlockCenter( chunkId , BlockKey.getBlockID( chunkId , tmp ) , tmp );
	    			blockCenter.add( 0 , World.HALF_BLOCK_SIZE , 0 );
	    			blockCenter.add( 0, PLAYER_HEIGHT , 0 ); // + player height since we need to get set the HEAD position
	    			setPosition( headPosition().x , blockCenter.y , headPosition().z );
    			}
    			return;
    		}
    	}
    	
        tmp3.set( playerAcceleration );
        tmp3.add( gravityCompensation );
        tmp3.add( GRAVITY ); 
        tmp3.scl( delta ); // acc = (acceleration + gravity)*dT
        
        System.out.println("acc = "+tmp3);
        tmp3.add( velocity );
        tmp3.limit( SPEED_LIMIT ); // velocity += (acceleration + gravity)*dT
        velocity.set( tmp3 );
        System.out.println("velocity = "+tmp3+" (delta: "+delta+")");
        
        tmp3.scl( delta ).add( headPosition() ); // headPosition += velocity*dT
        System.out.println("position = "+headPosition());
        
        if ( canMoveTo( tmp3.x , tmp3.y , tmp3.z ) ) 
        {
        	setPosition( tmp3 );
        }
    }
    
    private boolean canMoveTo(float headX,float headY,float headZ) 
    {
        tmp.set( headX , headY , headZ );
        Chunk chunk = world.getWorldChunk( tmp );
        final boolean headBlockEmpty = chunk.isBlockEmpty( tmp );
        
        calculateFeetPosition( tmp , tmp );
        chunk = world.getWorldChunk( tmp );
        final boolean feetBlockEmpty = chunk.isBlockEmpty( tmp );        
        
        return headBlockEmpty && feetBlockEmpty;
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
        return calculateFeetPosition( headPosition , feetPosition2 );
    }
    
    private Vector3 calculateFeetPosition(Vector3 headPosition,Vector3 result) 
    {
    	return result.set(headPosition.x , headPosition.y - PLAYER_HEIGHT , headPosition.z );
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
    public boolean translate(Vector3 v)
    {
        return translate(v.x,v.y,v.z);
    }
    
    /**
     * Translate player position.
     * 
     * @param dx
     * @param dy
     * @param dz
     */
    public boolean translate(float dx,float dy,float dz)
    {
    	if ( canMoveTo( camera.position.x + dx , camera.position.y + dy , camera.position.z + dz ) ) 
    	{ 
    		camera.translate(dx,dy,dz);
    		playerTranslated = true;
    		return true;
    	}
    	return false;
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