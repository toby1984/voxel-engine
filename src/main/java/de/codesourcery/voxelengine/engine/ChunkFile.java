package de.codesourcery.voxelengine.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import com.badlogic.gdx.math.Vector3;

/**
 * Filesystem representation of a chunk , provides methods for reading/writing chunk data.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class ChunkFile 
{
    private static final Logger LOG = Logger.getLogger(ChunkFile.class);
    
    protected static final byte[] FILE_HEADER_MAGIC = { (byte) 0xde,(byte) 0xad,(byte) 0xbe,(byte) 0xef };
    
    protected static final int HEADER_SIZE = 4+4+4;
    
    protected static final int VERSION_HEADER = 1;
    protected static final int VERSION_CHUNK = 1;
    protected static final int VERSION_SUB_CHUNK = 1;
    
    // segment types
    public static final int TYPE_HEADER_SEGMENT = 1;
    public static final int TYPE_CHUNK_SEGMENT = 2;
    public static final int TYPE_SUB_CHUNK_SEGMENT = 3;
    
    private final File file;
    
    protected interface Segment 
    {
        public int type(); 
        
        public int version();
        
        public int payloadLength();
        
        public byte[] payload();
        
        /**
         * Read int from payload.
         * 
         * @param offset
         * @return
         */
        public int readInt(int offset);
        
        /*
         * Read float from payload.
         */
        public float readFloat(int offset);
        
        public Vector3 readVector3(int offset);
        
        public int[] readIntArray(int offset);
    }    
    
    public static final class LoadVisitor  
    {
        private boolean headerFound;

        private final List<Chunk> subChunks = new ArrayList<>();
        private Chunk topLevelChunk; 
        
        public void visit(Segment t) 
        {
            switch( t.type() ) 
            {
                case TYPE_CHUNK_SEGMENT:
                    if ( t.version() == VERSION_CHUNK ) 
                    {
                        if ( topLevelChunk != null ) {
                            throw new RuntimeException("Duplicate chunk segment with version "+t.version());
                        }
                        topLevelChunk = readChunk( t , false );
                        return;
                    }
                    break;
                case TYPE_SUB_CHUNK_SEGMENT:
                    if ( t.version() == VERSION_SUB_CHUNK ) 
                    {
                        subChunks.add( readChunk( t , true ) );
                        return;
                    }
                    break;
                case TYPE_HEADER_SEGMENT:
                    if ( t.version() == VERSION_HEADER ) 
                    {
                        if ( headerFound ) 
                        {
                            throw new RuntimeException("Duplicate file header segment with version "+t.version());
                        }
                        if ( t.payloadLength() != 4 ) 
                        {
                            throw new RuntimeException("Invalid file header: payload length mismatch, expected 4 but got "+t.payloadLength());
                        }
                        for ( int i = 0 , len = FILE_HEADER_MAGIC.length ; i < len ; i++ ) 
                        {
                            if ( t.payload()[i] != FILE_HEADER_MAGIC[i] ) {
                                throw new RuntimeException("Invalid file magic, byte["+i+"] should've been 0x"+Integer.toHexString( FILE_HEADER_MAGIC[i] )+" but was 0x"+Integer.toHexString( t.payload()[i] ) );
                            }
                        }
                        headerFound = true;
                        return;
                    }
                    break;
            }
            LOG.warn("visit(): Skipping unknown segment type "+t.type());
        }
        
        public void endVisit() 
        {
            if ( topLevelChunk == null ) {
                throw new RuntimeException("Internal error,file contains no top-level chunk?");
            }
            topLevelChunk.updateIsEmptyFlag();
        }
        
        public Chunk getChunk() 
        {
            if ( topLevelChunk == null ) {
                throw new IllegalStateException("Reading top-level chunk file failed");
            }
            return topLevelChunk;
        }
    }
    
    private static Chunk readChunk(Segment s,boolean isSubChunk) {
        
        /*
            public final int totalChunkSize;
            public final float blocksize;
            public int flags;
            public final Vector3 center=new Vector3();
            public int[] blockTypes;             
         */
        final int totalChunkSize = s.readInt( 0 );
        final float blockSize = s.readFloat( 4 );
        final int flags = s.readInt( 8 );
        final Vector3 center = s.readVector3( 12 ); // 3*4 bytes 
        final int[] blockTypes = s.readIntArray( 24 );
        
        final ChunkKey chunkKey;
        if ( isSubChunk ) {
            chunkKey = null;
        } else {
            final int chunkX = s.readInt( 28 );
            final int chunkY = s.readInt( 32 );
            final int chunkZ = s.readInt( 36 );
            chunkKey =  new ChunkKey( chunkX ,chunkY,chunkZ ) ;
        }
        
        final Chunk result = new Chunk( chunkKey , center , totalChunkSize , blockSize , blockTypes );
        result.flags = flags;
        result.setFlags( Chunk.FLAG_NEEDS_REBUILD );
        return result;
    }   
    
    private static void writeChunk(SegmentWriter writer,Chunk chunk) throws IOException 
    {
        if ( chunk.chunkKey == null ) { // only sub-chunks do not have a ChunkKey set
            throw new IllegalArgumentException("ChunkKey needs to be set ");
        }
        writer.setType( TYPE_CHUNK_SEGMENT );
        writer.setVersion( VERSION_CHUNK );
        
        setPayload( writer , chunk );
        writer.writeSegment();
    }
    
    private static void setPayload(SegmentWriter writer,Chunk chunk) throws IOException {
        
        writer.writeInt( chunk.chunkSize );
        writer.writeFloat( chunk.blocksize );
        writer.writeInt( chunk.flags );
        writer.writeVector3( chunk.center );
        writer.writeIntArray( chunk.blockTypes );
        if ( chunk.chunkKey != null ) {
            writer.writeInt( chunk.chunkKey.x );
            writer.writeInt( chunk.chunkKey.y );
            writer.writeInt( chunk.chunkKey.z );
        }
    }    
    
    protected static final class SegmentWriter implements AutoCloseable {
        
        private final byte[] headerBuffer = new byte[ HEADER_SIZE ];
        private byte[] dataBuffer = new byte[ 64*1024 ];
        
        private int type;
        private int version;
        private int payloadLength=0;
        
        private final OutputStream out;
        
        public SegmentWriter(OutputStream out) throws FileNotFoundException  
        {
            Validate.notNull(out, "output stream must not be NULL");
            this.out = out;
        }
        
        public void setType(int type) {
            this.type = type;
        }
        
        public void setVersion(int version) {
            this.version = version;
        }
        
        public void setPayload(byte[] data) 
        {
            this.payloadLength = data.length;
            System.arraycopy( data , 0 , dataBuffer , 0 , data.length );
        }
        
        public void writeSegment() throws IOException 
        {
            if ( type == -1 || version == -1 || payloadLength < 0 ) {
                throw new IllegalStateException("write() called although writer not properly populated");
            }
            writeInt( type , headerBuffer , 0 );
            writeInt( version , headerBuffer , 4 );
            writeInt( payloadLength , headerBuffer , 8 );
            
            out.write( headerBuffer , 0 , HEADER_SIZE );
            
            if ( payloadLength > 0 ) 
            {
                out.write( dataBuffer , 0 , payloadLength );
            }
            type = version = -1;
            payloadLength = 0;
        }
        
        private void maybeGrowDataBuffer(int minBytesFree) 
        {
            if ( (dataBuffer.length - payloadLength ) < minBytesFree ) 
            {
                final byte[] tmp = new byte[ dataBuffer.length*2 ];
                System.arraycopy( dataBuffer , 0 , tmp , 0 , payloadLength );
                dataBuffer = tmp;
            }
        }
        
        public void writeInt(int value) 
        {
            maybeGrowDataBuffer( 4 );
            writeInt( value , dataBuffer ,payloadLength );
            payloadLength+=4;
        }
        
        public void writeFloat(float value) 
        {
            maybeGrowDataBuffer( 4 );            
            writeFloat( value , dataBuffer ,payloadLength );
            payloadLength+=4;
        }        
        
        public void writeVector3(Vector3 value) 
        {
            maybeGrowDataBuffer( 3*4 ); // 3x 32 bit float             
            writeVector3( value , dataBuffer ,payloadLength );
            payloadLength+=12;
        } 
        
        public void writeIntArray(int[] array) 
        {
            maybeGrowDataBuffer( 4 + array.length*4 ); // int<array length> + 32 bit * array.len 
            writeIntArray( array, dataBuffer ,payloadLength );
            payloadLength+= array.length*4;
        }         
        
        private void writeInt(int value,byte[] buffer,int offset) 
        {
            buffer[offset  ] = (byte) ( (value & 0xff000000) >> 24 );
            buffer[offset+1] = (byte) ( (value & 0x00ff0000) >> 16 );
            buffer[offset+2] = (byte) ( (value & 0x0000ff00) >>  8 );
            buffer[offset+3] = (byte) ( (value & 0x000000ff)       );
        }

        private void writeFloat(float value,byte[] buffer,int offset) {
            writeInt( Float.floatToIntBits( value ) , buffer ,offset );
        }
        
        private void writeVector3(Vector3 value,byte[] buffer,int offset) 
        {
            writeFloat( value.x , buffer , offset );
            writeFloat( value.y , buffer , offset+4 );
            writeFloat( value.z , buffer , offset+8 );
        }        
        
        public void writeIntArray(int[] array,byte[] buffer,int offset) 
        {
            writeInt( array.length , buffer ,offset );
            for ( int i = 0 , ptr = offset+4 , len = array.length ; i < len ; i++, ptr+=4 ) 
            {
                writeInt( array[i] , buffer , ptr );
            }
        }
        
        public void close() throws IOException {
            out.close();
        }
    }
    
    protected static final class SegmentReader implements Segment {
    
        private final byte[] headerBuffer = new byte[ HEADER_SIZE ];
        private final byte[] dataBuffer = new byte[ 64*1024 ];
        
        private final InputStream in;
        
        public SegmentReader(InputStream in) throws FileNotFoundException 
        {
            Validate.notNull(in, "input stream must not be NULL");
            this.in = in;
        }
        
        public void visit(LoadVisitor visitor) throws IOException 
        {
            int offset = 0;
            while( true ) 
            {
                int read = in.read( headerBuffer , 0, HEADER_SIZE );
                if ( read == -1 ) 
                {
                    visitor.endVisit();
                    return;
                }
                if ( read != HEADER_SIZE ) {
                    throw new IOException("(offset "+offset+") Failed to segment header, expected "+HEADER_SIZE+" bytes but got only "+read);
                }
                offset += HEADER_SIZE;
                final int payloadLen = payloadLength();
                if ( payloadLen > 0 ) 
                {
                    read = in.read( dataBuffer , 0 , payloadLen );
                    if ( read != payloadLen ) {
                        throw new IOException("(offset "+offset+") Failed to read "+payloadLen+" bytes of segment "+type()+", only "+read+" bytes available");
                    }
                    offset += read;
                }
                visitor.visit( this );
            }
        }
        
        @Override
        public int readInt(int offset) {
            return readInt( dataBuffer , offset );
        }
        
        @Override
        public float readFloat(int offset) {
            return readFloat( dataBuffer , offset );
        }
        
        @Override
        public Vector3 readVector3(int offset) 
        {
            final float x = readFloat(offset);
            final float y = readFloat(offset+4);
            final float z = readFloat(offset+8);
            return new Vector3( x,y,z);
        }
        
        @Override
        public int[] readIntArray(int offset) 
        {
            final int len = readInt( offset );
            final int[] result = new int[len];
            for ( int i=0 , ptr = offset+4 ; i < len ; i++ , ptr += 4) {
                result[i] = readInt(ptr);
            }
            return result;
        }
        
        private int readInt(byte[] buffer,int offset) 
        {
            return ( (buffer[offset  ] & 0xff) << 24 ) |
                   ( (buffer[offset+1] & 0xff) << 16 ) |
                   ( (buffer[offset+2] & 0xff) <<  8 ) |
                   ( (buffer[offset+3] & 0xff)       ) ;
        }
        
        private float readFloat(byte[] buffer,int offset) 
        {
            return Float.intBitsToFloat( readInt(buffer,offset ) ); 
        }        

        @Override
        public int type() {
            return readInt( headerBuffer , 0 );
        }
        
        @Override
        public int version() {
            return readInt( headerBuffer , 4 );
        }

        @Override
        public int payloadLength() {
            return readInt( headerBuffer , 8 );
        }

        @Override
        public byte[] payload() {
            return dataBuffer;
        }
    }
    
    public ChunkFile(File file) {
        this.file = file;
    }
    
    @Override
    public boolean equals(Object obj) {
        if ( obj instanceof ChunkFile ) {
            return this.file.equals( ((ChunkFile) obj).file );
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return file.hashCode();
    }
    
    /**
     * Load chunk associated with this file.
     * 
     * @return
     * @throws IOException
     */
    public Chunk load() throws IOException 
    {
        try ( FileInputStream in = new FileInputStream(file) ) {
            return load( in );
        }
    }
    
    /**
     * Load chunk from input stream.
     * 
     * @param in
     * @return
     * @throws IOException
     */
    public static Chunk load(InputStream in) throws IOException 
    {
        final LoadVisitor visitor = new LoadVisitor();        
        new SegmentReader( in ).visit( visitor );
        return visitor.getChunk();
    }
    
    /**
     * Store chunk to associated file.
     * 
     * @param chunk
     * @throws IOException
     */
    public void store(Chunk chunk) throws IOException {
        try ( OutputStream out = new FileOutputStream(file) ) 
        {
            store( chunk , out );
        }
    }
    
    /**
     * Write chunk to output stream.
     * 
     * @param chunk
     * @param out
     * @throws IOException
     */
    public static void store(Chunk chunk,OutputStream out) throws IOException 
    {
        try ( final SegmentWriter writer = new SegmentWriter( out ) ) 
        {
            writer.setType( TYPE_HEADER_SEGMENT );
            writer.setVersion( VERSION_HEADER );
            writer.setPayload( FILE_HEADER_MAGIC );
            writer.writeSegment();
            
            writeChunk( writer , chunk );
        }
    }
}