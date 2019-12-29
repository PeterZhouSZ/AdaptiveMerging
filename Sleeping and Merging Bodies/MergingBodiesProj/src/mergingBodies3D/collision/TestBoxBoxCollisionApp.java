package mergingBodies3D.collision;

import java.util.ArrayList;

import javax.swing.JPanel;
import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;

import mintools.parameters.DoubleParameter;
import mintools.parameters.Vec3Parameter;
import mintools.swing.VerticalFlowPanel;
import mintools.viewer.EasyViewer;
import mintools.viewer.SceneGraphNode;

/**
 * This is a quick test of a port of the box box collision detection of a java port of ODE, now working!
 * @author kry
 */
public class TestBoxBoxCollisionApp implements SceneGraphNode{

	public static void main( String[] args ) {
		new EasyViewer("test box box collision", new TestBoxBoxCollisionApp() );
	}
	
	public TestBoxBoxCollisionApp() {
		// not much to do here
	}
	
	Vector3d size1 = new Vector3d( 1,2,3 );
	Vector3d size2 = new Vector3d( 2,3,4 );
	
	@Override
	public void display(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		gl.glDisable( GL2.GL_LIGHTING );

		Vector3d p1 = new Vector3d( pos1.x, pos1.y, pos1.z );
		Vector3d a1 = new Vector3d( axis1.x, axis1.y, axis1.z );
		a1.normalize();
		AxisAngle4d aa1 = new AxisAngle4d( a1, angle1.getValue() );
		Matrix3d R1 = new Matrix3d();
		R1.set( aa1 );
		Vector3d p2 = new Vector3d( pos2.x, pos2.y, pos2.z );
		Vector3d a2 = new Vector3d( axis2.x, axis2.y, axis2.z );
		a2.normalize();
		AxisAngle4d aa2 = new AxisAngle4d( a2, angle2.getValue() );
		Matrix3d R2 = new Matrix3d();
		R2.set( aa2 );
		
		ArrayList<DContactGeom> contacts = new ArrayList<DContactGeom>();
		Vector3d normal = new Vector3d();
		double[] depth = new double[1];
		int[] return_code = new int[1];
		int flags = 0xffff; // as many contacts as we can get!
		int skip = 1; // only use skip if we want to dump other tests into the same arraylist
		
		int cnum = BoxBox.dBoxBox( p1, R1, size1, p2, R2, size2, normal, depth, return_code, flags, contacts, skip );

		gl.glPointSize(10);
		gl.glLineWidth(3);
		int i = 0; 
		for ( DContactGeom c : contacts ) {
			c.info = i++; // cheap way to keep track of contacts for warm starts?
			gl.glColor3f(1, 0, 0);
			gl.glBegin(GL.GL_POINTS);
			gl.glVertex3d( c.pos.x, c.pos.y, c.pos.z );
			gl.glEnd();
			gl.glBegin(GL.GL_LINES);
			gl.glVertex3d( c.pos.x, c.pos.y, c.pos.z );
			double x = normal.x;
			double y = normal.y;
			double z = normal.z;
			gl.glVertex3d( x + c.pos.x, y + c.pos.y, z + c.pos.z );
			gl.glVertex3d( c.normal.x + c.pos.x, c.normal.y + c.pos.y, c.normal.z + c.pos.z );
			gl.glEnd();
			gl.glColor3f( 1,1,1 );
			gl.glRasterPos3d( c.pos.x, c.pos.y, c.pos.z );
			EasyViewer.glut.glutBitmapString(GLUT.BITMAP_8_BY_13, "  " + c.info + " " + c.depth ); 
//			EasyViewer.glut.glutBitmapString(GLUT.BITMAP_8_BY_13, c.side1 + " " + c.side2 ); 
			// side is unused :(  but doesn't need to be... 
			// thus we are somewhat on our own for warmstarts..
		}
		
		gl.glColor4f(1,1,1,0.5f);
		gl.glLineWidth(1);
	
		gl.glPushMatrix();
		gl.glTranslated( pos1.x, pos1.y, pos1.z );
		gl.glRotated(angle1.getValue()*180/Math.PI, axis1.x, axis1.y, axis1.z);
		gl.glScaled( size1.x, size1.y, size1.z );
		EasyViewer.glut.glutWireCube(1);
		gl.glPopMatrix();
		
		gl.glPushMatrix();
		gl.glTranslated( pos2.x, pos2.y, pos2.z );
		gl.glRotated(angle2.getValue()*180/Math.PI, axis2.x, axis2.y, axis2.z);
		gl.glScaled( size2.x, size2.y, size2.z );
		EasyViewer.glut.glutWireCube(1);
		gl.glPopMatrix();
		
		gl.glColor4f(1,1,1,1);

		String msg = "#contacts = " + cnum + "\n" +
				"depth = " + depth[0] + "\n" +
				"normal = " + normal.toString() + "\n" +
				"code = " + return_code[0];
		EasyViewer.beginOverlay(drawable);
		EasyViewer.printTextLines(drawable, msg);
		EasyViewer.endOverlay(drawable);
	}
	
	@Override
	public JPanel getControls() {
		VerticalFlowPanel vfp = new VerticalFlowPanel();
		vfp.add( pos1 );
		vfp.add( axis1 );
		vfp.add( angle1.getSliderControls(false) );
		vfp.add( pos2 );
		vfp.add( axis2 );
		vfp.add( angle2.getSliderControls(false) );
		return vfp.getPanel();
	}
	
	Vec3Parameter pos1 = new Vec3Parameter("pos1", 0,  1, 0 );
	Vec3Parameter pos2 = new Vec3Parameter("pos2", 0, -1, 0 );
	Vec3Parameter axis1 = new Vec3Parameter("axis1", 0,  1, 0 );
	Vec3Parameter axis2 = new Vec3Parameter("axis2", 0,  1, 0 );
	DoubleParameter angle1 = new DoubleParameter("angle1", 0, -3.14, 3.14 );
	DoubleParameter angle2 = new DoubleParameter("angle2", 0, -3.14, 3.14 );
	
	@Override
	public void init(GLAutoDrawable drawable) {
		// nothing to do
	}

}
