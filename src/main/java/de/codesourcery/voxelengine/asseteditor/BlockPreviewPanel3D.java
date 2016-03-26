package de.codesourcery.voxelengine.asseteditor;

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

public class BlockPreviewPanel3D extends JPanel 
{
    private static final float CUBE_SIZE = 50;
    
    private final JFXPanel fxPanel;

    public BlockPreviewPanel3D() 
    {
        fxPanel = new JFXPanel();
        setLayout( new BorderLayout() );
        add( fxPanel , BorderLayout.CENTER );
    }

    public void init() 
    {
        Platform.runLater(new Runnable() 
        {
            @Override
            public void run() 
            {
                fxPanel.setScene( createScene() );
            }
        }); 
    }

    public static void main(String[] args) 
    {
        final Runnable r = () -> 
        {
            final JFrame frame = new JFrame("Swing and JavaFX");
            final BlockPreviewPanel3D panel = new BlockPreviewPanel3D();
            frame.add(panel);
            frame.setSize(300, 200);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo( null );
            frame.setVisible(true);
            panel.init();
        };
        SwingUtilities.invokeLater( r );
    }

    public static Scene createScene() 
    {
        final Cube cube = new Cube();
        final Group root = new Group(cube);

        // create scene
        final Scene scene = new Scene(root, 400,150);
        
        final PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-150);
        camera.setNearClip( 0.1 );
        camera.setFarClip( 500 );
        scene.setCamera(camera);

        // setup animation
        final Timeline animation = new Timeline();
        animation.getKeyFrames().addAll(
                new KeyFrame(Duration.ZERO       , new KeyValue(cube.ry.angleProperty(),   0d) ),
                new KeyFrame(Duration.seconds(20), new KeyValue(cube.ry.angleProperty(), 360d) 
                        ));
        animation.setCycleCount(Animation.INDEFINITE);        
        animation.play();
        return scene;
    }

    public static class Cube extends Group 
    {
        final Rotate ry = new Rotate(0,Rotate.Y_AXIS);

        private TriangleMesh createMesh(float w, float h, float d) {

            float hw = w / 2f;
            float hh = h / 2f;
            float hd = d / 2f;

            float points[] = {
                    -hw, -hh, -hd,
                    hw, -hh, -hd,
                    hw,  hh, -hd,
                    -hw,  hh, -hd,
                    -hw, -hh,  hd,
                    hw, -hh,  hd,
                    hw,  hh,  hd,
                    -hw,  hh,  hd};

            float texCoords[] = {0, 0, 1, 0, 1, 1, 0, 1};

            int faceSmoothingGroups[] = {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            };

            int faces[] = {
                    0, 0, 2, 2, 1, 1,
                    2, 2, 0, 0, 3, 3,            
                    1, 0, 6, 2, 5, 1,
                    6, 2, 1, 0, 2, 3,            
                    5, 0, 7, 2, 4, 1,
                    7, 2, 5, 0, 6, 3,
                    4, 0, 3, 2, 0, 1,
                    3, 2, 4, 0, 7, 3,            
                    3, 0, 6, 2, 2, 1,
                    6, 2, 3, 0, 7, 3,
                    4, 0, 1, 2, 5, 1,
                    1, 2, 4, 0, 0, 3,
            };

            TriangleMesh mesh = new TriangleMesh();
            mesh.getPoints().setAll(points);
            mesh.getTexCoords().setAll(texCoords);
            mesh.getFaces().setAll(faces);
            mesh.getFaceSmoothingGroups().setAll(faceSmoothingGroups);

            return mesh;
        }        
        public Cube() 
        {
            getTransforms().addAll(ry);

            final MeshView view = new MeshView( createMesh(CUBE_SIZE,CUBE_SIZE,CUBE_SIZE) );

            view.setDrawMode(DrawMode.FILL);
            //            view.setMaterial(blueStuff);

            getChildren().add( view );
        }
    }
}