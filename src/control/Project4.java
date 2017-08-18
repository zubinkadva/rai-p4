package control;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import visualization.SmoothedVisualizer;
import visualization.Visualizer;

//Some constants and port connection code taken from the RoombaComm package and modified

public class Project4 {
    static final int rate     = 57600;
    static final int databits = 8;
    static final int parity   = SerialPort.PARITY_NONE;
    static final int stopbits = SerialPort.STOPBITS_1;
    
    static SerialPort port;
    
    static InputStream input;
    static OutputStream output;

    static byte[] sensorData = new byte[52];
    
    static final int SENSOR_PERIOD = 200; //How long you have to wait between sensor requests, in milliseconds. (Plus a little bit, just in case.)
    
    /** distance between wheels on the roomba, in millimeters */
    public static final int wheelbase = 258;
    public static final double millimetersPerDegree = wheelbase * Math.PI / 360;
    static final int ROBOT_RADIUS = 169;

    static final int SPEED = 300;
    
    static final int WALL_THRESHOLD = 50;
    static final int WALL_MINIMUM = 0; //The minimum strength of the wall sensor
    static final int WALL_MAXIMUM = 200; //The maximum strength of the wall sensor
    
    static final int MAX_TURN_RATE = (int)(ROBOT_RADIUS * 1.5);
    static final int MIN_TURN_RATE = 2000; //The maximum turning radius allowed by the robot
    
    static final int TARGET_DISTANCE = 25_000;
    
    public static void main(String[] args) throws InterruptedException, IOException {
        //Add a shutdown hook to gracefully stop the iRobot and close the connection.
        //This both keeps the robot from continually running if the program is closed (normally), and
        //allows a new connection to be formed with the robot without power-cycling it.
        Runtime.getRuntime().addShutdownHook(new Thread(Project4::end));
        
        System.out.println("Initializing");
        init(false);
        System.out.println("Initialization done. Locating wall.");
        localize();
        System.out.println("Wall located. Aligning orientation with the wall.");
        orient();
        System.out.println("Oriented. Beginning mapping.");
        List<State> rawStates = map(TARGET_DISTANCE);
        printResults(rawStates);
    }
    
    static void init(boolean safeMode) throws InterruptedException, IOException {
        open_port("COM3");
        sendStart();
        if (safeMode) {
            sendSafe();
        } else {
            sendFull();
        }
        sendLEDs(true, true, 64, 255);
    }
    
    static void localize() throws IOException, InterruptedException {
        int dist = 0;
        while (true) {
            collectSensors();
            if (getBump()) break;
            dist += getDistance();
            if (dist > 300) {
                sendStop();
                doBackward(SPEED, dist);
                doSpinLeft(SPEED, 90);
            }
            sendForward(SPEED);
        }
        
        sendStop();
    }
    
    static void orient() throws IOException, InterruptedException {
        sendSpinLeft(100);
        while (true) {
            collectSensors();
            if (getWallStrength() > WALL_THRESHOLD) break;
        }
        doSpinLeft(100, 10);
    }
    
    static List<State> map(int targetDistance) throws IOException, InterruptedException {
        int distanceTravelled = 0;
        List<State> states = new LinkedList<>();
        states.add(State.INITIAL);
        Visualizer visualizer = new Visualizer("Raw", states);
        Visualizer smoothedVisualizer = new SmoothedVisualizer("Smoothed", states);
        collectSensors(); //Clear the accumulated sensor data
        boolean lastMoveBump = false;
        
        while(true) {
            collectSensors();
            
            double deltaDist = getDistance();
            distanceTravelled += deltaDist;
            double deltaAngle = getAngle();
            boolean bump = getBump();
            
            //Update state
            State previous = states.get(states.size() - 1);
            states.add(lastMoveBump ? State.fromPreviousMoveThenTurn(previous, deltaDist, deltaAngle) : State.fromPreviousCircle(previous, deltaDist, deltaAngle));
            visualizer.repaint();
            smoothedVisualizer.repaint();
            
            lastMoveBump = bump;
            
            System.out.printf("Distance Travelled: %d / %d\t", distanceTravelled, targetDistance);
            System.out.printf("Current State: %s%n", states.get(states.size() - 1));
            System.out.printf("Delta Angle: %d%n", Math.round(deltaAngle * 180 / Math.PI));
            if (distanceTravelled >= targetDistance) break;
            if (bump) {
                doBackward(SPEED, 20);
                doSpinLeft(SPEED, 30);
            } else {
                int wall = Math.min(Math.max(getWallStrength(), WALL_MINIMUM), WALL_MAXIMUM);
                double threshDist = Math.abs(WALL_THRESHOLD - wall);
                int turningRadius;
                if (wall < WALL_THRESHOLD) {
                    double threshFraction = threshDist / (WALL_THRESHOLD - WALL_MINIMUM);
                    turningRadius = -(int)Math.round((threshFraction * MAX_TURN_RATE) + ((1 - threshFraction) * MIN_TURN_RATE));
                } else {
                    double threshFraction = threshDist / (WALL_MAXIMUM - WALL_THRESHOLD);
                    turningRadius = (int)Math.round((threshFraction * MAX_TURN_RATE) + ((1 - threshFraction) * MIN_TURN_RATE));
                }
                sendDrive(SPEED, turningRadius);
            }
        }
        
        sendStop();
        return states;
    }
    
    static void printResults(List<State> rawStates) {
        List<State> smoothedStates = new SmoothedVisualizer("Final Map", rawStates).smoothed(rawStates);
        System.out.println("----------MAP----------");
        if (!smoothedStates.isEmpty()) {
            for (int i = 1; i < smoothedStates.size(); i++) {
                State curr = smoothedStates.get(i);
                State prev = smoothedStates.get(i - 1);
                System.out.printf("GO %dmm%n", (int)(curr.cumDist - prev.cumDist));
                if (i < smoothedStates.size() - 1) System.out.printf("TURN %s%n", curr.angle > prev.angle ? "LEFT" : "RIGHT");
            }
        }
        System.out.println("--------END MAP--------");
    }
    
    static void end() {
        System.out.println("Stopping iRobot and closing connection.");
        if (output != null) {
            try {
                sendStop();
            } catch (IOException e) {
                System.err.println("Failed to send stop command.");
            }
        }
        port.close();
    }
    
    //CONVERSION METHODS AND UTILITIES
    
    static byte toByte(long l) {
        return (byte)(l & 0xFF);
    }
    
    static byte[] b(int... vals) {
        byte[] result = new byte[vals.length];
        for (int x = 0; x < vals.length; x++) result[x] = toByte(vals[x]);
        return result;
    }
    
    static byte hi(long value) {
        return (byte)((value >> 8) & 0xFF);
    }
    
    static byte lo(long value) {
        return (byte)(value & 0xFF);
    }
    
    //SENDING COMMANDS
    
    static void send(byte... bytes) throws IOException {
        output.write(bytes);
        output.flush();
    }
    
    static void send(int... vals) throws IOException {send(b(vals));}
    
    static void sendStream() throws IOException {send(148, 1, 6);}
    
    static void sendStart() throws IOException {send(128);}
    static void sendSafe() throws IOException {send(131);}
    static void sendFull() throws IOException {send(132);}
    static void sendDemo(int demo) throws IOException {send(136, demo);}
    
    static void sendDrive(int speed, int radius) throws IOException {send(137, hi(speed), lo(speed), hi(radius), lo(radius));}
    static void sendForward(int speed) throws IOException {sendDrive(speed, 0x8000);}
    static void sendBackward(int speed) throws IOException {sendDrive(-speed, 0x8000);}
    static void sendSpinLeft(int speed) throws IOException {sendDrive(speed, 0x0001);}
    static void sendSpinRight(int speed) throws IOException {sendDrive(speed, 0xFFFF);}
    static void sendStop() throws IOException {sendDrive(0, 0);}

    static void sendSensors() throws IOException {send(142, 6);}
    
    static void sendLEDs(boolean advance, boolean play, int powerColor, int powerIntensity) throws IOException {
        int a = advance ? 1 : 0;
        int p = play ? 1 : 0;
        send(139, (a << 3) | (p << 1), powerColor, powerIntensity);
    }
    
    //COMPILING ACTIONS

    static long lastCollectTime = System.currentTimeMillis();
    static void collectSensors() throws IOException, InterruptedException {
        long time = System.currentTimeMillis();
        if (time < lastCollectTime + SENSOR_PERIOD) Thread.sleep(lastCollectTime + SENSOR_PERIOD - time);
        sendSensors();
        lastCollectTime = System.currentTimeMillis();
        updateSensors();
    }
    
    static void doDrive(int speed, int radius, int distance) throws IOException, InterruptedException {
        sendDrive(speed, radius);
        Thread.sleep((distance * 1_000) / Math.abs(speed));
        sendStop();
    }

    static void doForward(int speed, int distance) throws IOException, InterruptedException {doDrive(speed, 0x8000, distance);}
    static void doBackward(int speed, int distance) throws IOException, InterruptedException {doDrive(-speed, 0x8000, distance);}
    static void doSpinLeft(int speed, double degrees) throws IOException, InterruptedException {doDrive(speed, 0x0001, (int)(millimetersPerDegree * degrees));}
    static void doSpinRight(int speed, double degrees) throws IOException, InterruptedException {doDrive(speed, 0xFFFF, (int)(millimetersPerDegree * degrees));}
    
    //RECEIVING DATA
    
    static void updateSensorsFromStream() throws IOException, InterruptedException {
        while(input.available() < 3) Thread.sleep(1);
        
        int responseCode = input.read(); //19 header
        if (responseCode != 19) throw new IOException(String.format("Invalid response code: %d", responseCode));
        
        int length = input.read();
        if (length != sensorData.length + 1) throw new IOException(String.format("Invalid sensor packet length: %d", length));
        
        int packageId = input.read();
        if (packageId != 6) throw new IOException(String.format("Unexpected package ID: %d", packageId));
        
        updateSensors();
        
        while(input.available() < 1) Thread.sleep(1);
        
        int checksum = input.read();
        int sum = length + packageId + checksum;
        for (byte b : sensorData) sum += b & 0xFF;
        //if ((sum & 0xFF) != 0) System.err.println(String.format("Checksum validation failed: %d", sum)); //The checksum is currently behaving strangely.
    }
    
    static void updateSensors() throws IOException, InterruptedException {
        int totalRead = 0;
        while (totalRead < sensorData.length) {
            totalRead += input.read(sensorData, totalRead, sensorData.length - totalRead);
        }
    }
    
    //READING SENSORS
    
    static int getValues(int start, int length) {
        int result = 0;
        for (int x = start; x < start + length; x++) result = (result << 8) | (sensorData[x] & 0xFF);
        return result;
    }
    
    static int signed(int unsignedShort) {
        if (unsignedShort >> 15 == 1) return 0xFFFF0000 | unsignedShort;
        else return unsignedShort;
    }
    
    static int getSignedValues(int start, int length) {return signed(getValues(start, length));}
    
    static int getWallStrength() {return getValues(26, 2);}

    static boolean getWall() {return getValues(1, 1) == 1;}
    
    static boolean getBumpLeft() {return (getValues(0, 1) & 0b00000010) != 0;}
    static boolean getBumpRight() {return (getValues(0, 1) & 0b00000001) != 0;}
    static boolean getBump() {return getBumpLeft() || getBumpRight();}
    
    static int getDistance() {return getSignedValues(12, 2);} //Millimeters
    
    static double getAngle() {return getSignedValues(14, 2) * Math.PI / 180;} //Converted from degrees to radians
    
    //DEALING WITH PORTS

    //Taken from RoombaCommSerial (and slightly modified)
    static boolean open_port(String portname) {
        boolean success = false;
        try {
            @SuppressWarnings("unchecked")
            List<CommPortIdentifier> portList = Collections.list((Enumeration<CommPortIdentifier>)CommPortIdentifier.getPortIdentifiers());
            for(CommPortIdentifier portId : portList) {
                if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                    System.out.println("found " + portId.getName());
                    if (portId.getName().equals(portname)) {
                        System.out.println("open_port:"+ portId.getName());
                        port = (SerialPort)portId.open("Compliant Planner", 2000);
                        input  = port.getInputStream();
                        output = port.getOutputStream();
                        port.setSerialPortParams(rate,databits,stopbits,parity);
                        System.out.println("port "+portname+" opened successfully");
                        System.out.printf("Port receive threshold enabled: %b%n", port.isReceiveThresholdEnabled());
                        System.out.printf("Port receive framing enabled: %b%n", port.isReceiveFramingEnabled());
                        System.out.printf("Port receive timeout enabled: %b%n", port.isReceiveTimeoutEnabled());
                        success = true;
                    }
                }
            }
      
        } catch (Exception e) {
            System.out.println("connect failed: "+e);
            port = null;
            input = null;
            output = null;
        }
                        
        return success;
    }
}
