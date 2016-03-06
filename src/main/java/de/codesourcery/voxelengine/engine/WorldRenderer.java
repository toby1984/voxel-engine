package de.codesourcery.voxelengine.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.LongMap.Entries;

import de.codesourcery.voxelengine.model.BlockKey;
import de.codesourcery.voxelengine.model.BlockType;
import de.codesourcery.voxelengine.model.Chunk;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.Player;
import de.codesourcery.voxelengine.model.World;

/**
 * Responsible for rendering the game world.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class WorldRenderer implements Disposable
{
    private static final Logger LOG = Logger.getLogger(WorldRenderer.class);


    // dummy value used when abusing a Map as a Set
    private static final Long DUMMY_VALUE = new Long(3L);

    public static final int RENDER_DISTANCE_CHUNKS = 3;

    private static final int MAX_CHUNKS_TO_LOAD = (2*RENDER_DISTANCE_CHUNKS+1)*(2*RENDER_DISTANCE_CHUNKS+1)*(2*RENDER_DISTANCE_CHUNKS+1);

    public static final boolean CULL_FACES = true;

    public static final boolean DEPTH_BUFFER = true;    

    // Debug renderer
    public static final boolean RENDER_WIREFRAME =false;

    private final World world;

    private ChunkKey centerChunk=null;
    private long previousChunkID=ChunkKey.INVALID;

    // TODO: This map should really be just a Set but libgdx doesn't provide this
    private final LongMap<Chunk> visibleChunks = new LongMap<>(100); // populated with the chunk IDs of all chunks that intersect the view frustum
    private final LongMap<Chunk> loadedChunks = new LongMap<>(400); // Holds all chunks that are currently loaded because they're within view distance of the camera

    private Chunk[] visibleChunkList = new Chunk[ MAX_CHUNKS_TO_LOAD ];
    public int visibleChunkCount=0; 

    // Comparator used to sort chunks in top->down (+y -> -y ) order for
    // properly calculating the influence of sun light
    private static final Comparator<Chunk> Y_COMPARATOR = new Comparator<Chunk>() {

        @Override
        public int compare(Chunk o1, Chunk o2) 
        {
            return Integer.compare( o2.chunkKey.y , o1.chunkKey.y ); 
        }
    }; 

    // shader to use for rendering chunks
    private final ShaderProgram chunkShader;

    // the player instance
    private final Player player;

    public int totalTriangles=0; // TODO: Debug code, remove

    // float[] array to use when meshing a chunk
    private final VertexDataBuffer vertexBuffer = new VertexDataBuffer();

    private final SkyBox skyBox;

    public WorldRenderer(World world,ShaderManager shaderManager) 
    {
        Validate.notNull(world, "world must not be NULL");
        Validate.notNull(shaderManager,"shaderManager must not be NULL");
        this.world = world;
        this.player = world.player;
        this.chunkShader = shaderManager.getShader( RENDER_WIREFRAME ? ShaderManager.WIREFRAME_SHADER : ShaderManager.FLAT_SHADER );
        this.skyBox = new SkyBox( shaderManager );
    }

    /**
     * Returns the number of chunks loaded because they're within viewing distance of the player.
     * 
     * TODO: Debug code, remove when done
     * @return
     */
    public int getLoadedChunkCount() {
        return loadedChunks.size;
    }

    /**
     * Render the world.
     * 
     * @param deltaTime Delta time (seconds) to previous frame
     */
    public void render(float deltaTime) 
    {
        final PerspectiveCamera camera = world.camera;

        final long centerChunkID = player.cameraChunkID;

        visibleChunks.clear();
        visibleChunks.put( centerChunkID , null );
        int visibleChunkCount = 0;

        if ( previousChunkID != centerChunkID || centerChunk == null ) // player has moved to a different chunk
        {
            centerChunk = ChunkKey.fromID( centerChunkID );

            final int distanceInChunksSquared = 3*(RENDER_DISTANCE_CHUNKS)*(RENDER_DISTANCE_CHUNKS);// dx*dx+dy*dy+dz*dz with dx == dy == dz

            /* Determine chunks that intersect with the view frustum.
             * 
             * Note that I intentionally add +1 to the distance because building the mesh for
             * any given chunk also requires looking at the chunk's neighbours when
             * considering the boundary blocks. 
             * If we'd just load all the chunks that are within the rendering distance
             * the outer chunks wouldn't have their neighbours loaded and a NPE would
             * happen during mesh building when trying to access the neighbour.
             */            
            final List<Long> toLoad = new ArrayList<>( MAX_CHUNKS_TO_LOAD );

            final Frustum f = world.camera.frustum;

            final int xmin = centerChunk.x - RENDER_DISTANCE_CHUNKS;
            final int xmax = centerChunk.x + RENDER_DISTANCE_CHUNKS;
            final int ymin = centerChunk.y - RENDER_DISTANCE_CHUNKS;
            final int ymax = centerChunk.y + RENDER_DISTANCE_CHUNKS;
            final int zmin = centerChunk.z - RENDER_DISTANCE_CHUNKS;
            final int zmax = centerChunk.z + RENDER_DISTANCE_CHUNKS;

            for ( int x = xmin ; x <= xmax ; x++ ) 
            {
                final float px = x * World.CHUNK_WIDTH;
                final boolean borderX = (x == xmin) || (x == xmax); 
                for ( int y = ymin ; y <= ymax ; y++ ) 
                {
                    final boolean borderY = (y == ymin) || (y == ymax); 
                    final float py = y * World.CHUNK_WIDTH;
                    for ( int z = zmin ; z <= zmax ; z++ ) 
                    {
                        final long chunkID = ChunkKey.toID( x , y , z );
                        final Chunk loaded = loadedChunks.get( chunkID ); 
                        if ( loaded == null ) 
                        {
                            toLoad.add( chunkID );
                        }                     
                        final boolean borderZ = ( z == zmin ) || (z == zmax);
                        if ( ! ( borderX || borderY || borderZ ) ) 
                        {
                            final float pz = z * World.CHUNK_WIDTH;
                            // TODO: Culling against enclosing sphere selects way more chunks than necessary...maybe use AABB instead ?
                            if ( intersectsSphere(f,px,py,pz,World.CHUNK_ENCLOSING_SPHERE_RADIUS) ) 
                            { 
                                visibleChunks.put( chunkID , loaded );
                                if ( loaded != null ) {
                                    visibleChunkList[visibleChunkCount++]=loaded;
                                }
                            }
                        }
                    }                 
                }            
            }

            // unload all chunks that are no longer within range
            final List<Chunk> toUnload = new ArrayList<>( loadedChunks.size );
            final Entries<Chunk> entries = loadedChunks.entries();
            while ( entries.hasNext )
            {
                final Chunk chunk = entries.next().value;
                final ChunkKey key = chunk.chunkKey;
                if ( centerChunk.dst2( key ) > distanceInChunksSquared ) 
                {
                    entries.remove();
                    chunk.setIsInUse( false ); // crucial otherwise chunk unloading will fail because sanity check triggers
                    chunk.disposeVBO();
                    toUnload.add( chunk );
                }
            }

            // bluk-unload chunks
            if ( ! toUnload.isEmpty() ) {
                System.out.println("*** Unloading: "+toUnload.size()+" chunks");
                world.chunkManager.unloadChunks( toUnload );
            }

            // bulk-load missing chunks
            if ( ! toLoad.isEmpty() ) 
            {
                System.out.println("*** Loading "+toLoad.size()+" chunks");
                for ( Chunk chunk : world.chunkManager.getChunks( toLoad ) ) 
                {
                    final long chunkID = chunk.chunkKey.toID();
                    loadedChunks.put( chunkID , chunk );
                    if ( visibleChunks.containsKey( chunkID ) ) {
                        visibleChunks.put( chunkID , chunk );
                        visibleChunkList[visibleChunkCount++]=chunk;
                    }
                }
            }       
            previousChunkID = centerChunkID;
        } 
        else 
        {
            // camera is still within the same chunk, just determine the visible chunks
            // since all chunks within view distance have already been loaded
            final Frustum f = world.camera.frustum;

            final int xmin = centerChunk.x - RENDER_DISTANCE_CHUNKS;
            final int xmax = centerChunk.x + RENDER_DISTANCE_CHUNKS;
            final int ymin = centerChunk.y - RENDER_DISTANCE_CHUNKS;
            final int ymax = centerChunk.y + RENDER_DISTANCE_CHUNKS;
            final int zmin = centerChunk.z - RENDER_DISTANCE_CHUNKS;
            final int zmax = centerChunk.z + RENDER_DISTANCE_CHUNKS;

            for ( int x = xmin ; x <= xmax ; x++ ) 
            {
                final float px = x * World.CHUNK_WIDTH;
                final boolean borderX = (x == xmin) || (x == xmax); 
                for ( int y = ymin ; y <= ymax ; y++ ) 
                {
                    final boolean borderY = (y == ymin) || (y == ymax); 
                    final float py = y * World.CHUNK_WIDTH;
                    for ( int z = zmin ; z <= zmax ; z++ ) 
                    {
                        final boolean borderZ = ( z == zmin ) || (z == zmax); 
                        if ( ! ( borderX || borderY || borderZ ) ) 
                        {
                            final float pz = z * World.CHUNK_WIDTH;
                            // TODO: Culling against enclosing sphere selects way more chunks than necessary...maybe use AABB instead ? 
                            if ( intersectsSphere(f,px,py,pz,World.CHUNK_ENCLOSING_SPHERE_RADIUS) ) 
                            { 
                                final long chunkID = ChunkKey.toID( x, y, z );
                                final Chunk chunk = loadedChunks.get( chunkID );
                                visibleChunks.put( chunkID ,  chunk );
                                if ( chunk != null ) {
                                    visibleChunkList[visibleChunkCount++]=chunk;
                                }
                            }
                        }
                    }                 
                }            
            }
        }
        this.visibleChunkCount = visibleChunkCount;

        // sort chunks in descending Y-coordinate order
        // before propagating sunlight from top -> bottom
        Arrays.sort( visibleChunkList , 0 , visibleChunkCount , Y_COMPARATOR );

        // calculate sunlight
        for ( int i = 0 ; i < visibleChunkCount ; i++ ) 
        {
            final Chunk chunk = visibleChunkList[i];
            if ( chunk.needsRebuild() ) 
            {
                if ( chunk.isNotEmpty() ) 
                {
                    calculateSunlight( chunk );                    
                } else {
                    chunk.setLightLevel( Chunk.LIGHTLEVEL_SUNLIGHT );
                }
            }
        }

        // calculate diffuse light & build mesh
        for ( int i = 0 ; i < visibleChunkCount ; i++ ) 
        {
            final Chunk chunk = visibleChunkList[i];
            if ( chunk.isNotEmpty() ) 
            {
                if ( chunk.needsRebuild() ) {
                    calculateDiffuseLight( chunk );                    
                    buildMesh( chunk );
                }
            }
            chunk.clearFlags( Chunk.FLAG_NEEDS_REBUILD );
        }        

        // render skybox 
        skyBox.render( world.camera );

        // Render visible,non-empty chunks
        if ( CULL_FACES ) {
            Gdx.gl30.glEnable( GL20.GL_CULL_FACE );
        } else {
            Gdx.gl30.glDisable( GL20.GL_CULL_FACE );
        }
        if ( DEPTH_BUFFER ) {
            Gdx.gl30.glEnable(GL20.GL_DEPTH_TEST);
        } else {
            Gdx.gl30.glDisable(GL20.GL_DEPTH_TEST);
        }

        chunkShader.begin();

        if ( ! WorldRenderer.RENDER_WIREFRAME ) {
            chunkShader.setUniformMatrix("u_modelView", camera.view );
            chunkShader.setUniformMatrix("u_normalMatrix", player.normalMatrix() );
        }
        chunkShader.setUniformMatrix("u_modelViewProjection", camera.combined );

        int totalTriangles = 0;
        for ( int i = 0 ; i < visibleChunkCount ; i++ ) 
        {
            final Chunk chunk = visibleChunkList[i];
            if ( chunk.isNotEmpty() ) 
            {
                totalTriangles += chunk.renderer.render( chunkShader , false );
            }
        }
        this.totalTriangles = totalTriangles;
        chunkShader.end();        
    }

    private void calculateSunlight(Chunk chunk) 
    {
        System.out.println("Lighting chunk "+chunk.chunkKey);

        // set all blocks to 'darkness'
        chunk.setLightLevel( (byte) 0 );

        // light top->down blocks
        for ( int z = 0 ; z < World.CHUNK_SIZE ; z++ ) 
        {
            for ( int x = 0 ; x < World.CHUNK_SIZE ; x++ ) 
            {
                final byte lightLevel = chunk.topNeighbour == null ? Chunk.LIGHTLEVEL_SUNLIGHT : chunk.topNeighbour.getLightLevel(x,0,z);
                chunk.setLightLevel( x , World.CHUNK_SIZE-1 , z , lightLevel );
                if ( chunk.isBlockEmpty( x , World.CHUNK_SIZE-2 , z ) ) 
                {
                    for ( int y = World.CHUNK_SIZE-2 ; y >= 0 ; y --) 
                    {
                        chunk.setLightLevel( x , y , z , lightLevel );
                        if ( chunk.isBlockNotEmpty( x , y , z ) ) 
                        {
                            break;
                        }
                    }       
                }
            }                        
        }
    }

    private void calculateDiffuseLight(Chunk chunk) 
    {
        // for each non-opaque block that has a light-level
        // of 0, set it to the average lightlevel of all neighbouring
        // blocks minus one
        final BlockKey tmp = new BlockKey();
        for ( int y = World.CHUNK_SIZE-1 ; y >= 0 ; y-- ) 
        {        
            for ( int x = 0 ; x < World.CHUNK_SIZE ; x++ ) 
            {
                for ( int z = 0 ; z < World.CHUNK_SIZE ; z++) 
                {
                    byte lightlevel = chunk.getLightLevel( x ,  y ,  z );
                    if ( lightlevel == 0 ) 
                    {
                        tmp.set(x,y,z);
                        final byte newLevel = chunk.calcNeighbourLightLevel( tmp );
                        if ( newLevel >= 1 ) {
                            chunk.setLightLevel( x , y , z , (byte) (newLevel-1) );
                        }
                    }
                }
            }
        }
    }    

    private static boolean intersectsSphere(Frustum f,float x,float y,float z,float radius) 
    {
        for(int i = 0; i < 6; ++i) 
        {
            final Plane plane = f.planes[i]; // distance to this plane
            final float dist = plane.normal.dot( x , y, z ) + plane.d;

            if (dist < -radius) {
                return false; // outside of frustum
            }

            if ( Math.abs(dist) < radius) {
                return true; // intersects with frustum
            }
        }
        // inside frustum
        return true;
    }

    private void buildMesh(Chunk chunk) 
    {
        if ( chunk.renderer == null ) 
        {
            chunk.renderer = new ChunkRenderer();
        }
        LOG.debug("buildMesh(): Building mesh for "+chunk);
        chunk.renderer.buildMesh( chunk , vertexBuffer );
    }

    @Override
    public void dispose() {
        skyBox.dispose();
    }
}