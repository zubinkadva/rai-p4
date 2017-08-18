package visualization;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Iterator;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;

import control.State;

public class Visualizer extends JPanel {
    protected List<State> states;
    private JFrame containingFrame;
    
    private int xOffset = 0;
    private int yOffset = 0;
    private double xScale = 1;
    private double yScale = 1;
    
    private boolean drawTangents = false;
    
    public Visualizer(String name, List<State> states) {
        this.states = states;
        
        containingFrame = new JFrame();
        containingFrame.getContentPane().add(this);
        containingFrame.setSize(500, 500);
        containingFrame.setTitle(name);
        containingFrame.setVisible(true);
        
        containingFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
    }

    public void paint(Graphics g) {
        update();
        super.paintComponent(g);
        if (states.isEmpty()) return;
        Iterator<State> stateIter = states.iterator();
        State previous = stateIter.next();
        //System.out.printf("X: %d\tY: %d%n", r2cX(previous.x), r2cY(previous.y));
        while (stateIter.hasNext()) {
            State current = stateIter.next();
            //System.out.printf("X: %d\tY: %d%n", r2cX(current.x), r2cY(current.y));
            if (r2cY(current.y) > this.getHeight() || r2cX(current.x) < 0) {
                System.out.println("Break");
                System.out.printf("X: %d\tY: %d%n", r2cX(current.x), r2cY(current.y));
                System.out.println(this.getHeight());
            }
            g.setColor(Color.BLACK);
            g.drawLine(r2cX(previous.x), r2cY(previous.y), r2cX(current.x), r2cY(current.y));
            if (drawTangents) {
                State tangent = State.fromPreviousMoveThenTurn(current, 100, 0);
                g.setColor(Color.BLUE);
                g.drawLine(r2cX(current.x), r2cY(current.y), r2cX(tangent.x), r2cY(tangent.y));
            }
            previous = current;
        }
    }
    
    private void update() {
        int minX = 0;
        int maxX = 0;
        int minY = 0;
        int maxY = 0;
        
        for (State state : states) {
            int sX = roboToCanvas(state.x);
            int sY = roboToCanvas(state.y);
            
            minX = Math.min(minX, sX);
            maxX = Math.max(maxX, sX);
            minY = Math.min(minY, sY);
            maxY = Math.max(maxY, sY);
        }
        
        //Add a 10% buffer area on all sides
        xOffset = -minX + this.getWidth() / 10;
        yOffset = -minY + this.getHeight() / 10;
        xScale = Math.max(maxX - minX, maxY - minY) / (0.8d * this.getWidth());
        yScale = Math.max(maxX - minX, maxY - minY) / (0.8d * this.getHeight());
    }

    public void enableTangents() {drawTangents = true;}
    public void disableTangents() {drawTangents = false;}
    
    static int roboToCanvas(double roboCoord) {
        return (int)Math.round(roboCoord);
    }

    int r2cX(double roboCoord) {return roboToCanvas((roboCoord + xOffset) / xScale);}
    int r2cY(double roboCoord) {return this.getHeight() - roboToCanvas((roboCoord + yOffset) / yScale);}
}
