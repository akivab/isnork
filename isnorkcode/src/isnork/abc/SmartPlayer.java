package isnork.abc;


import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Set;

import isnork.sim.GameConfig;
import isnork.sim.Observation;
import isnork.sim.Player;
import isnork.sim.SeaLifePrototype;
import isnork.sim.iSnorkMessage;
import isnork.sim.GameObject.Direction;



public class SmartPlayer extends Player {

	private Direction direction;
	private Direction getNewDirection() {
		int r = random.nextInt(100);
		if(r < 10 || direction == null)
		{
			ArrayList<Direction> directions = Direction.allBut(direction);
			direction = directions.get(1);
		}
		return direction;
	}
	@Override
	public String getName() {
		return "Dumb Player 0";
	}
	Point2D whereIAm = null;
	int n = -1;
	@Override
	public String tick(Point2D myPosition, Set<Observation> whatYouSee,
			Set<iSnorkMessage> incomingMessages,Set<Observation> playerLocations) {
		whereIAm = myPosition;
		if(n % 10 == 0)
			return "s";
		else
			return null;
	}
	@Override
	public Direction getMove() {
		Direction d = getNewDirection();
		
		Point2D p = new Point2D.Double(whereIAm.getX() + d.dx,
				whereIAm.getY() + d.dy);
		while (Math.abs(p.getX()) > GameConfig.d
				|| Math.abs(p.getY()) > GameConfig.d) {
			d = getNewDirection();
			p = new Point2D.Double(whereIAm.getX() + d.dx,
					whereIAm.getY() + d.dy);
		}
		return d;
	}
	@Override
	public void newGame(Set<SeaLifePrototype> seaLifePossibilites, int penalty,
			int d, int r, int n) {
		// TODO Auto-generated method stub
		
	}




}
