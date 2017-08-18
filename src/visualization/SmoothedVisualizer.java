package visualization;

import java.awt.Graphics;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import control.State;

public class SmoothedVisualizer extends Visualizer {
    double distanceThreshold;
    double angleThreshold;
    
    public SmoothedVisualizer(String name, List<State> states, double distanceThreshold, double angleThreshold) {
        super(name, states);
        this.distanceThreshold = distanceThreshold;
        this.angleThreshold = angleThreshold;
    }
    
    public SmoothedVisualizer(String name, List<State> states) {
        this(name, states, 400, 70 * Math.PI / 180);
    }
    
    @Override
    public void paint(Graphics g) {
        List<State> storedStates = states;
        states = smoothed(states);
        super.paint(g);
        states = storedStates;
    }
    
    public List<State> smoothed(List<State> states) {
        if (states.isEmpty()) return states;
        
        states = new ArrayList<>(states);
        List<State> smoothedStates = new ArrayList<>();
        smoothedStates.add(states.get(0));
        int startIndex = 0;
        int endIndex = 0;
        while (endIndex < states.size()) {
            State start = states.get(startIndex);
            State end = states.get(endIndex);
            if (Math.abs(end.angle - start.angle) >= angleThreshold) {
                int midIndex = (startIndex + endIndex) / 2; //A little hacky, but it generally works.
                State midState = states.get(midIndex);
                State previous = smoothedStates.get(smoothedStates.size() - 1);
                smoothedStates.add(State.fromPreviousMoveThenTurn(previous, midState.cumDist - previous.cumDist, end.angle > start.angle ? Math.PI / 2 : -Math.PI / 2));
                startIndex = endIndex;
            } else {
                if (end.cumDist - start.cumDist < distanceThreshold) {
                    endIndex++;
                } else {
                    startIndex++;
                }
            }
        }
        State previous = smoothedStates.get(smoothedStates.size() - 1);
        smoothedStates.add(State.fromPreviousMoveThenTurn(previous, states.get(states.size() - 1).cumDist - previous.cumDist, 0));
        
        return smoothedStates;
    }
}
