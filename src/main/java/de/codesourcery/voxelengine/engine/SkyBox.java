package de.codesourcery.voxelengine.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.utils.MeshBuilder;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelengine.model.World;

public class SkyBox implements Disposable {

    private static final float SIZE=WorldRenderer.RENDER_DISTANCE_CHUNKS*World.CHUNK_HALF_WIDTH;
    
    private final Vector3 normal = new Vector3(0,1,0);

    private final Texture front;    
    private final Texture back;
    
    private final Texture left;
    private final Texture right;
    
    private final Texture top;
    private final Texture bottom;
    
    private Mesh mLeft;
    private Mesh mRight;
    
    private Mesh mTop;
    private Mesh mBottom;
    
    private Mesh mFront;
    private Mesh mBack;
    
    private ShaderProgram shader;
    
    private Texture loadTexture(String file) 
    {
        final String path = "textures/skybox/"+file;
        Texture result = new Texture(Gdx.files.classpath( path ), false);
        result.setFilter(TextureFilter.Linear,TextureFilter.Linear);
        result.setWrap(TextureWrap.ClampToEdge,TextureWrap.ClampToEdge);
        return result;
    }
    
    public SkyBox(ShaderManager shaderManager) 
    {
        this.shader = shaderManager.getShader( ShaderManager.SKYBOX_SHADER );
        
        // load textures
        final String suffix = ".jpg";
        front =loadTexture("front"+suffix);
        back = loadTexture("back"+suffix);
        left = loadTexture("right"+suffix);
        right = loadTexture("left"+suffix);
        top = loadTexture("top"+suffix);
        bottom = loadTexture("bottom"+suffix);
        
        final boolean flipFront = true;
        final boolean flipBack = true;
        final boolean flipTop = true;
        final boolean flipBottom = true;
        final boolean flipLeft= true;
        final boolean flipRight = true;
        
        // create box
        final Vector3 v1 = new Vector3();
        final Vector3 v2 = new Vector3();
        final Vector3 v3 = new Vector3();
        final Vector3 v4 = new Vector3();
        
        // back face
        v1.set(SIZE,SIZE,-SIZE); // 00
        v2.set(SIZE,-SIZE,-SIZE); // 10
        v3.set(-SIZE,-SIZE,-SIZE); // 11
        v4.set(-SIZE,SIZE,-SIZE); // 01
        mBack = createMesh(v1,v2,v3,v4,flipBack);
        
        // front face
        v1.set(-SIZE,SIZE,SIZE);
        v2.set(-SIZE,-SIZE,SIZE);
        v3.set(SIZE,-SIZE,SIZE);
        v4.set(SIZE,SIZE,SIZE);
        mFront = createMesh(v1,v2,v3,v4,flipFront);
        
        // left face
        v1.set(-SIZE, SIZE,-SIZE); // 00
        v2.set(-SIZE,-SIZE,-SIZE); // 1
        v3.set(-SIZE,-SIZE,SIZE); // 2
        v4.set(-SIZE, SIZE,SIZE); // 3
        mLeft = createMesh(v1,v2,v3,v4,flipLeft);
        
        // right face
        v1.set(SIZE,SIZE,SIZE);
        v2.set(SIZE,-SIZE,SIZE);
        v3.set(SIZE,-SIZE,-SIZE);
        v4.set(SIZE,SIZE,-SIZE);
        mRight = createMesh(v1,v2,v3,v4,flipRight);       
        
        // top face
        v1.set(-SIZE,SIZE,-SIZE);
        v2.set(-SIZE,SIZE,SIZE);
        v3.set(SIZE,SIZE,SIZE);
        v4.set(SIZE,SIZE,-SIZE);
        mTop = createMesh(v1,v2,v3,v4,flipTop); 
        
        // bottom face
        v1.set(-SIZE,-SIZE,SIZE);
        v2.set(SIZE,-SIZE,SIZE);
        v3.set(SIZE,-SIZE,-SIZE);
        v4.set(-SIZE,-SIZE,-SIZE);
        mBottom = createMesh(v1,v2,v3,v4,flipBottom);  
    }
    
    private Mesh createMesh(Vector3 v1,Vector3 v2,Vector3 v3,Vector3 v4,boolean flip) {
        
        final MeshBuilder builder = new MeshBuilder();
        builder.begin( MeshBuilder.createAttributes( Usage.Position | Usage.TextureCoordinates | Usage.Normal ) , GL20.GL_TRIANGLES );
        if ( flip ) {
            builder.rect( v4,v3,v2,v1,normal);
        } else {
            builder.rect( v1,v2,v3,v4,normal);
        }
        return builder.end();
    }
    
    public void render(Camera camera) {
    
        Gdx.gl20.glDepthMask(false);
        Gdx.gl20.glDisable(GL20.GL_CULL_FACE);

        shader.begin();
        
        shader.setUniformf("cameraTranslation", camera.position );
        shader.setUniformMatrix("mvp" , camera.combined );
        shader.setUniformi("colorMap" , 0 );
        
        back.bind(0);       
        mFront.render( shader , GL20.GL_TRIANGLES );    
        
        front.bind(0);      
        mBack.render( shader , GL20.GL_TRIANGLES );
        
        right.bind(0);      
        mRight.render( shader , GL20.GL_TRIANGLES );        
        
        left.bind(0);       
        mLeft.render( shader , GL20.GL_TRIANGLES );     
        
        top.bind(0);        
        mTop.render( shader , GL20.GL_TRIANGLES );      
        
        bottom.bind(0);     
        mBottom.render( shader , GL20.GL_TRIANGLES );           
        
        shader.end();
        
        if ( WorldRenderer.DEPTH_BUFFER ) {
            Gdx.gl20.glDepthMask(true);
        }
        if ( WorldRenderer.CULL_FACES) {
            Gdx.gl20.glEnable(GL20.GL_CULL_FACE);
        }
    }

    @Override
    public void dispose() 
    {
        front.dispose();
        back.dispose();
        left.dispose();
        right.dispose();
        top.dispose();
        bottom.dispose();
        
        mFront.dispose();
        mBack.dispose();
        mLeft.dispose();
        mRight.dispose();
        mTop.dispose();
        mBottom.dispose();
    }
}
