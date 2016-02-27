package de.codesourcery.voxelengine.shaders;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

public class ShaderManager implements Disposable
{
    private static final Logger LOG = Logger.getLogger(ShaderManager.class);

    public static final String FLAT_SHADER = "flatsolid";
    public static final String WIREFRAME_SHADER = "wireframe";
    
    private final Map<String,ShaderProgram> shaders = new HashMap<>();

    public ShaderProgram getShader(String name) 
    {
        ShaderProgram program = shaders.get(name);
        if ( program == null ) 
        {
            final String vertexPath = "/shaders/"+name+".vertex";
            final String fragmentPath = "/shaders/"+name+".fragment";
            try {
                program = new ShaderProgram( read( vertexPath ) , read( fragmentPath ) );
            } 
            catch (Exception e) 
            {
                LOG.error("getShader(): Failed to load shader '"+name+"'");
                throw new RuntimeException("Failed to load shader '"+name+"'",e);
            }
            
            if ( ! program.isCompiled() ) 
            {
                LOG.error("getShader(): Failed to compile shader '"+name+"': \n\n"+program.getLog());
                throw new RuntimeException("Failed to compile shader '"+name+"'");
            }
            shaders.put( name ,  program );
        }
        return program;
    }
    
    private static String read(String classPath) throws IOException 
    {
        LOG.debug("read(): Reading classpath:"+classPath);
        final InputStream in = ShaderManager.class.getResourceAsStream(classPath);
        if ( in == null ) {
            LOG.error("read(): Failed to load shader from classpath:"+classPath);
            throw new FileNotFoundException("Failed to load shader from classpath:"+classPath);
        }
        try 
        {
            final BufferedReader reader = new BufferedReader( new InputStreamReader(in ) );
            final StringBuilder buffer = new StringBuilder();
            String line;
            while ( (line=reader.readLine()) != null ) {
                buffer.append( line ).append("\n");
            }
            return buffer.toString();
        } 
        finally 
        {
            try {
                in.close();
            } catch (IOException e) { /* can't help it */ }
        }
    }

    @Override
    public void dispose() {
        shaders.values().forEach( shader ->shader.dispose() );
        shaders.clear();
    }
}
