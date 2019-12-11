package mergingBodies;

import java.util.ArrayList;

import javax.vecmath.Vector2d;

import mergingBodies.Contact.ContactState;
import mergingBodies.RigidBodySystem.MergeParameters;
import mergingBodies.RigidBodySystem.MotionMetricType;


/**
 * This class holds info about two colliding bodies (list of contacts, relative velocity, if they are merged...).
 */
public class BodyPairContact {
	
	public RigidBody body1; 
	public RigidBody body2;
	
	public ArrayList<Contact> contactList = new ArrayList<Contact>();
	
	// Utils for cycle merge/unmerge condition.
	 /** merge condition : by using this tag we avoid checking the same cycle twice*/
	public boolean inCycle = false;
	/** store the bpc which are part of the cycle */
	public ArrayList<BodyPairContact> othersInCycle = null; 
	public Color cycleColor = null;

	MotionMetricProcessor motionMetricProcessor = new MotionMetricProcessor();
	Vector2d relativeLinearVelocity = new Vector2d();
	double relativeAngularVelocity = 0;
	
	public ArrayList<Double> motionMetricHist = new ArrayList<Double>();
	public ArrayList<Contact.ContactState> contactStateHist = new ArrayList<Contact.ContactState>();
		
	public boolean inCollection = false;
	
	public BodyPairContact(RigidBody body1, RigidBody body2) {
		this.body1 = body1;
		this.body2 = body2;
	}

	/**
	 * Check if two bodies are in a list of BodyContact.
	 * @param body1
	 * @param body2
	 * @param bodyPairContacts
	 * @return the corresponding BodyContact object
	 */
	public static BodyPairContact checkExists(RigidBody body1, RigidBody body2, ArrayList<BodyPairContact> bodyPairContacts) {
		for (BodyPairContact bpc: bodyPairContacts) {
			if ((bpc.body1.equals(body1) && bpc.body2.equals(body2))
				|| (bpc.body1.equals(body2) && bpc.body2.equals(body1))) {
				return bpc;
			}
		}
		return null;
	}

	/**
	 * Check if BodyPairContact is in a list of BodyPairContacts
	 * @param bodyPairContacts
	 * @return true of false
	 */
	public boolean isIn(ArrayList<BodyPairContact> bodyPairContacts) {
		
		for (BodyPairContact c : bodyPairContacts) {
			if (c.body1.equals(this.body1) && c.body2.equals(this.body2)) { 
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Accumulate criteria for merging/unmerging
	 * @param contact
	 */
	public void accumulate(MergeParameters mergeParams) {

		RigidBody body1 = (this.body1.isInCollection())? this.body1.parent: this.body1;
		RigidBody body2 = (this.body2.isInCollection())? this.body2.parent: this.body2;
		
		motionMetricProcessor.setMotionMetricType(mergeParams.motionMetricType.getValue());
		motionMetricHist.add(motionMetricProcessor.getMotionMetric(body1, body2));
		
		if (motionMetricHist.size() > mergeParams.stepAccum.getValue())
			motionMetricHist.remove(0);
	
		Contact.ContactState state = Contact.ContactState.CLEAR;
		for (Contact contact : contactList) 
			if (contact.state == Contact.ContactState.ONEDGE)
				state = Contact.ContactState.ONEDGE;
		
		contactStateHist.add(state);
		if (contactStateHist.size() > mergeParams.stepAccum.getValue())
			contactStateHist.remove(0);
	}
	
	/**
	 * Check if:
	 * <p><ul> 
	 * <li> 1. the two bodies have been in contact for at least "sleepAccum" number of time steps
	 * <li> 2. the motion metric has been non-increasing 
	 * <li> 3. and lower than a threshold over CollisionProcessor.sleepAccum time steps.
	 * </ul><p>
	 * @return true or false
	 */
	public boolean checkMotionMetric(MergeParameters mergeParams) {

		double epsilon = 5e-4;

		if ((motionMetricHist.size() == mergeParams.stepAccum.getValue())) {
			double previousValue = 0; 
			double currentValue = 0;
			for (Double metric : motionMetricHist) {
				currentValue = metric;
				if (metric > mergeParams.thresholdMerge.getValue())
					return false;
				
				// I think the monotonically non-increasing metric has no meaning for the largest velocity case
				if (mergeParams.motionMetricType.getValue() != MotionMetricType.LARGESTVELOCITY.ordinal() && currentValue > previousValue + epsilon)
					return false;
				previousValue = metric;
			}
		} else {
			return false;
		}

		return true;
	}  

	/**
	 * Check if contacts have been stable over CollisionProcessor.sleepAccum time steps 
	 * (i.e not on edge of friction cone during the entire time)
	 * @return true or false
	 */
	public boolean areContactsStable(MergeParameters mergeParams) {

		if ((contactStateHist.size() == mergeParams.stepAccum.getValue())) {
			for (Contact.ContactState state : contactStateHist) {
				if (state == Contact.ContactState.ONEDGE)
					return false;
			}
		} else {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Check if it satisfies the cycle criteria: only bodies that share two
	 * contacts, or cycles formed by three bodies with one contact between each.
	 * @return true if the criteria is satisfied
	 */
	public boolean checkContactsCycle(MergeParameters mergeParams) {
		
		int nbActiveContact = 0;
		for (Contact contact : contactList)
			if (contact.state != ContactState.BROKEN)
				nbActiveContact += 1;
		
		// if there are more than one active contact within the bpc, we can merge
		if (nbActiveContact>1)
			return true;

		// if the bpc has already been identified as being part of a cycle, we can merge
		if(inCycle) 
			return true;
		
		// otherwise check if this bpc is in a cycle formed by three bodies with one contact between each
		return body1.checkCycle(1, body2, this, mergeParams); 
	}
	
	/**
	 * Check the contacts' state for unmerge
	 * @param body
	 * @param dt
	 * @return true if should unmerge
	 */
	public boolean checkContactsState(double dt, MergeParameters mergeParams) {		
				
		for (Contact contact : contactList) { 
			if (contact.state == ContactState.BROKEN && mergeParams.enableUnmergeNormalCondition.getValue()) 
				return true; // rule 1. if one contact has broken
			if (contact.state == ContactState.ONEDGE && mergeParams.enableUnmergeFrictionCondition.getValue())
				return true; // rule 2. if one contact is on the edge of friction cone
		}
	
		return false;
	}
	
	/**
	 * Given a RigidBody sB in a BodyPairContact, return the adjacent RigidBody in the BodyPairContact
	 * @param body
	 * @return
	 */
	public RigidBody getOtherBody(RigidBody body) {
		if (body1 == body) return body2;
		if (body2 == body) return body1;
		return null;
	}
	
	/**
	 * Given a RigidBody or RigidCollection sB in a BodyPairContact, return the adjacent RigidBody in the BodyPairContact
	 * @param body
	 * @return
	 */
	public RigidBody getOtherBodyWithCollectionPerspective(RigidBody body) {
		if (body.isInCollection()) 
			return (body1.isInSameCollection(body))? body2 : body1;
		else 
			return getOtherBody(body);
	}
	
	/**
	 * Add the BodyContact to bodyContactList of members body1 and body2  
	 */
	public void addBodiesToUnmerge(ArrayList<RigidBody> bodiesToUnmerge, boolean addBoth) {
		if(!body1.isInCollection() || !body2.isInCollection() || !body1.isInSameCollection(body2)) {
			System.err.println("[addBodiesToUnmerge] trying to unmerge a body that is not in a collection. This shouldn't happen?");
			return;
		}

		if(addBoth) {
			if(!bodiesToUnmerge.contains(body1) && !body1.pinned)
				bodiesToUnmerge.add(body1);
			if(!bodiesToUnmerge.contains(body2) && !body2.pinned)
				bodiesToUnmerge.add(body2);
		} else {
			double metric1 = motionMetricProcessor.getMotionMetric(body1.parent, body1);
			double metric2 = motionMetricProcessor.getMotionMetric(body2.parent, body2);
			if(!bodiesToUnmerge.contains(body1) && !body1.pinned && metric1>metric2)
				bodiesToUnmerge.add(body1);
			else if (!bodiesToUnmerge.contains(body2) && !body2.pinned)
				bodiesToUnmerge.add(body2);
		}
	}

	/**
	 * Add the BodyContact to bodyContactList of members body1 and body2  
	 */
	public void addToBodyLists() {
		if (!body1.bodyPairContactList.contains(this)) {
			body1.bodyPairContactList.add(this);
		}
		if (!body2.bodyPairContactList.contains(this)) {
			body2.bodyPairContactList.add(this);
		}
	}
	
	/**
	 * Add the BodyContact to bodyContactList of parent members body1 and body2  
	 */
	public void addToBodyListsParent() {		
		if (body1.isInCollection() && !body1.parent.bodyPairContactList.contains(this)) {
			body1.parent.bodyPairContactList.add(this);
		}
		if (body2.isInCollection() && !body2.parent.bodyPairContactList.contains(this)) {
			body2.parent.bodyPairContactList.add(this);
		}
	}
	
	/**
	 * Remove the BodyContact from bodyContactList of members body1 and body2
	 */
	public void removeFromBodyLists() {
		if (body1.bodyPairContactList.contains(this))
			body1.bodyPairContactList.remove(this);
		if (body2.bodyPairContactList.contains(this))
			body2.bodyPairContactList.remove(this);
	}
	
	/**
	 * Remove the BodyContact from bodyContactList of parent of members body1 and body2
	 */
	public void removeFromBodyListsParent() {
		if (body1.isInCollection() && body1.parent.bodyPairContactList.contains(this)) {
			body1.parent.bodyPairContactList.remove(this);
		}
		if (body2.isInCollection() && body2.parent.bodyPairContactList.contains(this)) {
			body2.parent.bodyPairContactList.remove(this);
		}
	}
	
	/**
	 * Check merge condition
	 * @return
	 */
	protected boolean checkMergeCondition(MergeParameters mergeParams, boolean checkCycle) {

		if (body1.isSleeping && body2.isSleeping) 
			return true;
				
		if (mergeParams.enableMergeLetItBreathe.getValue())
			for (Contact contact: contactList)
				if (Math.abs(contact.prevConstraintViolation - contact.constraintViolation)>mergeParams.thresholdBreath.getValue()) 
					return false;
		
		if (!mergeParams.enableMergePinned.getValue() && (body1.pinned || body2.pinned)) 
			return false;
		
		if (body1.isInSameCollection(body2)) 
			return false;
		
		if (!checkMotionMetric(mergeParams))
			return false;
		
		if (mergeParams.enableMergeStableContactCondition.getValue() && !areContactsStable(mergeParams))
			return false;
		
		if (mergeParams.enableMergeCycleCondition.getValue() && checkCycle && !checkContactsCycle(mergeParams))
			return false;
			
		return true;
	}
	
	/**
	 * Clear datas related to the cycle, and tag the other bpc in the cycle with input unmerge value 
	 * @param unmerge
	 */
	public void clearCycle() {
		if (othersInCycle != null) {
			for (BodyPairContact bpc : othersInCycle) {	
				bpc.inCycle = false;
				bpc.cycleColor = null;
				bpc.othersInCycle.clear();
			}
			inCycle = false;
			cycleColor = null;
			othersInCycle.clear();
		}
	}
	
	/**
	 * Input bpc is added as being part of the cycle. Update all lists and datas accordingly. 
	 * @param bpc
	 */
	public void updateCycle(BodyPairContact bpc) {
		
		if (othersInCycle == null)
			othersInCycle = new ArrayList<BodyPairContact>();
		bpc.othersInCycle = new ArrayList<BodyPairContact>();
		
		if (cycleColor == null) {
			cycleColor = new Color();
			cycleColor.setRandomColor();
		}
		bpc.cycleColor = cycleColor;
		
		if (!othersInCycle.isEmpty()) {
			BodyPairContact thirdbpc = othersInCycle.get(0);
			bpc.othersInCycle.add(thirdbpc);
			thirdbpc.othersInCycle.add(bpc);
		}
		
		bpc.othersInCycle.add(this);
		this.othersInCycle.add(bpc);
		
		bpc.inCycle = true;
		this.inCycle = true;
	}
	
	public void checkCyclesToUnmerge(ArrayList<RigidBody> bodiesToUnmerge) {
		ArrayList<BodyPairContact> bpcToCheck = new ArrayList<BodyPairContact>();
		
		for (BodyPairContact bpc: body1.bodyPairContactList) { // check if body1 was part of a cycle
			if (bpc.inCycle) {
				bpc.addBodiesToUnmerge(bodiesToUnmerge, false); 
				bpcToCheck.add(bpc);
				for (BodyPairContact bpcToUnmerge : bpc.othersInCycle) { // unmerge the others bodies
					bpcToUnmerge.addBodiesToUnmerge(bodiesToUnmerge, false);
					bpcToCheck.add(bpcToUnmerge);
				}
				bpc.clearCycle();
			}
		}
		
		for (BodyPairContact bpc: body2.bodyPairContactList) { // check if body2 was part of a cycle
			if (bpc.inCycle) {
				bpc.addBodiesToUnmerge(bodiesToUnmerge, false);
				bpcToCheck.add(bpc);
				 for (BodyPairContact bpcToUnmerge : bpc.othersInCycle) { // unmerge the others bodies
					bpcToUnmerge.addBodiesToUnmerge(bodiesToUnmerge, false);
					bpcToCheck.add(bpcToUnmerge);
				 }
				bpc.clearCycle();
			}
		}
		
		for (BodyPairContact bpc: bpcToCheck)
			bpc.checkCyclesToUnmerge(bodiesToUnmerge);
	}
}
