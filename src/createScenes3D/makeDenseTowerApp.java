package createScenes3D;

import java.io.PrintStream;
import java.util.Random;

public class makeDenseTowerApp {

	public static void main( String[] args ) {	
		System.out.print("hi");
		try {
			PrintStream ps = new PrintStream("scenes3D/denseTower25.xml");
			ps.println("<root>");
			// rotate blocks about y as we go around a circle of a 
			// given radius
			double r = 10;
			int layers = 6;
			int sx = 2;
			int sy = 2;
			int sz = 4; 

			int floory = -10;
			String dim = "dim=\"" + asString(sx, sy, sz) + "\"";
			// leave room to add a bouncy plane by hand...  ?
 			int space = 0;
			ps.println("<body type=\"plane\" name=\"plane1\" p=\"0 "+(floory-space)+" 0\" n=\"0 1 0\"></body>");
			// first layer of blocks above the floor, taking into account
			// the sqrt2 bounding sphere that goes slightly outside blocks
			float y = (float) ( floory + sy*0.5 + (Math.sqrt(2)-1) );
			// number of blocks that can fit around the circumference
			// with a small safety factor?  
			int N = (int) Math.floor( Math.PI * 2 * r / (sz * 1.2) );			
			for ( int j = 0; j < layers; j++ ) {
				String density =  "density=\"" + Integer.toString(1) + "\"";
				if (j%2 == 0) {
					density="density=\"" + Integer.toString(100)+ "\"";
				}
				for ( int i = 0; i < N; i++ ) {
					double radians = 2.0 * Math.PI * i / N;
					if ( j%2 == 1 ) {
						radians += Math.PI / N;
					}
					double x = Math.cos( radians ) * r;
					double z = Math.sin( radians ) * r;
					String name = "B" + i + "L" + j;
					ps.println("<body type=\"box\" name=\""+name+"\" " + dim + " " +density + ">" );
					ps.println("    <x> " + asString(x,y,z) + " </x>" );
					ps.println("    <R> 0 -1 0 " + radians + "</R>" );
					if (j%2 == 0) {
						ps.println("    <col> " + "0 0 0" + "</col>" );
					}else {
						int ind = rand.nextInt( col.length );
						ps.println("    <col> " + col[ind] + "</col>" );	
					}

					ps.println("</body>");
				}
				y = y + (float)(sy + (Math.sqrt(2)-1));
			}			
			ps.println("</root>");			
			ps.close();
		} catch (Exception e ) {
			e.printStackTrace();
		}
	}
	
	static Random rand = new Random();
	static String[] col = new String[] {
		"0.8 0.5 0.5",
		"0.5 0.5 0.8",
		"0.5 0.8 0.5",
		"0.8 0.8 0.5",
		"0.5 0.8 0.8",
		"0.8 0.5 0.8",
	};
	
	public static String asString( double sx, double sy, double sz ) {
		return  sx + "  " + sy + " " + sz; 
	}
	
	public static String asString( int sx, int sy, int sz ) {
		return  sx + "  " + sy + " " + sz; 
	}
}
