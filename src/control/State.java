package control;

import java.util.LinkedList;
import java.util.List;

import visualization.SmoothedVisualizer;
import visualization.Visualizer;

public class State {
    public double x;
    public double y;
    public double cumDist;
    public double angle;
    
    public State(double x, double y, double cumDist, double angle) {
        this.x = x;
        this.y = y;
        this.cumDist = cumDist;
        this.angle = angle;
    }
    
    public static State fromPreviousCircle(State previous, double dDist, double dAng) {
        if (Math.abs(dAng) < 0.00000001) return fromPreviousMoveThenTurn(previous, dDist, dAng);
        
        double turningRadius = dDist / dAng; //turning circumference = dDist * (2PI/dAng), and turning radius = circumference / 2PI
        
        double unwoundAngle = dAng + previous.angle;
        /*double unwoundOldDX = turningRadius * Math.cos(previous.angle);
        double unwoundOldDY = turningRadius * Math.sin(previous.angle);
        double unwoundX = previous.x - unwoundOldDX;
        double unwoundY = previous.y - unwoundOldDY;*/
        double turnCenterX = previous.x + (turningRadius * Math.cos(previous.angle + Math.PI / 2));
        double turnCenterY = previous.y + (turningRadius * Math.sin(previous.angle + Math.PI / 2));
        double unwoundX = turnCenterX + turningRadius;
        double unwoundY = turnCenterY;

        double unwoundDX = turningRadius * Math.cos(unwoundAngle - Math.PI / 2);
        double unwoundDY = turningRadius * Math.sin(unwoundAngle - Math.PI / 2);
        
        return new State(turnCenterX + unwoundDX, turnCenterY + unwoundDY, previous.cumDist + dDist, previous.angle + dAng);
    }
    
    public static State fromPreviousMoveThenTurn(State previous, double dDist, double dAng) {
        double dX = dDist * Math.cos(previous.angle);
        double dY = dDist * Math.sin(previous.angle);
        return new State(previous.x + dX, previous.y + dY, previous.cumDist + dDist, previous.angle + dAng);
    }
    
    public static State fromPreviousTurnThenMove(State previous, double dDist, double dAng) {
        double dX = dDist * Math.cos(previous.angle + dAng);
        double dY = dDist * Math.sin(previous.angle + dAng);
        return new State(previous.x + dX, previous.y + dY, previous.cumDist + dDist, previous.angle + dAng);
    }
    
    @Override
    public String toString() {
        return String.format("(%f, %f, %f, %f)", x, y, cumDist, angle * 180 / Math.PI);
    }
    
    public static final State INITIAL = new State(0, 0, 0, 0);
    
    public static void main (String[] args) {
        List<State> states = new LinkedList<>();
        Visualizer raw = new Visualizer("Raw", states);
        Visualizer smoothed = new SmoothedVisualizer("Smoothed", states);
        states.add(INITIAL);
        for(int x = 0; x < 10; x++) {
            states.add(fromPreviousCircle(states.get(states.size() - 1), 5, 0));
        }
        for(int x = 0; x < 5; x++) {
            states.add(fromPreviousCircle(states.get(states.size() - 1), 5, Math.PI / 20));
        }
        for(int x = 0; x < 10; x++) {
            states.add(fromPreviousCircle(states.get(states.size() - 1), 5, 0));
        }
        for(int x = 0; x < 5; x++) {
            states.add(fromPreviousCircle(states.get(states.size() - 1), 5, Math.PI / 20));
        }
        for(int x = 0; x < 10; x++) {
            states.add(fromPreviousCircle(states.get(states.size() - 1), 5, 0));
        }
        for(int x = 0; x < 5; x++) {
            states.add(fromPreviousCircle(states.get(states.size() - 1), 5, -Math.PI / 20));
        }
        for(int x = 0; x < 10; x++) {
            states.add(fromPreviousCircle(states.get(states.size() - 1), 5, 0));
        }
        for(int x = 0; x < 5; x++) {
            states.add(fromPreviousCircle(states.get(states.size() - 1), 5, -Math.PI / 20));
        }
        for(int x = 0; x < 10; x++) {
            states.add(fromPreviousCircle(states.get(states.size() - 1), 5, 0));
        }
        raw.repaint();
        smoothed.repaint();
    }
}
