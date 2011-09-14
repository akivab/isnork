package isnork.g3;

import isnork.g3.strategy.SpiralStrategy;
import isnork.g3.strategy.SpiralStrategyPlayer;
import isnork.g3.strategy.SpiralStrategyType;
import isnork.sim.GameConfig;
import isnork.sim.GameEngine;
import isnork.sim.Observation;
import isnork.sim.Player;
import isnork.sim.SeaLifePrototype;
import isnork.sim.iSnorkMessage;
import isnork.sim.GameObject.Direction;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;

//REMOVE System.out

class ObservedCreature {

	int age = 0;
	int id = 0;
	int senderId = 0;
	int tick = 0;
	Point2D location = null;
	SeaLifePrototype slp = null;

	// Direction angle;

	public ObservedCreature(Point2D location, int age, SeaLifePrototype slp,
			Direction movement, int id, int senderId, int tick) {

		this.location = location;
		this.age = age;
		this.slp = slp;
		this.id = id;
		this.senderId = senderId;
		this.tick = tick;
	}
}

public class G3Player extends Player {
	private int id;
	private int tickCount;

	private int TOTAL_TICK = 8 * 60;
	private static int topN = 5;

	private int d = -1, r = -1, n = -1;
	Point2D whereIAm = null;
	Point2D whereIWasOneTurnAgo = null;
	Point2D whereIWasTwoTurnsAgo = null;
	Point2D whereIWasThreeTurnsAgo = null;
	Point2D whereIWasFourTurnsAgo = null;
	private SpiralStrategy spiralStrategy;
	private ArrayList<Set<iSnorkMessage>> myIncomingMessages;
	private Set<SeaLifePrototype> seaLifePossibilities;
	private Set<SeaLifePrototype> topSLPossibilities;
	private boolean goingAfterCreature = false;
	private Point2D currentDest;
	double numCreaturesWithHighValue = 0;
	private ArrayList<DangerousCreature> dangerousCreaturesAroundYou;
	private Communication myCommunication;
	private EncodedCommunication encodedCommunication;
	private String currentSendingMessage = new String();
	private int msgOffset = 0;
	private boolean sendingMessageComplete = true;
	private HashMap<Integer, String> currentReceivingMessages = new HashMap<Integer, String>();
	private HashMap<Integer, iSnorkMessage> initiatingMessage = new HashMap<Integer, iSnorkMessage>();
	private boolean endGame;
	private boolean preEndGameStrategy;
	private String nameOfCreaturePursuing;
	private int idOfCreaturePursuing;
	private ArrayList<ObservedCreature> observedCreatures = new ArrayList<ObservedCreature>();
	private ArrayList<ObservedCreature> observedCreaturesFromEntireGame = new ArrayList<ObservedCreature>();
	private boolean tracking = false;
	private Set<Observation> whatYouSee;
	private Point2D trackedCreatureLocation;
	private CreatureToTrack creatureImTracking;
	private int IDofCreatureImTracking = -1000;
	private static int OBSERV_PERIOD = 3;
	private static final Logger log = Logger.getLogger(G3Player.class);
	private boolean[] diverHasSeenCreatureImTracking;
	private double dangerDensity = 1.4;
	private double maximumScore = 0;
	private Point2D locationOfCreatureImTrackingOneTickAgo;
	private int ticksLeftToGoHome;
	private double[] eachPlayersApproximateScore;
	private ArrayList<WhatEachPersonHasSeen> eachPlayersCreaturesSeenList;
	private int trackingJudgeThreshold = 5;
	private int trackingOffsetThreshold = 4;
	private double DENSITY_THRESHOLD = 0.2;
	private Point2D intermediateCheckpoint;
	private int dangerAvoidanceLimit = 20;
	private double staticOverMovingDanger = 0.7;
	private int exploreAnywayIfPositiveOverNegative = 100;
	private int maxExploreRadiusIfDangerous = 10;

	@Override
	public String getName() {
		return "G3: Nautical Navigator ";
	}

	public int getScore(String creatName) {
		for (SeaLifePrototype slp : seaLifePossibilities)
			if (creatName != null) {
				if (slp.getName() != null) {
					if (slp.getName().equals(creatName)) {
						return slp.getHappiness();
					}
				}
			}
		return -1;
	}

	public SeaLifePrototype getSLP(String name) {

		for (SeaLifePrototype slp : this.topSLPossibilities) {
			if (slp.getName().equals(name)) {
				return slp;
			}
		}
		// Should never reach here
		return null;
	}

	public ArrayList<ObservedCreature> getObservedCreatures() {

		return this.observedCreatures;
	}

	/**
	 * Use maximum score to return to boat when no more happiness can be
	 * achieved
	 */
	public void computeMaximumScore() {

		for (SeaLifePrototype slp : this.seaLifePossibilities) {
			int num = java.lang.Math.min(slp.getMaxCount(), 3);
			int d = 1;
			for (int i = 1; i <= num; i++) {
				this.maximumScore += slp.getHappinessD() / d;
				d = d << 1;
			}
		}
	}

	public void computeDangerDensity() {

		int cntDangerous = 0;
		for (SeaLifePrototype slp : this.seaLifePossibilities) {
			if (slp.isDangerous() && (slp.getHappiness() > 0))
				cntDangerous++;
		}
		if (cntDangerous > 0)
			this.dangerDensity = 2.0;

	}

	public double getDangerDensity() {
		return this.dangerDensity;
	}

	public boolean maximumScoreAchieved() {

		// GameEngine.println(this.getHappiness()+" "+this.maximumScore );
		return (this.getHappiness() >= this.maximumScore);
	}

	public DirectionAngle getRelativeDir(Point2D p1, Point2D p2) {

		if (p1.equals(p2))
			return null;

		if (p1.getY() < p2.getY()) {
			if (p1.getX() < p2.getX()) {
				if ((Math.abs(p1.getX()) - Math.abs(p1.getY())) < (Math.abs(p2
						.getX()) - Math.abs(p2.getY()))) {
					return DirectionAngle.NNE;
				} else if ((Math.abs(p1.getX()) - Math.abs(p1.getY())) == (Math
						.abs(p2.getX()) - Math.abs(p2.getY()))) {
					return DirectionAngle.NE;
				} else {
					return DirectionAngle.NEE;
				}

			} else if (p1.getX() > p2.getX()) {
				if ((Math.abs(p1.getX()) - Math.abs(p1.getY())) < (Math.abs(p2
						.getX()) - Math.abs(p2.getY()))) {
					return DirectionAngle.NNW;
				} else if (Math.abs(p1.getX()) - Math.abs(p1.getY()) == (Math
						.abs(p2.getX()) - Math.abs(p2.getY()))) {
					return DirectionAngle.NW;
				} else {
					return DirectionAngle.NWW;
				}

			} else
				return DirectionAngle.N;

		} else if (p1.getY() > p2.getY()) {
			if (p1.getX() < p2.getX()) {
				if ((Math.abs(p1.getX()) - Math.abs(p1.getY())) < (Math.abs(p2
						.getX()) - Math.abs(p2.getY()))) {
					return DirectionAngle.SSE;
				} else if ((Math.abs(p1.getX()) - Math.abs(p1.getY())) == (Math
						.abs(p2.getX()) - Math.abs(p2.getY()))) {
					return DirectionAngle.SE;
				} else {
					return DirectionAngle.SEE;
				}

			} else if (p1.getX() > p2.getX()) {
				if ((Math.abs(p1.getX()) - Math.abs(p1.getY())) < (Math.abs(p2
						.getX()) - Math.abs(p2.getY()))) {
					return DirectionAngle.SSW;
				} else if (Math.abs(p1.getX()) - Math.abs(p1.getY()) == (Math
						.abs(p2.getX()) - Math.abs(p2.getY()))) {
					return DirectionAngle.SW;
				} else {
					return DirectionAngle.SWW;
				}

			} else
				return DirectionAngle.S;

		} else {
			if (p1.getX() == p2.getX())
				return null;
			else if (p1.getX() < p2.getX())
				return DirectionAngle.E;
			else
				return DirectionAngle.W;
		}

	}

	public ArrayList<ObservedCreature> removeOldAndUpdate(
			ArrayList<ObservedCreature> newCreatures) {
		for (int i = 0; i < this.observedCreatures.size(); i++) {
			ObservedCreature obs = this.observedCreatures.get(i);
			if (obs.age == OBSERV_PERIOD) {
				this.observedCreatures.remove(i);
				i--;
				continue;
			} else
				obs.age++;
		}

		for (int i = 0; i < newCreatures.size(); i++) {
			for (int j = i + 1; j < newCreatures.size(); j++) {
				if (newCreatures.get(i).location.getX() == newCreatures.get(j).location
						.getX()
						&& newCreatures.get(i).location.getY() == newCreatures
								.get(j).location.getY()
						&& newCreatures.get(i).id == newCreatures.get(j).id) {
					newCreatures.remove(j);
				}

			}
		}
		for (ObservedCreature o : newCreatures) {

			this.observedCreatures.add(o);
		}
		return this.observedCreatures;
	}

	double getDensityOfSLP(String creatureName) {
		Iterator<SeaLifePrototype> myIter = seaLifePossibilities.iterator();
		while (myIter.hasNext()) {
			SeaLifePrototype slp = myIter.next();
			if (slp.getName().equals(creatureName)) {
				return Math.sqrt((double) (this.r
						* (slp.getMinCount() + slp.getMaxCount()) / 2.0)
						/ (GameConfig.d * GameConfig.d + 1));
			}
		}

		return 0;
	}

	double getDensityOfSLP(SeaLifePrototype slp) {

		return Math
				.sqrt(((this.r * (slp.getMinCount() + slp.getMaxCount()) / 2.0))
						/ (double) (GameConfig.d * GameConfig.d + 1));

	}

	double getScoreOfSLP(double density, SeaLifePrototype slp) {

		// return slp.getHappiness();
		return Math.cbrt(slp.getHappinessD()) / density;
	}

	double getScoreOfSLP(String creatureName) 
	{
		Iterator<SeaLifePrototype> myIter = seaLifePossibilities.iterator();
		while (myIter.hasNext()) 
		{
			SeaLifePrototype slp = myIter.next();
			if (slp.getName().equals(creatureName)) 
			{
				double density = getDensityOfSLP(slp);
				double score = Math.cbrt(slp.getHappinessD()) / density;
				return score;
			}
		}

		return 0;
	}

	/**
	 * This function filters out creatures whose score is either too low or are
	 * very frequent
	 */
	private Set<SeaLifePrototype> filterOutSLP(Set<SeaLifePrototype> slps,
			int topN) {

		this.topSLPossibilities = new HashSet<SeaLifePrototype>();
		if (this.seaLifePossibilities.size() < topN) {
			this.topSLPossibilities.addAll(slps);
			return this.topSLPossibilities;
		}

		TreeMap<Double, LinkedList<SeaLifePrototype>> rankedSLP = new TreeMap<Double, LinkedList<SeaLifePrototype>>();
		for (SeaLifePrototype slp : slps) {
			double density = this.getDensityOfSLP(slp);
			//GameEngine.println("slp " + slp.getName() + " " + density + " "
			//		+ slp.getSpeed());
			// if(density>this.DENSITY_THRESHOLD)
			// continue;
			double score = this.getScoreOfSLP(density, slp);
			if (rankedSLP.containsKey(score))
				rankedSLP.get(score).add(slp);
			else {
				LinkedList<SeaLifePrototype> toAdd = new LinkedList<SeaLifePrototype>();
				toAdd.add(slp);
				rankedSLP.put(score, toAdd);
			}
		}

		for (int i = 0; i < topN;) {
			if (rankedSLP.isEmpty())
				break;
			for (SeaLifePrototype slp : rankedSLP.lastEntry().getValue()) {
				//GameEngine.println("Adding to top " + slp.getName());
				this.topSLPossibilities.add(slp);
				i++;
				if (i == topN)
					break;
			}
			if (i == topN)
				break;
			rankedSLP.remove(rankedSLP.lastKey());
		}
		return this.topSLPossibilities;

	}

	boolean compareIds(int id1, int id2, SeaLifePrototype slp) {
		if ((id1 % slp.getMaxCount()) == (id2 % slp.getMaxCount()))
			return true;
		return false;
	}

	private void addNewObservedCreaturesToEntireGameList() {
		for (int i = 0; i < observedCreatures.size(); i++) {
			observedCreaturesFromEntireGame.add(observedCreatures.get(i));
		}
	}

	@Override
	public String tick(Point2D myPosition, Set<Observation> whatYouSee,
			Set<iSnorkMessage> incomingMessages,
			Set<Observation> playerLocations) {
		tickCount++;
		this.whatYouSee = whatYouSee;
		if (tickCount==1)
        {
                spiralStrategy = new SpiralStrategy(d, r, n, buildSpiralStrategyType());
                //spiralStrategy = new SpiralStrategy(d, r, n); //normal mode
        }

		ArrayList<ObservedCreature> myObservedCreatures = getObservedCreatures();

		/*if (tracking) {
			updateWhichDiversHaveSeenCreatureImTracking(incomingMessages,whatYouSee);
			//GameEngine.println("the percent of divers that have seen this is: " + findPercentOfDiversThatHaveSeenCreatureImTracking());
			if (findPercentOfDiversThatHaveSeenCreatureImTracking() >= .9) {
				IDofCreatureImTracking = -1000;
				tracking = false;
				GameEngine.println(id + "***90% of the divers have seen the creature I'm tracking, so stop tracking");
			}
		}*/

		if (preEndGameStrategy) 
		{
			if (!tracking) 
			{
				ticksLeftToGoHome--;
				if(id == -300)
					GameEngine.println(id + "ticksLeftToGoHome just went down");
			} 
			else {
				ticksLeftToGoHome = 30;
				if(id == -300)
					GameEngine.println(id + "ticksLeftToGoHome is 30");
			}
		}

		// ***************
		// Looking at the array list
		// GameEngine.println("\n********************");
		if(id == -300)
			GameEngine.println(id + "Tick count: " + tickCount + " Creatures being broadcast to me");

		if (myObservedCreatures.size() > 0) {
			for (int m = 0; m < myObservedCreatures.size(); m++) {
				ObservedCreature o = myObservedCreatures.get(m);
				if(id == -300)
					GameEngine.println(id + "  Creature: " + o.slp.getName() + ", Score: " + o.slp.getHappiness() + ", location: " + o.location + "id: " + o.id + ", age: " + o.age + " sender: " + o.senderId);
			}
		}
		// ****************

		ArrayList<ObservedCreature> newCreatures = new ArrayList<ObservedCreature>();
		updateDangerousCreaturesAroundYou(myPosition, whatYouSee);
		printDangerousreatures();

		whereIAm = myPosition;
		myIncomingMessages.add(incomingMessages);

		Message nm;
		for (iSnorkMessage ism : incomingMessages) {
			if (ism.getMsg() != null) {
				// log.debug("Received something " + ism.getMsg()
				// + " from " + ism.getSender() + " " + this.getId());
				String tMsg = this.currentReceivingMessages
						.get(ism.getSender());
				if (tMsg == null) {
					this.currentReceivingMessages.put(ism.getSender(), ism
							.getMsg());
					tMsg = ism.getMsg();
					this.initiatingMessage.put(ism.getSender(), ism);
				} else {
					if (this.currentReceivingMessages.get(ism.getSender())
							.isEmpty())
						this.initiatingMessage.put(ism.getSender(), ism);
					tMsg += ism.getMsg();
					this.currentReceivingMessages.put(ism.getSender(), tMsg);
				}

				if ((nm = this.encodedCommunication.getDecodedString(tMsg)) != null) {
					// if(SimpleUtil.DEBUG)
					// GameEngine.println(id + " Received a message from "
					// + ism.getSender() + " " +nm.slp.getName());
					this.currentReceivingMessages.put(ism.getSender(),
							new String());

					Point2D location;

					Point2D sender = this.initiatingMessage
							.get(ism.getSender()).getLocation();
					if (nm.directionAngle != null)
						location = new Point2D.Double(sender.getX() + nm.r
								* nm.directionAngle.dx, sender.getY() + nm.r
								* nm.directionAngle.dy);
					else
						location = new Point2D.Double(sender.getX(), sender
								.getY());
					// if (SimpleUtil.DEBUG)
					// log.debug(" Location of " + nm.slp.getName() + " " +
					// location.getX() + " " + location.getY());
					ObservedCreature obs = new ObservedCreature(location, 0,
							nm.slp, null, nm.id, ism.getSender(), tickCount);
					newCreatures.add(obs);
					this.currentReceivingMessages.put(ism.getSender(),
							new String());
				}
			}
		}
		this.removeOldAndUpdate(newCreatures);
		addNewObservedCreaturesToEntireGameList();

		// If we see nothing and have completed sending the previous message
		// send blank message
		if (whatYouSee.isEmpty() && (this.sendingMessageComplete == true)) {
			return null;
		}

		double max = Double.MIN_VALUE;
		Observation happiestObs = null;
		// String initCreat = null;

		// If not completed sending the previous message go on
		if (this.sendingMessageComplete == false) {
			if ((this.msgOffset + 1) == this.currentSendingMessage.length())
				this.sendingMessageComplete = true;
			this.msgOffset++;
			return this.currentSendingMessage.substring(this.msgOffset - 1,
					this.msgOffset);
		} else {
			boolean sawMyTracking = false;
			for (Observation obCreat : whatYouSee) {
				// This creature is not "interesting"
				SeaLifePrototype slp = this.getSLP(obCreat.getName());
				if (slp == null)
					continue;
				double obScore = this.getScoreOfSLP(this.getDensityOfSLP(slp),slp);
				//GameEngine.println("***********Creature "+slp.getName()+" has density "+this.getDensityOfSLP(slp)+" "+" score "+obScore);
				if(percentOfPlayersWhoHaveSeenThisCreature(obCreat.getName(), obCreat.getId()) < .9)  {
					
					if (((obScore >= max) && ((this.creatureImTracking==null) || (!sawMyTracking)))
							&& !obCreat.getName().equals(this.getName())
							|| ((this.creatureImTracking != null) && (obCreat
									.getName().equals(this.creatureImTracking
									.getName()))) && !sawMyTracking)
					
//				if (((obScore >= max))
//						&& !obCreat.getName().equals(this.getName())
//					) 
				{
					if ((this.creatureImTracking != null)
							&& (obCreat.getName()
									.equals(this.creatureImTracking.getName()))) {
						sawMyTracking = true;
					//	break;
					}
					max = obScore;
					happiestObs = obCreat;
					myCommunication.sawCreature(obCreat);
					if (nameOfCreaturePursuing != null && currentDest != null) {
						//log.debug("The creature I'm pursuing is: " + nameOfCreaturePursuing + " and the creature I see is: " + obCreat.getName());
						// if(id == -300)
						// GameEngine.println(id + "whereIAm: " + whereIAm +
						// "  currentDest: " + currentDest +
						// " currentDest.distance(whereIAm)" +
						// currentDest.distance(whereIAm));
						if (obCreat.getName().equals(nameOfCreaturePursuing)
								&& compareIds(obCreat.getId(),
										idOfCreaturePursuing,
										getSeaLifePrototype(obCreat))) {
						
							goingAfterCreature = false;
							nameOfCreaturePursuing = null;
							idOfCreaturePursuing = -3000;
							myCommunication.setCreaturePursuingToNull();
						}
					}
				}
				}
			}
			if (nameOfCreaturePursuing != null && currentDest != null) {
				if (currentDest.distance(whereIAm) < 1) {
					goingAfterCreature = false;
					nameOfCreaturePursuing = null;
					idOfCreaturePursuing = -3000;
					myCommunication.setCreaturePursuingToNull();
				
				}
			}

			// If nothing interesting to update the rest of the group
			if (happiestObs == null) {
				return null;
			}
			if (happiestObs != null
					&& !happiestObs.getName().equals(this.getName())) {

				SeaLifePrototype slp = this.getSLP(happiestObs.getName());
				if (slp == null)
					return null;
				Message m;

				int freq = (slp.getMinCount() + slp.getMaxCount()) / 2
						+ slp.getHappiness();
				m = new Message(slp, (int) this.whereIAm.distance(happiestObs
						.getLocation()), freq, this.getRelativeDir(
						this.whereIAm, happiestObs.getLocation()), happiestObs
						.getId()
						% slp.getMaxCount());

				this.currentSendingMessage = this.encodedCommunication
						.getEncodedString(m);

				// log.debug("Msg "
				// + m.slp.getName()
				// + " "
				// + (int) this.whereIAm.distance(happiestObs
				// .getLocation()) + " CM "
				// + this.currentSendingMessage);
				if (this.currentSendingMessage.length() > 1) {
					this.sendingMessageComplete = false;
					this.msgOffset = 1;
				}
				return this.currentSendingMessage.substring(0, 1);
			}

		}
		// initCreat = happiestObs.getName().substring(0, 1);
		// GameEngine.println.print(id + "Broadcasting initial "+initCreat+
		// " of creature " + happiestObs.getName()+" with happiness score "+
		// max);
		return null;
	}

	public void printWhatYouSee(Set<Observation> whatYouSee) {
		Iterator<Observation> myIter = whatYouSee.iterator();
		// log.debug("*****OBS THIS TICK******");
		while (myIter.hasNext()) {
			Observation myObs = myIter.next();
			// log.debug("   myObs: " + myObs.getName() + " ID: " +
			// myObs.getId() + " happiness: " + myObs.happiness()+
			// " isDangeorus: " + myObs.isDangerous() + " location: "+
			// myObs.getLocation() + "direction: " + myObs.getDirection());
		}
	}

	public void printDangerousreatures() {
		int count = 1;
		// log.debug("***Dangerous Creatures at tick " + tickCount + "***");
		for (int i = 0; i < dangerousCreaturesAroundYou.size(); i++) {
			DangerousCreature myDC = dangerousCreaturesAroundYou.get(i);
			// log.debug("Creature #" + count + ": " + myDC.getName() + ", ID: "
			// + myDC.getId() + ", isFresh: " + myDC.isFresh() +
			// ", numTicksOnSameLocation: " + myDC.getNumTicksOnSameLocation());
			count++;
		}
		// log.debug("*********");
	}

	public void updateDangerousCreaturesAroundYou(Point2D myPosition,
			Set<Observation> whatYouSee) {
		setDangerousCreaturesToNotFresh();
		Iterator<Observation> myIter = whatYouSee.iterator();
		while (myIter.hasNext()) {
			Observation myObs = myIter.next();
			if (myObs.isDangerous()) {
				boolean foundCreatureInArrayList = false;
				for (int i = 0; i < dangerousCreaturesAroundYou.size(); i++) {
					DangerousCreature currentDangerousCreature = dangerousCreaturesAroundYou
							.get(i);
					if (myObs.getName() == currentDangerousCreature.getName()
							&& myObs.getId() == currentDangerousCreature
									.getId()) {
						currentDangerousCreature.updateLocation(myObs
								.getLocation(), myObs.getDirection());
						foundCreatureInArrayList = true;
					}
				}

				if (foundCreatureInArrayList == false) {
					int dangerScore = findDangerScore(myObs);
					dangerousCreaturesAroundYou.add(new DangerousCreature(myObs
							.getName(), myObs.getId(), myObs.getLocation(),
							myObs.getDirection(), dangerScore));
				}
			}
		}
		removeNotFreshCreatures();
	}

	public void setDangerousCreaturesToNotFresh() {
		for (int i = 0; i < dangerousCreaturesAroundYou.size(); i++) {
			DangerousCreature currentDangerousCreature = dangerousCreaturesAroundYou
					.get(i);
			currentDangerousCreature.setNotFresh();
		}
	}

	public void removeNotFreshCreatures() {
		int size = dangerousCreaturesAroundYou.size();
		ListIterator<DangerousCreature> listIterator = dangerousCreaturesAroundYou
				.listIterator();
		while (listIterator.hasNext()) {
			DangerousCreature dc = listIterator.next();

			if (dc.isFresh() == false) {
				listIterator.remove();
			}
		}
		int newSize = dangerousCreaturesAroundYou.size();
		// log.debug("size before: " + size + ", new size: " + newSize);
	}

	public int findDangerScore(Observation myObs) {
		for (SeaLifePrototype slp : seaLifePossibilities) {
			if (myObs.getName() != null) {
				if (slp.getName() != null) {
					if (slp.getName().equals(myObs.getName())) {
						return -2 * slp.getHappiness();
					}
				}
			}
		}
		// log.debug("**ERROR: SHOULD NOT REACH HERE");
		return 0;
	}

	private void printScoreArray() {
		for (int i = 0; i < eachPlayersApproximateScore.length; i++) {
			GameEngine.println("the score of player " + i + " is: "
					+ eachPlayersApproximateScore[i]);
		}
	}

	@Override
	public Direction getMove() {

		// GameEngine.println("");
		// GameEngine.println(id + "getMove() just called: tick=" + tickCount +
		// " position=" + whereIAm);
		Direction d = Direction.W;
		int timeLeft = TOTAL_TICK - tickCount;

		if (this.maximumScoreAchieved() && !tracking && !preEndGameStrategy) {
			// GameEngine.println(id + " Maximum score was achieved!");
			preEndGameStrategy = true;
			ticksLeftToGoHome = 30;
		}

		//if(id == -300)
			//GameEngine.println("getNumberOfDangerousCreatures()/area: " + (getNumberOfDangerousCreature()/((this.d+1)*(this.d+1))));
		
		//this "1.6" should depend on how dangerous the waters are
		
		double whenToGoHomeConstant;
		
		if(ifNoDangerousCreature())
			whenToGoHomeConstant = 1.0;
		else if(getNumberOfDangerousCreature()/((this.d+1)*(this.d+1)) < .008)
			whenToGoHomeConstant = 1.6;
		else
			whenToGoHomeConstant = 2.4;
		
		
		if ((timeLeft <= whenToGoHomeConstant * timeToHome() + 6) || (preEndGameStrategy && ticksLeftToGoHome < 0)) 
		{
			if(id == -300)
			{
				GameEngine.println(id + "Time to start end game strategy!");
				if(whenToGoHomeConstant == 2.4)
					GameEngine.println(id + "***STARTING EARLIER B/C THIS IS A DANGEROUS BOARD");
				else if(whenToGoHomeConstant == 1.4)
					GameEngine.println(id + "***WAITED UNTIL LAST POSSIBLE SECOND B/C NO DANGEROUS CREATURES");
				else
					GameEngine.println(id + "***Starting at normal time b/c this board is not too dangerous");
			}
			endGame = true;
		}

		if (endGame == true) {
			if (timeLeft <= 1.4 * timeToHome()) {
				if ((id == -300))
					GameEngine.println(id + "final end game strategy, just go home and don't take into account dangerous creatures");
				d = moveToHome();
			} else {
				if ((id == -300))
					GameEngine.println(id + "END GAME STRATEGY: MOVE TOWARDS HOME IN A WAY THAT AVOIDS DANGEROUS CREATURES");
				if (whereIAm.equals(new Point2D.Double(0, 0))) {
					// log.debug("Stay put, already on ship");
					return null;
				} else {
					d = moveAwayFromDanger(new Point2D.Double(0, 0));
				}
			}
		}

		else {
			// GameEngine.println(id + "not in end game strategy yet");
			SpiralStrategyPlayer ssPlayer = spiralStrategy.getPlayer(-id);
			Point2D p = ssPlayer.getCurrentPoint();
			if (whereIAm.getX() == p.getX() && whereIAm.getY() == p.getY()) {
				ssPlayer.switchToNextPoint();
				p = ssPlayer.getCurrentPoint();
				if (intermediateCheckpoint != null) {
					intermediateCheckpoint = null;
				}
			}
			if (whereIWasTwoTurnsAgo != null&& whereIAm.equals(whereIWasTwoTurnsAgo)) {
				if (goingAfterCreature) {
					if (whereIWasFourTurnsAgo != null && whereIWasThreeTurnsAgo != null) {
						if (whereIWasFourTurnsAgo.equals(whereIWasTwoTurnsAgo) || whereIWasThreeTurnsAgo.equals(whereIWasTwoTurnsAgo)) {
							ssPlayer.switchToNextPoint();
							intermediateCheckpoint = ssPlayer.getCurrentPoint();
							//GameEngine.println(id + "Couldn't get to intermediate checkpoint, change to new intermediate checkpoint");
						}
					}
				} else {
					ssPlayer.switchToNextPoint();
					p = ssPlayer.getCurrentPoint();
				}
			}

			CreatureLocation myCreatureLocation = myCommunication.findDestinationPointBasedOnIncomingMessages(getObservedCreatures(), whereIAm);
			Point2D myDestination = myCreatureLocation.getLocation();

			updateWhatEachPersonHasSeen();
			// printWhatEachDiverHasSeen();
			updateScoreArray();
			if(id == -300)
				printScoreArray();
			CreatureToTrack myCreature = creatureToTrack(); // this function
			// returns a
			// CreatureToTrack
			if (myCreature != null) {
				creatureImTracking = myCreature;
				tracking = true;
			} else {
				creatureImTracking = null;
				IDofCreatureImTracking = -1000;
				tracking = false;
			}

			if (tracking) {
				//GameEngine.println(id + "Tracking creature: " + creatureImTracking.getName() + " with ID: " + creatureImTracking.getId());
				d = moveAwayFromDanger(getCreatureLocation(creatureImTracking));
				if (id == -300) {
					GameEngine.println(id + "The creature is located at: " + getCreatureLocation(creatureImTracking));
					GameEngine.println(id + "The move that gets me closest to the dangerous creature is: " + d);
				}
			} else if (intermediateCheckpoint != null) {
				//GameEngine.println(id + " ***intermediateCheckpoint: " + intermediateCheckpoint);
				d = moveAwayFromDanger(intermediateCheckpoint);
			} else if (myDestination.getX() != -99999) {
				if ((id == -300))
					GameEngine.println(id + "**************New Destination! It is " + myDestination);

				if (goingAfterCreature && nameOfCreaturePursuing != null && myCreatureLocation.getCreatureName().equals(nameOfCreaturePursuing) && myCreatureLocation.getCreatureId() == idOfCreaturePursuing) {
					d = moveAwayFromDanger(myDestination);
					currentDest = myDestination;
					goingAfterCreature = true;
					if ((id == -300))
						GameEngine.println(id + "Continuing to go after the : " + myCreatureLocation.getCreatureName() + " It is now at " + myDestination);
				} else {
					if (goingAfterCreature) {
						d = moveAwayFromDanger(currentDest);
					
					} else {
						d = moveAwayFromDanger(myDestination);
						currentDest = myDestination;
						goingAfterCreature = true;
						nameOfCreaturePursuing = myCreatureLocation
								.getCreatureName();
						idOfCreaturePursuing = myCreatureLocation
								.getCreatureId();
						if ((id == -300))
							GameEngine.println(id + "New Destination! It is "
									+ myDestination);
					}
				}
			} else if (goingAfterCreature) {
				d = moveAwayFromDanger(currentDest);
			
			} else {
				// No good creature to go see, keep searching in spiral
				// formation

				d = moveAwayFromDanger(p, ssPlayer);
			}
		}

		whereIWasFourTurnsAgo = whereIWasThreeTurnsAgo;
		whereIWasThreeTurnsAgo = whereIWasTwoTurnsAgo;
		whereIWasTwoTurnsAgo = whereIWasOneTurnAgo;
		whereIWasOneTurnAgo = whereIAm;
		return d;
	}

	@Override
	public void newGame(Set<SeaLifePrototype> seaLifePossibilites, int penalty,
			int d, int r, int n) {
		id = getId();
		tickCount = 0;
		// log.debug(id + "new game " + getId());
		this.d = d;
		this.r = r;
		this.n = n;
		this.trackingJudgeThreshold = Math.max(8, (int) (r * .8));
		this.myIncomingMessages = new ArrayList<Set<iSnorkMessage>>();
		dangerousCreaturesAroundYou = new ArrayList<DangerousCreature>();
		this.seaLifePossibilities = seaLifePossibilites;
		this.computeMaximumScore();
		this.computeDangerDensity();
		this.filterOutSLP(this.seaLifePossibilities, topN);
		encodedCommunication = new EncodedCommunication(
				this.topSLPossibilities, r);
		encodedCommunication.buildTree();
		endGame = false;
		preEndGameStrategy = false;
		myCommunication = new Communication(d, r, n, id);
		myCommunication.createCreatureObjects(seaLifePossibilities);
		this.nameOfCreaturePursuing = null;
		this.idOfCreaturePursuing = -3000;
		locationOfCreatureImTrackingOneTickAgo = null;
		eachPlayersApproximateScore = new double[n];
		initializeToZero(eachPlayersApproximateScore);
		initializeWhatEachPersonHasSeen();
		intermediateCheckpoint = null;
		whereIWasOneTurnAgo = null;
		whereIWasTwoTurnsAgo = null;
		whereIWasThreeTurnsAgo = null;
		whereIWasFourTurnsAgo = null;
		// myCreaturesToTrack = new ArrayList<CreatureToTrack>();
	}

	public void initializeToZero(double[] eachPlayersApproximateScore) {
		for (int i = 0; i < eachPlayersApproximateScore.length; i++) {
			eachPlayersApproximateScore[i] = 0;
		}
	}

	public double timeToDest(Point2D dest) {
		// minimal time to destination
		double x1 = whereIAm.getX();
		double y1 = whereIAm.getY();
		double x2 = dest.getX();
		double y2 = dest.getY();
		double deltaX = Math.abs(x1 - x2);
		double deltaY = Math.abs(y1 - y2);
		double diagonal = Math.min(deltaX, deltaY);
		double orthogonal = Math.abs(deltaX - deltaY);
		return diagonal * 3 + orthogonal * 2;
	}

	public double timeToHome() {
		return timeToDest(new Point2D.Double(0, 0));
	}

	public Direction moveToDest(Point2D dest) {
		double x1 = whereIAm.getX();
		double y1 = whereIAm.getY();
		double x2 = dest.getX();
		double y2 = dest.getY();
		double deltaX = Math.abs(x1 - x2);
		double deltaY = Math.abs(y1 - y2);
		// already on dest
		if (deltaX == 0 && deltaY == 0)
			return null;
		// always perform diagonal moves after all orthogonal moves
		boolean diag = (deltaX == deltaY);
		if (diag) {
			if (x2 > x1 && y2 < y1)
				return Direction.NE;
			else if (x2 < x1 && y2 < y1)
				return Direction.NW;
			else if (x2 < x1 && y2 > y1)
				return Direction.SW;
			else if (x2 > x1 && y2 > y1)
				return Direction.SE;
		} else {
			if (deltaX > deltaY) {
				if (x2 > x1)
					return Direction.E;
				else if (x2 < x1)
					return Direction.W;
			} else if (deltaX < deltaY) {
				if (y2 < y1)
					return Direction.N;
				else if (y2 > y1)
					return Direction.S;
			}
		}
		return null;
	}

	public Direction moveToHome() {
		// log.debug(id + "move home");
		return moveToDest(new Point2D.Double(0, 0));
	}

	public Point2D getCreatureLocation(CreatureToTrack ctt) {
		Point2D location = null;
		for (Observation o : whatYouSee) {
			if (o.getId() == ctt.getId() && o.getName().equals(ctt.getName())) {
				location = o.getLocation();
				break;
			}
		}
		if (location == null)
			return null;

		Point2D movingOffset = getMovingOffset(trackedCreatureLocation,
				location);
		trackedCreatureLocation = location;
		Point2D potentialLocation = new Point2D.Double(location.getX()
				+ movingOffset.getX(), location.getY() + movingOffset.getY());
		// GameEngine.println(id + " The creature to track is: " + ctt.getName()
		// + " with ID: " + ctt.getId() + " and its potential location is: " +
		// potentialLocation);
		return potentialLocation;
	}

	private boolean creatureMoves(Observation o) {
		Iterator<SeaLifePrototype> myIter = seaLifePossibilities.iterator();
		while (myIter.hasNext()) {
			SeaLifePrototype mySLP = myIter.next();
			if (mySLP.getName().equals(o.getName())) {
				if (mySLP.getSpeed() == 1)
					return true;
				else
					return false;
			}
		}
		return false;
	}

	private void updateWhichDiversHaveSeenCreatureImTracking(
			Set<iSnorkMessage> incomingMessages, Set<Observation> whatYouSee) {
		Point2D locationOfCreatureImTracking = null;

		// GameEngine.println(id + " Tick: " + tickCount +
		// " Creature I'm tracking: " + creatureImTracking.getName() +
		// " with ID:" + creatureImTracking.getId());

		Iterator<Observation> myIter = whatYouSee.iterator();
		while (myIter.hasNext()) {
			Observation myObs = myIter.next();
			if (myObs.getName() == creatureImTracking.getName()
					&& myObs.getId() == creatureImTracking.getId()) {
				locationOfCreatureImTracking = myObs.getLocation();
			}
		}

		if (locationOfCreatureImTrackingOneTickAgo != null) {
			Iterator<iSnorkMessage> myIter2 = incomingMessages.iterator();
			while (myIter2.hasNext()) {
				iSnorkMessage myMessage = myIter2.next();

				if (myMessage.getLocation().distance(
						locationOfCreatureImTrackingOneTickAgo) <= r
						&& !(myMessage.getLocation().getX() == 0 && myMessage
								.getLocation().getY() == 0)) {
					// GameEngine.println(id + " Diver " + myMessage.getSender()
					// + " can see the creature I'm tracking");
					diverHasSeenCreatureImTracking[-1 * myMessage.getSender()] = true;
				} else {
					// GameEngine.println(id + " Diver " + myMessage.getSender()
					// + " is at" + myMessage.getLocation() +
					// " and creature I'm tracking is at: " +
					// locationOfCreatureImTrackingOneTickAgo +
					// " the distance b/w them is:" +
					// myMessage.getLocation().distance(locationOfCreatureImTrackingOneTickAgo));
				}
			}
		} else
		// GameEngine.println(id +
		// "Can't see the creature I'm tracking (one tick ago)");

		if (locationOfCreatureImTracking != null) {
			locationOfCreatureImTrackingOneTickAgo = locationOfCreatureImTracking;
		}
	}

	private double findPercentOfDiversThatHaveSeenCreatureImTracking() {
		double numDiversThatHaveSeenCreatureImTracking = 0;
		for (int i = 0; i < diverHasSeenCreatureImTracking.length; i++) {
			if (diverHasSeenCreatureImTracking[i]) {
				numDiversThatHaveSeenCreatureImTracking++;
			}
		}
		return numDiversThatHaveSeenCreatureImTracking / n;
	}

	private void initializeWhatEachPersonHasSeen() {
		eachPlayersCreaturesSeenList = new ArrayList<WhatEachPersonHasSeen>();
		for (int i = 0; i < n; i++) {
			eachPlayersCreaturesSeenList.add(new WhatEachPersonHasSeen(i));
		}
	}

	private void updateWhatEachPersonHasSeen() {
		for (int i = 0; i < n; i++) {
			WhatEachPersonHasSeen w = getWhatDiverHasSeen(i);
			for (int j = 0; j < observedCreatures.size(); j++) {
				ObservedCreature myObservedCreature = observedCreatures.get(j);
				if (myObservedCreature.senderId == -1 * i) {
					Creature myCreature = w.getCreature(myObservedCreature.slp
							.getName());
					if (myCreature == null) // snorkeler has not seen this
					// creature befoer
					{
						myCreature = new Creature(
								getSeaLifePrototype(myObservedCreature.slp
										.getName()));
						w.addToList(myCreature);
					}
					myCreature.sawCreature(myObservedCreature.id,
							myObservedCreature.tick);
				}
			}
		}
	}

	private void printWhatEachDiverHasSeen() {
		for (int i = 0; i < n; i++) {
			WhatEachPersonHasSeen w = getWhatDiverHasSeen(i);
			ArrayList<Creature> creatureList = w.getCreatureList();
			for (int j = 0; j < creatureList.size(); j++) {
				GameEngine.println("Player " + i + " has seen "
						+ creatureList.get(j).getName() + " "
						+ creatureList.get(j).getNumTimesSeen() + " times");
			}
		}
	}

	private void updateScoreArray() {
		for (int i = 0; i < eachPlayersApproximateScore.length; i++) {
			WhatEachPersonHasSeen w = getWhatDiverHasSeen(i);
			eachPlayersApproximateScore[i] = w.getTotalScoreFromThisDiver();
		}
	}

	private WhatEachPersonHasSeen getWhatDiverHasSeen(int i) {
		if (i < 0)
			i = -1 * i;
		for (int j = 0; j < eachPlayersCreaturesSeenList.size(); j++) {
			WhatEachPersonHasSeen whatDiverHasSeen = eachPlayersCreaturesSeenList
					.get(j);
			if (whatDiverHasSeen.getSnorkelerId() == i)
				return whatDiverHasSeen;
		}
		GameEngine.println("ERROR: didn't return what the diver has seen");
		return null;
	}

	private CreatureToTrack creatureToTrack3() {
		double bestScore = Double.MIN_VALUE;
		CreatureToTrack bestCreatureToTrack = null;
		boolean justStartingToTrack = false;
		for (Observation o : whatYouSee) {

			if (id == -2 && o.getId() >= 0) {

				GameEngine.println(id + "Observation: " + o.getName()
						+ " with ID: " + o.getId());

				GameEngine.println(id + "CreatureMoves: " + creatureMoves(o)
						+ "getDensityOfSLP(o.getName()): "
						+ getDensityOfSLP(o.getName()));

				GameEngine.println(id
						+ "   percent Of Players Who Have Seen this is: "
						+ percentOfPlayersWhoHaveSeenThisCreature(o.getName(),
								o.getId()));

				

				GameEngine.println(id
						+ "   areLessThan40PercentOfDiversInTrackingMode(): "
						+ areLessThan40PercentOfDiversInTrackingMode());

				GameEngine.println(id + "   o.getId(): " + o.getId()
						+ " IDofCreatureImTracking: " + IDofCreatureImTracking);


				GameEngine.println("");
			}

			if (creatureMoves(o)
					&& getDensityOfSLP(o.getName()) < DENSITY_THRESHOLD) {
				boolean beenSeenBefore = false;
				for (int i = 0; i < observedCreaturesFromEntireGame.size(); i++) {
					ObservedCreature myObservedCreature = observedCreaturesFromEntireGame
							.get(i);
					if (myObservedCreature.slp.getName().equals(o.getName())) {
						if (getSeaLifePrototype(o) != null) {
							if (compareIds(myObservedCreature.id, o.getId(),
									getSeaLifePrototype(o))) {
								beenSeenBefore = true;
							}
						} 					}
				}

				if (beenSeenBefore == false
						|| o.getId() == IDofCreatureImTracking) {
					double score = getScoreOfSLP(o.getName());
					if (score > bestScore) {
						bestScore = score;
						bestCreatureToTrack = new CreatureToTrack(o.getName(),
								o.getId());
					}
					if (beenSeenBefore == false) {
						justStartingToTrack = true;
					} else {
						justStartingToTrack = false;
					}
				}
			}
		}

		if (bestCreatureToTrack == null) {
			if ((id == -300))
				GameEngine.println(id + "no good creature to track right now");
			return null;
		} else {
			IDofCreatureImTracking = bestCreatureToTrack.getId();
			if (justStartingToTrack) {
				diverHasSeenCreatureImTracking = new boolean[n];
				initializeToFalse(diverHasSeenCreatureImTracking);
				diverHasSeenCreatureImTracking[-1 * id] = true;
			}
			// Maybe need to update it here for everyone else too.
			if (id == -300)
				GameEngine.println(id + "   ****Tracking: "
						+ bestCreatureToTrack.getName() + " with id : "
						+ bestCreatureToTrack.getId());
			return bestCreatureToTrack;
		}
	}


	private CreatureToTrack creatureToTrack() 
	{
		double bestScore=Double.MIN_VALUE; 
		CreatureToTrack bestCreatureToTrack = null;
		boolean justStartingToTrack = false; 
		for (Observation o : whatYouSee) 
		  {
			if(id == -300 && !o.getName().equals("G3: Nautical Navigator "))
			{ 
				
				GameEngine.println(id + "Observation: " + o.getName() + " with ID: " + o.getId());
				
				GameEngine.println(id + "CreatureMoves: " + creatureMoves(o) + "getDensityOfSLP(o.getName()): " + getDensityOfSLP(o.getName()));
				
				GameEngine.println(id + "   percent Of Players Who Have Seen this is: " +  percentOfPlayersWhoHaveSeenThisCreature(o.getName(), o.getId())); 
						
				GameEngine.println(id + "   isSomeoneWithAHigherScoreThanMeTrackingThisCreature: " + isSomeoneWithAHigherScoreThanMeTrackingThisCreature(o.getName(), o.getId())); 
				
				GameEngine.println(id + "   areLessThan40PercentOfDiversInTrackingMode(): " + areLessThan40PercentOfDiversInTrackingMode());
				
				GameEngine.println(id + "   o.getId(): " + o.getId() + " IDofCreatureImTracking: " + IDofCreatureImTracking);
				
				GameEngine.println(id + "   Based on all of this, should we track?: " + (creatureMoves(o) && getDensityOfSLP(o.getName()) < DENSITY_THRESHOLD && percentOfPlayersWhoHaveSeenThisCreature(o.getName(), o.getId()) < .9 && !isSomeoneWithAHigherScoreThanMeTrackingThisCreature(o.getName(), o.getId()) && (areLessThan40PercentOfDiversInTrackingMode() || o.getId() == IDofCreatureImTracking)));
				
				GameEngine.println("");
			}  
			
			boolean worthTracking = false;
			  if(creatureMoves(o) && getDensityOfSLP(o.getName()) < DENSITY_THRESHOLD)
			  {
				  if(getSLP(o.getName()) != null)
				  {
					  if(percentOfPlayersWhoHaveSeenThisCreature(o.getName(), o.getId()) < .9 && !isSomeoneWithAHigherScoreThanMeTrackingThisCreature(o.getName(), o.getId()) && (areLessThan40PercentOfDiversInTrackingMode() || o.getId() == IDofCreatureImTracking))
					  { 
						  worthTracking = true; 
					  }
					  if(worthTracking) //maybe get rid of second part of this 
					  { 
						  double score = getScoreOfSLP(o.getName()); 
						  if(score > bestScore) 
						  { 
							  bestScore = score;
							  bestCreatureToTrack = new CreatureToTrack(o.getName(), o.getId());
						  }
						  if(o.getId() != IDofCreatureImTracking) 
						  { 
							  justStartingToTrack = true; 
						  }
						  else 
						  { 
							  justStartingToTrack = false; 
						  } 
					  }
				  }
			  } 
		  }
  
  if (bestCreatureToTrack==null) 
  { 
	  if((id == -300)) 
		  GameEngine.println(id + "no good creature to track right now"); 
	  return null; 
  } 
  else 
  {
	  IDofCreatureImTracking = bestCreatureToTrack.getId();
	  if(justStartingToTrack) 
	  { 
		  diverHasSeenCreatureImTracking = new boolean[n]; 
		  initializeToFalse(diverHasSeenCreatureImTracking);
		  diverHasSeenCreatureImTracking[-1*id] = true; } //Maybe need to update it here for everyone else too. 
	  if(id == -300) 
		  GameEngine.println(id +"   ****Tracking: " + bestCreatureToTrack.getName() + " with id : " + bestCreatureToTrack.getId()); 
		  return bestCreatureToTrack; 
	  } 
  }
	
	private SeaLifePrototype getSeaLifePrototype(Observation o) {
		Iterator<SeaLifePrototype> myIter = seaLifePossibilities.iterator();
		while (myIter.hasNext()) {
			SeaLifePrototype mySLP = myIter.next();
			if (mySLP.getName().equals(o.getName())) {
				return mySLP;
			}
		}
		return null;
	}

	private void initializeToFalse(boolean[] myArray) {
		for (int i = 0; i < myArray.length; i++) {
			myArray[i] = false;
		}
	}

	public Point2D getMovingOffset(Point2D p1, Point2D p2) {
		if (p1 == null || p2 == null)
			return new Point2D.Double(0, 0);
		else
			return new Point2D.Double(p2.getX() - p1.getX(), p2.getY()
					- p1.getY());
	}

	public Direction moveAwayFromDanger(Point2D p) {
		return moveAwayFromDanger(p, null);
	}

	public Direction moveAwayFromDanger(Point2D p, SpiralStrategyPlayer ssPlayer) {
		ArrayList<Direction> allValidDirections = checkWall(buildAllDirections());
		// d will now be an arraylist of all possible directions we can move in
		// that are valid (i.e. that don't go off the board)
		/*if ((id == -300)) {
			 log.debug(id + "Tick: " + tickCount);
			 log.debug(id + "There are " + allValidDirections.size()+
			 " valid directions that don't go off the board");
			// log.debug(id + "There are " + dangerousCreaturesAroundYou.size()+
			// " dangerous creatures around.");
		}*/

		double minDirectionPenalty = -1000000.0;
		Direction safestDirection = Direction.N;

		ArrayList<Direction> completelySafeDirections = new ArrayList<Direction>();

		Point2D dest = p;

		for (int i = 0; i < allValidDirections.size(); i++) {
			Direction currentDir = allValidDirections.get(i);
			double directionPenalty = 0;
			for (int w = 0; w < dangerousCreaturesAroundYou.size(); w++) {
				DangerousCreature myDangCreat = dangerousCreaturesAroundYou.get(w);
				directionPenalty += myDangCreat.getPenalty(whereIAm,currentDir, !endGame, tickCount);
			}
			//if ((id == -300))
			 //GameEngine.println(id + "directionPenalty for " + currentDir + ": "+ directionPenalty);
			if (directionPenalty > minDirectionPenalty) {
				safestDirection = currentDir;
				minDirectionPenalty = directionPenalty;
			}

			if (directionPenalty >= -.001) // If the direction penalty is 0 then
			// this is a completely safe
			// direction
			{
				completelySafeDirections.add(currentDir);
			} else {
				if (ssPlayer != null) {
					if (currentDir != null) {
						Point2D pointInThisDirection = new Point2D.Double(
								whereIAm.getX() + currentDir.getDx(), whereIAm
										.getY()
										+ currentDir.getDy());
						if (pointInThisDirection.equals(p)) {
							ssPlayer.switchToNextPoint();
							dest = ssPlayer.getCurrentPoint();
							if (intermediateCheckpoint != null) {
								//GameEngine.println(id + "***got close enough to intermediate checkpoint, changing it to null");
								intermediateCheckpoint = null;
							}
						}
					}
				}
			}
		}

		if (completelySafeDirections.size() == 0
				|| completelySafeDirections.size() == 1) {
			
			return safestDirection;
		} else {
			if ((id == -300))
			 GameEngine.println(id + "There are " +
			 completelySafeDirections.size() + " safe directions");
			Direction minDirection = Direction.N;
			double minDistance = Double.MAX_VALUE;
			for (int i = 0; i < completelySafeDirections.size(); i++) {
				Point2D pointAfterMove;
				if (completelySafeDirections.get(i) != null) {
					pointAfterMove = new Point2D.Double(
							completelySafeDirections.get(i).getDx()
									+ whereIAm.getX(), completelySafeDirections
									.get(i).getDy()
									+ whereIAm.getY());
					double distance = pointAfterMove.distance(dest);
					if (distance < minDistance) {
						minDistance = distance;
						minDirection = completelySafeDirections.get(i);
					}
				}
			}
			 if ((id == -300))
			 GameEngine.println(id +
			 "Choosing direction closest towards goal of " + dest + " :" +
			 minDirection);
			return minDirection;
		}
	}

	public ArrayList<Direction> buildAllDirections() {
		ArrayList<Direction> d = new ArrayList<Direction>();
		d.add(Direction.E);
		d.add(Direction.NE);
		d.add(Direction.N);
		d.add(Direction.NW);
		d.add(Direction.W);
		d.add(Direction.SW);
		d.add(Direction.S);
		d.add(Direction.SE);
		d.add(null);
		return d;
	}

	private SeaLifePrototype getSeaLifePrototype(String name) {
		Iterator<SeaLifePrototype> myIter = seaLifePossibilities.iterator();
		while (myIter.hasNext()) {
			SeaLifePrototype mySLP = myIter.next();
			if (mySLP.getName().equals(name)) {
				return mySLP;
			}
		}
		return null;
	}

	public ArrayList<Direction> checkWall(ArrayList<Direction> d) {
		if (whereIAm.getY() == -GameConfig.d) {
			d.remove(Direction.N);
			d.remove(Direction.NW);
			d.remove(Direction.NE);
			// log.debug(id + "wall to the north, don't move N, NW, or NE");
		}
		if (whereIAm.getY() == GameConfig.d) {
			d.remove(Direction.S);
			d.remove(Direction.SW);
			d.remove(Direction.SE);
			// log.debug(id + "Wall to the south, don't move S, SW, or SE");
		}
		if (whereIAm.getX() == -GameConfig.d) {
			d.remove(Direction.W);
			d.remove(Direction.NW);
			d.remove(Direction.SW);
			// log.debug(id + "Wall to the west, don't move W, NW, or SW");
		}
		if (whereIAm.getX() == GameConfig.d) {
			d.remove(Direction.E);
			d.remove(Direction.NE);
			d.remove(Direction.SE);
			// log.debug(id + "Wall to the east, don't move E, NE, or SE");
		}
		return d;
	}

	double percentOfPlayersWhoHaveSeenThisCreature(String name, int cid) {
		Set<Integer> set = new HashSet<Integer>();
		for (ObservedCreature oc : observedCreaturesFromEntireGame)
			if (compareIds(oc.id, cid, oc.slp) && oc.slp.getName().equals(name))
				set.add(oc.senderId);
		// GameEngine.println(id + " The percent of players who have seen the "
		// + name + " with id " + cid + " is: " + 1.0 * set.size() / n);
		return 1.0 * set.size() / n;
	}

	boolean isSomeoneWithAHigherScoreThanMeTrackingThisCreature(String name,
			int cid) {
		for (int i = 0; i < eachPlayersApproximateScore.length; i++)
			if (eachPlayersApproximateScore[i] > eachPlayersApproximateScore[-id]

			     || (eachPlayersApproximateScore[i] == eachPlayersApproximateScore[-id] && i > -id))
			{
				if(id == -300)
					GameEngine.println(id + "Player " + i + " has a higher score than me");
				if (isPlayerTrackingThisCreature(i, name, cid))
				{
					if(id == -300)
						GameEngine.println(id + "and he is tracking " + name + " with id " + cid);
					return true;
				}
				if(id == -300)
					GameEngine.println(id + "But he is not tracking " + name + " with id " + cid);

			}
		return false;
	}

	
	boolean isPlayerTrackingThisCreature(int snorkelerId, String name, int cid)
	{
		if(id == -300 && snorkelerId == 15)
			GameEngine.println(id + "Trying to find out if snorkler " + snorkelerId + " is tracking " + name + " with id: " + cid);
		WhatEachPersonHasSeen wephs=getWhatDiverHasSeen(snorkelerId);
		if (wephs==null)
		{
			if(id == -300 && snorkelerId == 15)
				GameEngine.println(id + "wephs was null");
			return false;
		}
		
		ArrayList<Creature> myClist = wephs.getCreatureList();
		if(id == -300 && snorkelerId == 15)
		{
			GameEngine.println(id + "Size of creatureList: " + myClist.size());
			for(int m = 0; m < myClist.size(); m++)
			{
					GameEngine.println(id + "   Creature snorkler: " + snorkelerId + "has seen: " + myClist.get(m).getName());
			}
		}
		
		Creature c=wephs.getCreature(name);
		
		if (c==null)
		{
			if(id == -300 && snorkelerId == 15)
				GameEngine.println(id + "creature was null");
			return false;
		}
		SpecificCreatureWithUniqueId scwui=c.getSeeingTimes(cid);
		if (scwui==null)
		{
			if(id == -300 && snorkelerId == 15)
				GameEngine.println(id + "SpecificCreatureWithUniqueId was null");
			return false;
		}
		if(id == -300 && snorkelerId == 15)
			GameEngine.println(id + "checking the fcn isTrackingBySeeingTimes");
		return isTrackingBySeeingTimes(scwui.getSeeingTimes(), snorkelerId, c.getName(), cid);
	}
	
	boolean isTrackingBySeeingTimes(ArrayList<Integer> seeingTimes, int snorkelerId, String creatureName, int cid)
	{
		if(snorkelerId == 10 && id == -300)
		{
			GameEngine.println("");
			GameEngine.println(id + "Now calling isTrackingBySeeingTimes for snorkler " + snorkelerId + " and creature: " + creatureName + " with ID: " + cid);
		}
		if (seeingTimes==null || seeingTimes.size()<trackingJudgeThreshold)
		{
			if(snorkelerId == 10 && id == -300)
				GameEngine.println("   (seeingTimes==null || seeingTimes.size()<trackingJudgeThreshold)"); 
			return false;
		}
		int endIndex=seeingTimes.size()-1;
		boolean tracking=true;
		/* current tick - the last seeing time */
		if (tickCount - seeingTimes.get(endIndex) > trackingOffsetThreshold)
		{
			if(snorkelerId == 10 && id == -300)
				GameEngine.println("   (tickCount-seeingTimes.get(endIndex)>trackingOffsetThreshold)");
			return false;
		}
		for (int i=0; i<trackingJudgeThreshold-1; i++)
		{
			/* difference between two adjacent seeing time */
			if (seeingTimes.get(endIndex-i)-seeingTimes.get(endIndex-i-1)>3)
			{
				if(snorkelerId == 10 && id == -300)
					GameEngine.println("   (seeingTimes.get(endIndex-i)-seeingTimes.get(endIndex-i-1)>3)");
				tracking=false;
				break;
			}
		}
		if(snorkelerId == 10 && id == -300)
			GameEngine.println("   return tracking: " + tracking);
		return tracking;
	}

	int numberOfTrackingPlayers() {
		int count = 0;
		for (WhatEachPersonHasSeen wephs : eachPlayersCreaturesSeenList) {
			ArrayList<Creature> cl = wephs.getCreatureList();
			for (Creature c : cl) {
				// log.debug("numberOfTrackingPlayers: name="+c.getName());
				for (SpecificCreatureWithUniqueId scwui : c.getCreaturesSeen()) {
					// log.debug("numberOfTrackingPlayers: id="+scwui.getCreatureID());
					// log.debug("numberOfTrackingPlayers: size="+scwui.getSeeingTimes().size());
					// log.debug("numberOfTrackingPlayers: seetingTimes="+scwui.getSeeingTimes());

					if (isTrackingBySeeingTimes(scwui.getSeeingTimes(), 1, "asdf", -1))
						count++;
				}
			}
		}
		return count;
	}
	
	private boolean areLessThan40PercentOfDiversInTrackingMode()
	{
		if(id == -300)
			GameEngine.println(id + "       # of tracking players: " + numberOfTrackingPlayers() + " n: : " + n);
		if(1.0*numberOfTrackingPlayers()/n < .4)
			return true;
		else
			return false;
	}

	public SpiralStrategyType buildSpiralStrategyType() {
		if (!isBoardDangerous())
			return new SpiralStrategyType(false);
		if (getBoardPositiveValue() / getBoardNegativeValue() > exploreAnywayIfPositiveOverNegative)
			return new SpiralStrategyType(false);

		int radius = 0;
		double boardValue = getBoardValue();
		log.debug(getBoardPositiveValue());
		log.debug(getBoardNegativeValue());
		log.debug(boardValue);
		if (boardValue < 0)
			radius = 1;
		else
			radius = Math.min((int) (1.5 + getBoardPositiveValue()
					/ getBoardNegativeValue()), maxExploreRadiusIfDangerous);

		log.debug("SpiralStrategyType: dangerous radius=" + radius);
		boolean isRepeat = false;
		for (SeaLifePrototype slp : topSLPossibilities) {
			if (slp.getSpeed() > 0) {
				isRepeat = true;
				break;
			}
		}

		return new SpiralStrategyType(true, isRepeat, radius);
	}

	public boolean isBoardDangerous() {
		return (getNumberOfDangerousCreature() > dangerAvoidanceLimit);
	}

	public double getNumberOfDangerousCreature() {
		double staticDangerNum = 0;
		double movingDangerNum = 0;
		double staticDangerCoefficient = 0;
		for (SeaLifePrototype seaLife : seaLifePossibilities) {
			if (seaLife.isDangerous() && seaLife.getSpeed() > 0)
				movingDangerNum += getPredictedNumberOfCreature(seaLife);
			if (seaLife.isDangerous() && seaLife.getSpeed() == 0)
				staticDangerNum += getPredictedNumberOfCreature(seaLife);
		}

		if (movingDangerNum > dangerAvoidanceLimit)
			staticDangerCoefficient = staticOverMovingDanger;
		else
			staticDangerCoefficient = staticOverMovingDanger
					* (movingDangerNum / dangerAvoidanceLimit);

		double totalNum = movingDangerNum + staticDangerNum
				* staticDangerCoefficient;

		//log.debug("numberOfDangerousCreature="+totalNum);
		return totalNum;
	}
	
	public boolean ifNoDangerousCreature()
	{
		for (SeaLifePrototype seaLife : seaLifePossibilities)
			if (seaLife.isDangerous() && seaLife.getMaxCount()>0)
				return false;
		return true;
	}

	public double getBoardValue() {
		return getBoardPositiveValue() - getBoardNegativeValue();
	}

	public double getBoardNegativeValue() {
		double movingDanger = 0;
		double staticDanger = 0;
		double movingDangerNum = 0;
		double staticDangerCoefficient = 0;
		for (SeaLifePrototype seaLife : seaLifePossibilities) {
			if (seaLife.isDangerous() && seaLife.getSpeed() > 0) {
				movingDangerNum += getPredictedNumberOfCreature(seaLife);
				movingDanger += 2.0 * seaLife.getHappiness()
						* getPredictedNumberOfCreature(seaLife);
			}
			if (seaLife.isDangerous() && seaLife.getSpeed() == 0)
				staticDanger += 2.0 * seaLife.getHappiness()
						* getPredictedNumberOfCreature(seaLife);
		}

		if (movingDangerNum > dangerAvoidanceLimit)
			staticDangerCoefficient = staticOverMovingDanger;
		else
			staticDangerCoefficient = staticOverMovingDanger
					* (movingDangerNum / dangerAvoidanceLimit);

		return movingDanger + staticDanger * staticDangerCoefficient;
	}

	public double getBoardPositiveValue() {
		double happiness = 0;
		for (SeaLifePrototype seaLife : seaLifePossibilities)
			if (!seaLife.isDangerous() || (seaLife.isDangerous() && seaLife.getSpeed()==0))
				happiness += getPredictedHappinessOfCreature(seaLife);
		return happiness;
	}

	public double getPredictedHappinessOfCreature(SeaLifePrototype seaLife) {
		double num = getPredictedNumberOfCreature(seaLife);
		double happiness = seaLife.getHappiness();
		int iNum = (int) (num + 0.5);
		if (iNum == 1)
			return happiness;
		else if (iNum == 2)
			return happiness * 1.5;
		else if (iNum >= 3)
			return happiness * 1.75;
		else
			return 0;
	}

	public double getPredictedNumberOfCreature(SeaLifePrototype seaLife) {
		if (seaLife.getHappiness()==0)
			return 0;
		
		double diff = getNumberOfAllCreatures() - getNumberOfCreature(seaLife);
		double num = getNumberOfCreature(seaLife);
		if (diff > 2)
			return num;

		int maxId = Integer.MIN_VALUE;
		for (ObservedCreature oc : observedCreaturesFromEntireGame)
			if (oc.slp.equals(seaLife))
				if (oc.id > maxId)
					maxId = id;
		if (maxId > num)
			return maxId;

		return num;
	}

	public double getNumberOfCreature(SeaLifePrototype seaLife) {
		return (seaLife.getMinCount() + seaLife.getMaxCount()) / 2.0;
	}

	public double getNumberOfAllCreatures() {
		double num = 0;
		for (SeaLifePrototype seaLife : seaLifePossibilities)
			num += getNumberOfCreature(seaLife);
		return num;
	}
}
