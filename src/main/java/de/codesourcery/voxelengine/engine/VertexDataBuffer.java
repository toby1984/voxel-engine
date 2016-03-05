package de.codesourcery.voxelengine.engine;

/**
 * Used to hold vertex data temporarily until it has been uploaded to the GPU.
 * 
 * The intention is have one fixed buffer per (mesh builder) worker
 * thread to avoid memory allocations (and thus GC) whenever a chunk needs to be re-build.  
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class VertexDataBuffer 
{
    // ptr into the vertexData array
    // where the next vertex should be written
    public int vertexPtr=0;
    public float[] vertexData = new float[ 100 * ChunkRenderer.VERTEX_FLOAT_SIZE ];
}
