package src;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

public class RigidCollection extends RigidBody{
	
	public LinkedList<RigidBody> colRemovalQueue = new LinkedList<RigidBody>();

	ArrayList<RigidBody> collectionBodies = new ArrayList<RigidBody>();
	
	//ArrayList<Contact> inner_contacts = new ArrayList<Contact>();
	
	ArrayList<BodyContact> internalBodyContacts = new ArrayList<BodyContact>();
	ArrayList<Contact> internalContacts = new ArrayList<Contact>();
	
	boolean unMergedThisTimestep = false;
	
	boolean updatedThisTimeStep = false;

	
	public RigidCollection(RigidBody body1, RigidBody body2) {
		
		super(body1); // this will copy the blocks, which is not exactly what we want... fine though.
		
		clearUselessJunk();
		
		index = body1.index;
		collectionBodies.add(body1);
		collectionBodies.add(body2);
		body1.bodyContactListPreMerging.clear();
		body2.bodyContactListPreMerging.clear();
		body1.bodyContactListPreMerging.addAll(body1.bodyContactList);
		body2.bodyContactListPreMerging.addAll(body2.bodyContactList);
		
		setupCollection();
		
		body1.parent = this;
		body2.parent = this;
		body1.merged = true;
		body2.merged = true;
		
	}

	
	private void clearUselessJunk() {
		blocks.clear();
		boundaryBlocks.clear();
		
		velHistory.clear();
		//no longer relevant
		contactList.clear();
	
		savedContactForce.set(0, 0);
		contactTorques = 0;
		springs.clear();
		
		
	}


	/**
	 * adds Body to the collection
	 * @param bc 
	 */
	public void addBody(RigidBody body) {
		body.bodyContactListPreMerging.addAll(body.bodyContactList);
		collectionBodies.add(body);
		setupCollection();
		
		body.parent = this;
		body.merged = true;
		
	}
	
	
	
	public void addInternalContact(BodyContact bc) {
		if (!internalBodyContacts.contains(bc))
			internalBodyContacts.add(bc);
		if (!bc.body1.bodyContactList.contains(bc)) {
			bc.body1.bodyContactList.add(bc);
		}
		if (!bc.body2.bodyContactList.contains(bc)) {
			bc.body2.bodyContactList.add(bc);
		}
		
		//convert the ontacts to collections coordinates. 
		for (Contact c: bc.contactList)c.getHistoryStatistics();
		
		internalContacts.addAll(bc.contactList);
	
	
		

	}
	//like addBody but with another collection...
	public void addCollection(RigidCollection col) {
		LinkedList<RigidBody> additionQueue = new LinkedList<RigidBody>();
		for (RigidBody b : col.collectionBodies) {
			//transform all the subBodies to their world coordinates... 
			b.merged = true;
			col.transformB2W.transform(b.x);
			b.theta = b.transformB2W.getTheta();
			b.transformB2C.T.setIdentity();
			b.transformC2B.T.setIdentity();
			b.parent = null;
			additionQueue.add(b);
			
			
		}
		
		for (RigidBody b: additionQueue) {
			b.bodyContactListPreMerging.addAll(b.bodyContactList);
			collectionBodies.add(b);
		}
		//col.collectionBodies.clear();
		
		setupCollection();
		
		// handle the internal contacts of col... they no longer beloogn
		internalBodyContacts.addAll(col.internalBodyContacts);
		
		for (Contact c: col.internalContacts) {
			
			internalContacts.add(c);
			col.transformB2W.transform(c.contactW);
			col.transformB2W.transform(c.normal);
		}
	}
	
	/** 
	 * seperates each collectionBody from its parent so the bodies are ready to be added back to the system individually
	 * x positions now need to be in world coordinates etc.
	 */
	public void unmergeAllBodies() {
		internalBodyContacts.clear();
		for (RigidBody b: collectionBodies) {
			transformB2W.transform(b.x);
			b.theta = b.transformB2W.getTheta();
			b.transformB2C.T.setIdentity();
			b.transformC2B.T.setIdentity();
			b.v.set(v);
			b.omega = omega;
			b.parent = null;
		}
		
	}
	
	@Override
	public void advanceTime(double dt){
    	
		if (!pinned) {
				    	
			v.x += force.x * dt/massLinear + delta_V.get(0);
	    	v.y += force.y * dt/massLinear + delta_V.get(1);
	    	omega += torque * dt/ massAngular + delta_V.get(2);
	    	
	       	x.x += v.x * dt;
	    	x.y += v.y * dt;
	    	theta += omega*dt;
	    	
	    	updateTransformations();
	    	updateCollectionBodyTransformations(dt);
	    		    
		}

	}
	
	//applies springs on the body, to the collection
	private void addSprings() {
		ArrayList<Spring> newSprings = new ArrayList<Spring>();
		
		for (RigidBody body: collectionBodies) {
			for (Spring s: body.springs) {
				Spring newSpring = new Spring(s, this);
				newSprings.add(newSpring);
			
			}
		}
	
		this.springs.addAll(newSprings);
		
	}

	@Override
    /**
     * Updates the B2W and W2B transformations
     */
    public void updateTransformations() {
        transformB2W.set( theta, x );
        transformW2B.set(transformB2W);
        transformW2B.invert();
      
    }
	
	private void updateCollectionBodyTransformations(double dt) {
		//WHY doesn't this work!
		for (RigidBody b: collectionBodies) {
	       	b.transformB2W.set(b.transformB2C);
	       	b.transformB2W.leftMult(transformB2W);
	       	b.transformW2B.set(b.transformB2W); b.transformW2B.invert();
		}

    	
	}
    
	/*For each body in collection, determine the transformations to go from body to collection
	*But also, make each body's x, in collection and theta in collection, relative to this x and theta
	*/
	
	private void setMergedTransformationMatrices() {
				
		for (RigidBody body: collectionBodies) {
			body.transformB2C.set(body.transformB2W);
			body.transformB2C.leftMult(transformW2B);
			body.transformC2B.set(body.transformB2C); body.transformC2B.invert();
	
	
		}

		
		
	}
	
	/*
	 * transforms body x's and thetas in world coordinates into collection cooridnates
	 */
	private void transformToCollectionCoords(){
		RigidTransform temp = new RigidTransform();
		for (RigidBody b : collectionBodies) {
			
			transformW2B.transform(b.x);
			
		 	b.theta =  b.transformB2C.getTheta();
		}
	}
	
	@Override
    public void display( GLAutoDrawable drawable ) {
		GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        gl.glTranslated( x.x, x.y, 0 );
        gl.glRotated(theta*180/Math.PI, 0,0,1);
        
        for (RigidBody b: collectionBodies) {
        	b.display(drawable);
        }
        gl.glPopMatrix();
	}


	public void calculateMass() {
		double mass = 0;
		for(RigidBody b: collectionBodies) {
			mass += b.massLinear;
		}
		massLinear = mass;
		minv = 1/mass;
	}
	

	
	/**
	 * Loops through all bodies in collectionBodies and sets the transformation matrices for each
	 */
	public void calculateCOM() {
		Vector2d com = new Vector2d();
		Vector2d bCOM = new Vector2d();
		double totalMass = massLinear;
		com.set(0,0);
	
		
		for (RigidBody b: collectionBodies) {
			double bRatio = b.massLinear/totalMass;
			if (b.parent != null) {
				//transform back to world if the body is in collection coordinates... dont worry, very temporary
				b.parent.transformB2W.transform(b.x);
				
			}
			bCOM.scale(bRatio, b.x);
			com.add(bCOM);
			
			
		
		}
		
	
	//	set collections new transformations...
		x.set(com);
		
	    transformB2W.set( theta, x );
	    transformW2B.set( transformB2W);
	    transformW2B.invert();
	}
	
	/**
	 * Calculates the angular inertia. 
	 */
	public void calculateInertia() {
		double inertia = 0;
		Point2d bpB = new Point2d(0, 0);
		Point2d zero = new Point2d(0, 0);
		for (RigidBody body: collectionBodies) {
			   for ( Block b : body.blocks ) {
		            double mass = b.getColourMass();
		            bpB.set(b.pB);
		            body.transformB2C.transform(bpB);
		            //b.pb is in c
		            inertia += mass*bpB.distanceSquared(zero);
		            
		        }
			   body.parent = this;
		
		}
		massAngular = inertia;
		jinv = 1/inertia;
		
	}

    /**
     * Draws the center of mass position with a circle.  
     * @param drawable
     */
    public void displayCOM( GLAutoDrawable drawable ) {
        GL2 gl = drawable.getGL().getGL2();
          
        if ( this.active==0 || this.woken_up) {
            gl.glPointSize(8);
            gl.glColor3f(0,0,0.7f);
            gl.glBegin( GL.GL_POINTS );
            gl.glVertex2d(x.x, x.y);
            gl.glEnd();
            gl.glPointSize(4);
            gl.glColor3f(1,1,1);
            gl.glBegin( GL.GL_POINTS );
            gl.glVertex2d(x.x, x.y);
            gl.glEnd();
        }else {
            gl.glPointSize(8);
            gl.glColor3f(0,0,0.7f);
            gl.glBegin( GL.GL_POINTS );
            gl.glVertex2d(x.x, x.y);
            gl.glEnd();
            gl.glPointSize(4);
            gl.glColor3f(0,0,1);
            gl.glBegin( GL.GL_POINTS );
            gl.glVertex2d(x.x, x.y);
            gl.glEnd();
        }

    }
    /** Map to keep track of display list IDs for drawing our rigid bodies efficiently */
    static private HashMap<ArrayList<Block>,Integer> mapBlocksToDisplayList = new HashMap<ArrayList<Block>,Integer>();
    
    /** display list ID for this rigid body */
    int myListID = -1;
    
    //list of bodies to be added to this collection in the next timestep
	ArrayList<RigidBody> bodyQueue = new ArrayList<RigidBody>();

    
    /**
     * displays the Body Collection as lines between the center of masses of each rigid body to the other. 
     * Uses a string arrayList to check if a connection has already been drawn.
     * @param drawable
     */
    
    public void displayCollection( GLAutoDrawable drawable ) {
        GL2 gl = drawable.getGL().getGL2();
        
        // draw a line between the two bodies but only if they're both not pinned
        Point2d p1 = new Point2d();
        Point2d p2 = new Point2d();
        for (BodyContact bc: internalBodyContacts) {
        	gl.glLineWidth(5);
			gl.glColor4f(0, 1,0, 1.0f);
			gl.glBegin( GL.GL_LINES );
			p1.set(bc.body1.x);
			p2.set(bc.body2.x);
			if (bc.body1.parent !=null)
			bc.body1.parent.transformB2W.transform(p1);
			if (bc.body2.parent != null)
			bc.body2.parent.transformB2W.transform(p2);
			gl.glVertex2d(p1.x, p1.y);
			gl.glVertex2d(p2.x, p2.y);
			gl.glEnd();

        }

        
    }

    
    /*
     * goes through each body in collection and sees if it should be unmerged. Fill the removal queue with the bodies that need to be unmerged
     */
    public void fillNewBodiesQueue(Vector2d totalForce, double totalTorque, double forceMetric){
    	LinkedList<RigidBody> additionQueue = new LinkedList<RigidBody>();
    	LinkedList<RigidBody> removalQueue = new LinkedList<RigidBody>();
    	
    	for (RigidBody sB: collectionBodies) {
			
			checkMetric(sB, totalForce, totalTorque, forceMetric);
			if (!newRigidBodies.isEmpty()) 
				for(RigidBody b : newRigidBodies) {
					if (b instanceof RigidCollection) {
						RigidCollection colB = (RigidCollection) b;
						colB.fillNewBodiesQueue(totalForce, totalTorque, forceMetric);
						if (!colB.newRigidBodies.isEmpty()) {
							additionQueue.addAll(colB.newRigidBodies);
							removalQueue.add(b);
						}
					}
				}
			break;
		}
    	
    	newRigidBodies.addAll(additionQueue);
    	for (RigidBody element : removalQueue)
    	newRigidBodies.remove(element);
    	

    }
    
 public boolean  metricCheck(RigidBody sB , Vector2d totalForce, double totalTorque) {
	 	totalForce.set(sB.force);
		sB.transformB2W.transform(sB.currentContactForce);
	
		
		totalForce.add(sB.currentContactForce);

		sB.transformW2B.transform(sB.currentContactForce);
		sB.deltaF.set(totalForce);
		totalTorque = sB.torque + sB.currentContactTorques;
		double threshold = CollisionProcessor.forceMetricTolerance.getValue();
		double forceMetric = totalForce.x/sB.massLinear;
		double forceMetric2 = totalForce.y/sB.massLinear;
		double forceMetric3 = totalTorque/sB.massAngular;
		if (Math.abs(forceMetric) > threshold || Math.abs(forceMetric2) > threshold || Math.abs(forceMetric3) > threshold) 
		return true;
		else return false;
 }
    /*c
     * checks if body sB is going to unmerge by comparing the acceleration vector magnitude with a threshold
     */
    
  public void checkMetric(RigidBody sB, Vector2d totalForce, double totalTorque, double forceMetric) {
    	totalForce.set(sB.force);
		sB.transformB2W.transform(sB.savedContactForce);
	
		
		totalForce.add(sB.savedContactForce);

		sB.transformW2B.transform(sB.savedContactForce);
		totalTorque = sB.torque + sB.contactTorques;
		forceMetric = Math.sqrt(Math.pow(totalForce.x,2 ) + Math.pow(totalForce.y, 2))/sB.massLinear + Math.sqrt(Math.pow(totalTorque, 2))/sB.massAngular;
		
		if (forceMetric > CollisionProcessor.forceMetricTolerance.getValue()) {
			handledBodies.add(sB);
			newRigidBodies.add(sB);
			unmergeSingleBody(sB);
			contactsToWorld();
			dealWithNeighbors(sB);
			
			//sB.unmergeBodyContacts();
			//checkSubBodyNeighbors(sB, totalForce, totalTorque, forceMetric);
		}
	}

  /*
   * makes body ready to be used by system... converts everything to world coordinates and makes body independant of collection
   * ... does not do anything to the collection itself.
   */
    public void unmergeSingleBody(RigidBody sB) {
		if (sB.parent == null) return;
		else {
	
			sB.parent.transformB2W.transform(sB.x);
			sB.theta = sB.transformB2W.getTheta();
			sB.transformB2C.T.setIdentity();
			sB.transformC2B.T.setIdentity();
			sB.v.set(sB.parent.v);
			sB.omega = sB.parent.omega;
			sB.parent = null;
			
			
		}
	
}
private void removeBodyContact(BodyContact bc) {
		internalBodyContacts.remove(bc);
	}
//contains all the new RigidBodies that result after this unmerging
ArrayList<RigidBody> newRigidBodies = new ArrayList<RigidBody>();

//the bodies here have been handled in this unmerging step...
ArrayList<RigidBody> handledBodies = new ArrayList<RigidBody>();
/*
 * loops through the current unmerged body's neighbors. each one becomes the source of a new RigidCollection surrounding the body
 */
private void dealWithNeighbors(RigidBody sB) {
		for (BodyContact bc : sB.bodyContactList ) {
			if (bc.merged) {
				bc.merged = false;
				RigidBody body2 = bc.getOtherBody(sB);
				handledBodies.add(body2);
				neighborCollection.add(body2);
				makeNeighborCollection(body2);
				if (neighborCollection.size() >= 2) {
					//make a new collection
					RigidCollection newCollection = new RigidCollection(neighborCollection.remove(0), neighborCollection.remove(0));
					newCollection.addBodies(neighborCollection);
					newCollection.fillInternalBodyContacts();
					contactsToBody();
					newCollection.v.set(v);
					newCollection.omega = omega;
					newRigidBodies.add(newCollection);
					neighborCollection.clear();
				}
				else if ( neighborCollection.size() == 1){
					unmergeSingleBody(neighborCollection.get(0));
					newRigidBodies.add(neighborCollection.remove(0));
					contactsToBody();
				}
				
			}
		}
	}

public void contactsToBody() {
	for (Contact c: internalContacts) {
		transformW2B.transform(c.contactW);
		transformW2B.transform(c.normal);
		
	}
}


/*
 * Go through all bodies and makes sure all the body contacts of each body is in the collection
 */
public void fillInternalBodyContacts() {
	for (RigidBody b: collectionBodies) {
		for (BodyContact bc: b.bodyContactList) {
			if (!internalBodyContacts.contains(bc) && bc.merged == true) {
				RigidBody body2 = bc.getOtherBody(b);
				if (b.parent.collectionBodies.contains(body2)) {
					internalBodyContacts.add(bc);
					for (Contact c : bc.contactList) {
						if (!internalContacts.contains(c)) {
							internalContacts.add(c);
						}
					}
					
					
				}
				
				
			}
			
		}
	}
}


/*
 * Add list of bodies to rigidCollection
 */
public void addBodies(ArrayList<RigidBody> bodyList) {
	LinkedList<RigidBody> additionQueue = new LinkedList<RigidBody>();
	for (RigidBody b : bodyList) {
		b.bodyContactListPreMerging.clear();
		//transform all the subBodies to their world coordinates... 
		b.merged = true;
		if (b.parent != null) b.parent.transformB2W.transform(b.x);
		b.theta = b.transformB2W.getTheta();
		b.transformB2C.T.setIdentity();
		b.transformC2B.T.setIdentity();
		b.parent = null;
		additionQueue.add(b);
		b.bodyContactListPreMerging.addAll(b.bodyContactList);
		
	}
	
	for (RigidBody b: additionQueue) {
		collectionBodies.add(b);
	}
	
	setupCollection();
	
}
/*
 * given input body, finds all the bodies this body is connected to and makes them into a single rigidCollection
 */
public ArrayList<RigidBody> neighborCollection = new ArrayList<RigidBody>();

private void makeNeighborCollection(RigidBody body) {
		for (BodyContact bc : body.bodyContactList) {
			if( !bc.merged) continue;
			RigidBody body2 = bc.getOtherBody(body);
			if (handledBodies.contains(body2) ) continue;
			else {
				neighborCollection.add(body2);
				handledBodies.add(body2);
				makeNeighborCollection(body2);
			}
		}
	
}


/*
     * for each neighbor of sB, check whether or not that neighbor is still awake by applying the previous contact forces.
     */
private void checkSubBodyNeighbors(RigidBody sB, Vector2d totalForce, double totalTorque, double forceMetric) {
		for (BodyContact bc : sB.bodyContactList) {
			if (!bc.merged) continue;
			
			if (sB.equals(bc.body1)) {
				checkMetric(bc.body2, totalForce, totalTorque, forceMetric);
			}else {
				checkMetric(bc.body1, totalForce, totalTorque, forceMetric);
			}
		}
		
	}


/**
 * Removes a body from the current collection, without changing the other Bodies
 */
	public void unmergeSelectBodies() {
		// TODO Auto-generated method stub

		for (RigidBody b : colRemovalQueue) {
			b.bodyContactList.clear();
			b.contactList.clear();
			transformB2W.transform(b.x);
			b.theta = b.transformB2W.getTheta();
			b.transformB2C.T.setIdentity();
			b.transformC2B.T.setIdentity();
			b.v.set(v);
			b.omega = omega;
			b.parent = null;
			
			
			/*int i = 0;
			while (true) {
				BodyContact bc = bodyContactList.get(i);
				if (bc.body1 == b || bc.body2== b) {
					bodyContactList.remove(bc);
					if (i >= bodyContactList.size()) break;
					continue;
				}
				i++;
				if (i >= bodyContactList.size()) break;
			}*/
		}
		colRemovalQueue.clear();
		//reset up the collection
		setupCollection();

		
	}
	
	public void unmergeSelectBodies(ArrayList<RigidBody> bodies) {
		// TODO Auto-generated method stub

		for (RigidBody b : bodies) {
			b.bodyContactList.clear();
			b.contactList.clear();
			transformB2W.transform(b.x);
			b.theta = b.transformB2W.getTheta();
			b.transformB2C.T.setIdentity();
			b.transformC2B.T.setIdentity();
			b.v.set(v);
			b.omega = omega;
			b.parent = null;
			
		}
		
		setupCollection();

		
	}

/*basic setup of the collection... calculates transforms, COM, inertia , etc.
 * 
 */
	private void setupCollection() {
		contactsToWorld();
		calculateMass();
	
		calculateCOM();
		//addBlocks(body);
		//from this new collection COM determine new transforms for each body in the collection
		//set Transform B2C and C2B for each body
		setMergedTransformationMatrices();
		transformToCollectionCoords();
	
		calculateInertia();
		//update BVNode roots for each body

		springs.clear();
		addSprings();
}
	

	public void contactsToWorld() {
		for (Contact c: internalContacts) {
			transformB2W.transform(c.contactW);
			transformB2W.transform(c.normal);
			
		}
	}


	public void drawInternalContacts(GLAutoDrawable drawable) {
	
		for (BodyContact bc: internalBodyContacts) {
			if (!bc.merged) continue;
				for (Contact c: bc.contactList) {
					//c.display(drawable);
					
					c.drawInternalContactForce(drawable);
					
				}
		
		}
	}

/* 
 * input parameter is a body being merged. We need to also add the other contacts that body has with the collection it's being merged with
		//Must also add the bodycontacts around the body that didn't reach 50 timesteps but are still part of the same parents.
		
 */
	public void addIncompleteContacts(RigidBody body, LinkedList<BodyContact> removalQueue) {
		for (BodyContact bc: body.bodyContactList) {
			if (bc.body1.parent == bc.body2.parent && bc.relativeVelHistory.size() <= CollisionProcessor.sleep_accum.getValue() && !bc.merged) {
				bc.merged = true;
				body.parent.addInternalContact(bc);
				removalQueue.add(bc);
			}
		}
	}

	//input parameter is a collection being merged . we must add also all the incomplete contacts this parent has with other collections.

	public void addIncompleteCollectionContacts(RigidCollection parent, LinkedList<BodyContact> removalQueue) {
		for (RigidBody b: parent.collectionBodies) {
			addIncompleteContacts(b, removalQueue);
		}
			
	}


	public void drawInternalDeltas(GLAutoDrawable drawable) {
		for (RigidBody b: collectionBodies) {
			b.drawDeltaF(drawable);
		}
	}


	public void drawInternalHistory(GLAutoDrawable drawable) {
		for (Contact c: internalContacts) {
			c.drawInternalContactHistory(drawable);;
		}
	}


	
}
