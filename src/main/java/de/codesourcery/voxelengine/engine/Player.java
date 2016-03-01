package de.codesourcery.voxelengine.engine;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Vector3;

public class Player 
{
    /**
     * Height of player's bounding box (player footprint is always one block).
     */
    public static float PLAYER_HEIGHT = 1.75f*World.WORLD_CHUNK_BLOCK_SIZE;
    public static float MAX_VELOCITY= 5f;
    
    public static final boolean CAMERA_MODE_FLYING = false;
    
    /**
     * Player Y position is adjusted by deltaTime * COLLISION_Y_ADJUST
     * when he/she is either floating in the air or either head or feet are inside a block.
     */
    public static final float COLLISION_Y_ADJUST = 5f;
    
    // position of player feet
    private final Vector3 feetPosition = new Vector3();
    
    // position of player head
    private final Vector3 headPosition = new Vector3();
    
    public final Vector3 velocity= new Vector3();
    
    public final Vector3 up = new Vector3(0,1,0);
    public final Vector3 direction = new Vector3(0,0,-1); // normal vector
    
    private final Vector3 tmpVec = new Vector3();
    
    private final BlockKey block = new BlockKey();
    
    private final World world;
    
    private boolean cameraNeedsUpdate = true;
    
    private final Matrix3 normalMatrix = new Matrix3();
    
    public Player(World world) {
        this.world = world;
    }
    
    public ChunkKey getCurrentChunk() {
        return world.getChunkCoordinates( feetPosition );
    }
    
    public void update(float delta) 
    {
        if ( ! CAMERA_MODE_FLYING ) {
            handleCollisions(delta);
        }
        
        // update camera
        updateHeadPosition();
        
        if ( cameraNeedsUpdate ) 
        {
            final PerspectiveCamera camera = world.camera;
            world.camera.position.set( headPosition );            
            camera.direction.set( direction );
            camera.update(true);
            normalMatrix.set( camera.view ).inv().transpose();
            cameraNeedsUpdate = false;
        }
    }
    
    public Matrix3 normalMatrix() {
        return normalMatrix;
    }

    private void handleCollisions(float delta) 
    {
        final Chunk headChunk = world.getWorldChunk( headPosition );
        int blockType = headChunk.getBlockType( headChunk.blockIndex( headPosition ) );
        if ( blockType != BlockType.BLOCKTYPE_AIR ) { // head is blocked, move up
            feetPosition.y += delta * 5f;
            cameraNeedsUpdate=true;
            return;
        } 
        
        Chunk feetChunk = world.getWorldChunk( feetPosition );
        feetChunk.getBlockKey( feetPosition , block );
        
        blockType = feetChunk.getBlockType( feetChunk.blockIndex( feetPosition ) );
        if ( blockType != BlockType.BLOCKTYPE_AIR ) { // feet are blocked, move up
            final float blockTopY = feetChunk.center.y - World.WORLD_CHUNK_HALF_WIDTH + block.y * World.WORLD_CHUNK_BLOCK_SIZE;
            float deltaY = Math.abs( feetPosition.y - blockTopY );
            if ( deltaY > 0.05f) {
                feetPosition.y += delta * COLLISION_Y_ADJUST;
            } else {
                feetPosition.y = blockTopY;
            }
            cameraNeedsUpdate=true;
            return;
        } 
        
        // check type of block below us
        if ( block.y-1 >= 0 ) {
            block.y -= 1;
        } else {
            block.y = World.WORLD_CHUNK_SIZE-1;
            feetChunk = feetChunk.bottomNeighbour;
        }
        blockType = feetChunk.getBlockType( block.x , block.y , block.z );
        if ( blockType == BlockType.BLOCKTYPE_AIR ) { // oops, need to fall down
            feetPosition.y -= delta * COLLISION_Y_ADJUST;
            cameraNeedsUpdate=true;
            if ( feetPosition.y < 0 ) {
                feetPosition.y = 0;
            }
        } 
        else 
        { 
            // solid block, make sure we're standing right on the top surface
            // TODO: Adjust Y gradually.
            final float blockTopY = feetChunk.center.y - World.WORLD_CHUNK_HALF_WIDTH + block.y * World.WORLD_CHUNK_BLOCK_SIZE;
            if ( feetPosition.y != blockTopY ) {
                feetPosition.y = blockTopY;
                cameraNeedsUpdate=true;
            }
        }
    }
    
    public void setPosition(float x, float y, float z) {
        this.feetPosition.set(x,y,z);
        updateHeadPosition();
        cameraNeedsUpdate = true;
    }      
    
    public void setPosition(Vector3 pos) {
        setPosition(pos.x,pos.y,pos.z);
    }
    
    public Vector3 headPosition() {
        return headPosition;
    }
    
    public Vector3 feetPosition() {
        return feetPosition;
    }
    
    private void updateHeadPosition() 
    {
        headPosition.set( feetPosition.x , feetPosition.y + PLAYER_HEIGHT , feetPosition.z );
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
        feetPosition.add( v );
        updateHeadPosition();
        cameraNeedsUpdate=true;
    }
    
    public void translate(float dx,float dy,float dz)
    {
        feetPosition.add( dx,dy,dz );
        updateHeadPosition();
        cameraNeedsUpdate=true;
    }
    
    public void lookAt (float x, float y, float z) 
    {
        tmpVec.set(x, y, z).sub( feetPosition ).nor();
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
        cameraNeedsUpdate = true;
    }

    public void lookAt (Vector3 target) {
        lookAt(target.x, target.y, target.z);
    }   
    
    private void normalizeUp () {
        tmpVec.set(direction).crs(up).nor();
        up.set(tmpVec).crs(direction).nor();
    }
}
