package isnork.g3;

import isnork.sim.GameEngine;


public class Test
{
	double alpha=1.0/2;
	double d=20;
	
	public static void main(String[] args)
	{	
		Test t=new Test();
		GameEngine.println(t.value(100, t.density(15)));
		GameEngine.println(t.value(26, t.density(1)));
	}
	
	public double value(double score, double density, double alpha)
	{
		return score / Math.pow(density, alpha);
	}
	
	public double value(double score, double density)
	{
		return value(score, density, alpha);
	}
	
	public double density(double num, double d)
	{
		return num / Math.pow(d*2+1, 2);
	}
	
	public double density(double num)
	{
		return density(num, d);
	}
}
