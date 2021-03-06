package de.codesourcery.voxelengine.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import com.badlogic.gdx.math.Vector3;

import de.codesourcery.voxelengine.model.Chunk;
import de.codesourcery.voxelengine.model.ChunkKey;
import de.codesourcery.voxelengine.model.World;

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
        public int readInt();

        /*
         * Read float from payload.
         */
        public float readFloat();

        public Vector3 readVector3();

        public int[] readIntArray();

        public byte[] readByteArray();
    }    

    public static final class LoadVisitor  
    {
        private boolean headerFound;
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
                        topLevelChunk = readChunk( t );
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
            if ( ! headerFound ) {
                throw new RuntimeException("Internal error,file contains no header?");
            }
            if ( topLevelChunk == null ) {
                throw new RuntimeException("Internal error,file contains no top-level chunk?");
            }
        }

        public Chunk getChunk() 
        {
            if ( topLevelChunk == null ) {
                throw new IllegalStateException("Reading top-level chunk file failed");
            }
            return topLevelChunk;
        }
    }

    private static Chunk readChunk(Segment s) {

        final int totalChunkSize = s.readInt();
        final float blockSize = s.readFloat();
        if ( totalChunkSize != World.CHUNK_SIZE ) {
            throw new RuntimeException("Internal error, file has incompatible chunk size "+totalChunkSize);
        }
        if ( blockSize != World.BLOCK_SIZE ) {
            throw new RuntimeException("Internal error, file has incompatible chunk block size "+blockSize);
        }        
        final int flags = s.readInt();
        final int[] blockTypes = s.readIntArray();
        final byte[] lightLevels = s.readByteArray();

        final int chunkX = s.readInt();
        final int chunkY = s.readInt();
        final int chunkZ = s.readInt();
        final ChunkKey chunkKey =  new ChunkKey( chunkX ,chunkY,chunkZ ) ;

        final Chunk result = new Chunk( chunkKey , blockTypes , lightLevels );
        result.flags = flags;
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
        writer.writeInt( World.CHUNK_SIZE );
        writer.writeFloat( World.BLOCK_SIZE );
        writer.writeInt( chunk.flags & ~Chunk.FLAG_NEEDS_SAVE );
        writer.writeIntArray( chunk.blockTypes );
        writer.writeByteArray( chunk.lightLevels );
        writer.writeInt( chunk.chunkKey.x );
        writer.writeInt( chunk.chunkKey.y );
        writer.writeInt( chunk.chunkKey.z );
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
            maybeGrowDataBuffer(data.length);
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
            final int bytesLeft = dataBuffer.length - payloadLength;
            if ( bytesLeft < minBytesFree ) 
            {
                final int newSize = dataBuffer.length*2 + minBytesFree;
                final byte[] tmp = new byte[ newSize ];
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
        
        public void writeByteArray(byte[] array) 
        {
            maybeGrowDataBuffer( 4 + array.length ); // int<array length> + 32 bit * array.len 
            writeByteArray( array, dataBuffer ,payloadLength );
            payloadLength+= 4+array.length;
        }      
        
        public void writeByteArray(byte[] array,byte[] buffer,int offset) 
        {
            writeInt( array.length , buffer ,offset );
            System.arraycopy( array , 0 , buffer , offset+4 , array.length);
        }          

        public void writeIntArray(int[] array) 
        {
            maybeGrowDataBuffer( 4 + array.length*4 ); // int<array length> + 32 bit * array.len 
            writeIntArray( array, dataBuffer ,payloadLength );
            payloadLength+= 4+array.length*4;
        }   
        
        public void writeIntArray(int[] array,byte[] buffer,int offset) 
        {
            writeInt( array.length , buffer ,offset );
            for ( int i = 0 , ptr = offset+4 , len = array.length ; i < len ; i++, ptr+=4 ) 
            {
                writeInt( array[i] , buffer , ptr );
            }
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

        public void close() throws IOException {
            out.close();
        }
    }

    protected static final class SegmentReader implements Segment 
    {
        private final byte[] headerBuffer = new byte[ HEADER_SIZE ];
        private byte[] dataBuffer = new byte[ 64*1024 ];

        private int dataReadOffset;
        
        private final InputStream in;

        public SegmentReader(InputStream in) throws FileNotFoundException 
        {
            Validate.notNull(in, "input stream must not be NULL");
            this.in = in;
        }

        private void maybeGrowDataBuffer(int minSize) 
        {
            if ( dataBuffer.length < minSize ) {
                dataBuffer = new byte[ minSize ];
            }
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
                    maybeGrowDataBuffer( payloadLen );
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
        public int readInt() {
            int result = readInt( dataBuffer , dataReadOffset );
            dataReadOffset+=4;
            return result;
        }
        
        private int readInt(byte[] buffer,int offset) 
        {
            return ( (buffer[offset  ] & 0xff) << 24 ) |
                    ( (buffer[offset+1] & 0xff) << 16 ) |
                    ( (buffer[offset+2] & 0xff) <<  8 ) |
                    ( (buffer[offset+3] & 0xff)       ) ;
        }        
        
        private byte readByte() 
        {
            byte result = dataBuffer[ dataReadOffset ];
            dataReadOffset++;
            return result;
        }

        @Override
        public float readFloat() 
        {
            int result = readInt();
            return Float.intBitsToFloat( result );
        }

        @Override
        public Vector3 readVector3() 
        {
            final float x = readFloat();
            final float y = readFloat();
            final float z = readFloat();
            return new Vector3( x,y,z);
        }
        
        @Override
        public byte[] readByteArray() {
            final int len = readInt();
            final byte[] result = new byte[len];
            for ( int i=0 ; i < len ; i++ ) {
                result[i] = readByte();
            }
            return result;
        }

        @Override
        public int[] readIntArray() 
        {
            final int len = readInt();
            final int[] result = new int[len];
            for ( int i=0 ; i < len ; i++ ) {
                result[i] = readInt();
            }
            return result;
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
            chunk.clearFlags( Chunk.FLAG_NEEDS_SAVE );    
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
        chunk.clearFlags( Chunk.FLAG_NEEDS_SAVE );
    }
}