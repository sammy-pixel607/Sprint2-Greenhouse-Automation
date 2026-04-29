import java.util.*;
import java.io.*;
import java.time.LocalDateTime;

// ================= MAIN =================

class GreenhouseSystem {

    static void main() {

        Scanner sc = new Scanner(System.in);
        Config.load();

        GreenhouseController controller = new GreenhouseController();

        System.out.println("=== SMART GREENHOUSE SYSTEM ===");

        while (true) {

            System.out.println("\n1. Manual Input");
            System.out.println("2. Auto Simulation");
            System.out.println("3. Show Statistics");
            System.out.println("4. Export CSV");
            System.out.println("5. Switch Strategy");
            System.out.println("6. Show History");
            System.out.println("7. Exit");
            System.out.print("Choice: ");

            int choice = sc.nextInt();

            try {
                switch (choice) {

                    case 1:
                        Environment env1 = new Environment(
                                readDouble(sc, "Temperature: "),
                                readDouble(sc, "Soil Moisture: "),
                                readDouble(sc, "Humidity: ")
                        );
                        controller.regulate(env1);
                        controller.printStatus(env1);
                        break;

                    case 2:
                        Environment env2 = Environment.autoGenerate();
                        System.out.println("AUTO → " + env2);
                        controller.regulate(env2);
                        controller.printStatus(env2);
                        break;

                    case 3:
                        controller.showStatistics();
                        break;

                    case 4:
                        controller.exportCSV();
                        break;

                    case 5:
                        controller.toggleStrategy();
                        break;

                    case 6:
                        controller.printHistory(); // recursion
                        break;

                    case 7:
                        controller.saveHistory();
                        System.out.println("Exiting...");
                        return;
                }

            } catch (SensorException e) {
                System.out.println("ERROR: " + e.getMessage());
                Logger.log("ERROR", e.getMessage());
            }
        }
    }

    static double readDouble(Scanner sc, String msg) {
        while (true) {
            System.out.print(msg);
            if (sc.hasNextDouble()) return sc.nextDouble();
            System.out.println("Invalid input!");
            sc.next();
        }
    }
}

// ================= ENVIRONMENT =================
class Environment implements Serializable {
    private final double temperature;
    private final double soilMoisture;
    private final double humidity;

    public Environment(double t, double s, double h) {
        temperature = t;
        soilMoisture = s;
        humidity = h;
    }

    public double getTemperature() { return temperature; }
    public double getSoilMoisture() { return soilMoisture; }
    public double getHumidity() { return humidity; }

    public static Environment autoGenerate() {
        Random r = new Random();
        return new Environment(
                15 + r.nextDouble() * 25,
                20 + r.nextDouble() * 60,
                30 + r.nextDouble() * 50
        );
    }

    public String toString() {
        return "Temp=" + temperature +
                " | Soil=" + soilMoisture +
                " | Humidity=" + humidity;
    }
}

// ================= CONFIG =================
class Config {
    static double MAX_TEMP = 28;
    static double MIN_TEMP = 18;
    static double MIN_SOIL = 35;

    public static void load() {
        try (BufferedReader br = new BufferedReader(new FileReader("config.txt"))) {
            Properties p = new Properties();
            p.load(br);
            MAX_TEMP = Double.parseDouble(p.getProperty("MAX_TEMP"));
            MIN_TEMP = Double.parseDouble(p.getProperty("MIN_TEMP"));
            MIN_SOIL = Double.parseDouble(p.getProperty("MIN_SOIL"));
        } catch (Exception e) {
            System.out.println("Using default config.");
        }
    }
}

// ================= INTERFACE =================
interface Controllable {
}

// ================= DEVICE =================
abstract class Device implements Controllable {
    protected boolean isOn = false;
    public boolean isRunning() { return isOn; }
}

class Fan extends Device {
    public void on() { if (!isOn) { System.out.println("Fan ON"); isOn = true; } }
    public void off() { if (isOn) { System.out.println("Fan OFF"); isOn = false; } }
}

class Heater extends Device {
    public void on() { if (!isOn) { System.out.println("Heater ON"); isOn = true; } }
    public void off() { if (isOn) { System.out.println("Heater OFF"); isOn = false; } }
}

class Irrigation extends Device {
    public void on() { if (!isOn) { System.out.println("Irrigation ON"); isOn = true; } }
    public void off() { if (isOn) { System.out.println("Irrigation OFF"); isOn = false; } }
}

// ================= STRATEGY =================
interface ControlStrategy {
    void apply(Environment env, Fan fan, Heater heater, Irrigation irrigation);
}

class DefaultStrategy implements ControlStrategy {
    public void apply(Environment env, Fan fan, Heater heater, Irrigation irrigation) {

        if (env.getTemperature() > Config.MAX_TEMP) {
            fan.on(); heater.off();
        } else if (env.getTemperature() < Config.MIN_TEMP) {
            heater.on(); fan.off();
        } else {
            fan.off(); heater.off();
        }

        if (env.getSoilMoisture() < Config.MIN_SOIL) irrigation.on();
        else irrigation.off();

        if (env.getTemperature() > 40)
            System.out.println("⚠️ ALERT: Extreme Heat!");
    }
}

class EcoStrategy implements ControlStrategy {
    public void apply(Environment env, Fan fan, Heater heater, Irrigation irrigation) {
        if (env.getTemperature() > Config.MAX_TEMP + 2) fan.on();
        else fan.off();
        irrigation.off();
    }
}

// ================= CONTROLLER =================
class GreenhouseController {

    private final Fan fan = new Fan();
    private final Heater heater = new Heater();
    private final Irrigation irrigation = new Irrigation();

    private ControlStrategy strategy = new DefaultStrategy();
    private final ArrayList<Environment> history = FileManager.load();

    public void regulate(Environment env) throws SensorException {

        if (env.getTemperature() < -20 || env.getTemperature() > 60)
            throw new SensorException("Invalid temperature!");

        history.add(env);

        System.out.println("\n--- SYSTEM RESPONSE ---");
        strategy.apply(env, fan, heater, irrigation);

        Logger.log("INFO", env.toString());
    }

    // STATUS
    public void printStatus(Environment env) {
        System.out.println("\n--- STATUS ---");
        System.out.println(env);
        System.out.println("Fan: " + (fan.isRunning() ? "ON" : "OFF"));
        System.out.println("Heater: " + (heater.isRunning() ? "ON" : "OFF"));
        System.out.println("Irrigation: " + (irrigation.isRunning() ? "ON" : "OFF"));
    }

    // STATISTICS
    public void showStatistics() {
        if (history.isEmpty()) return;

        double sum = 0, max = Double.MIN_VALUE, min = Double.MAX_VALUE;

        for (Environment e : history) {
            double t = e.getTemperature();
            sum += t;
            max = Math.max(max, t);
            min = Math.min(min, t);
        }

        System.out.println("\n--- STATISTICS ---");
        System.out.println("Avg Temp: " + sum / history.size());
        System.out.println("Max Temp: " + max);
        System.out.println("Min Temp: " + min);
    }

    // CSV EXPORT
    public void exportCSV() {
        try (PrintWriter pw = new PrintWriter("data.csv")) {
            pw.println("Temperature,Soil,Humidity");

            for (Environment e : history) {
                pw.println(e.getTemperature() + "," +
                        e.getSoilMoisture() + "," +
                        e.getHumidity());
            }

            System.out.println("CSV exported.");
        } catch (Exception e) {
            System.out.println("Export failed.");
        }
    }

    // STRATEGY SWITCH
    public void toggleStrategy() {
        if (strategy instanceof DefaultStrategy) {
            strategy = new EcoStrategy();
            System.out.println("Switched to ECO mode");
        } else {
            strategy = new DefaultStrategy();
            System.out.println("Switched to DEFAULT mode");
        }
    }

    // RECURSION (History)
    public void printHistory(int index) {
        if (index >= history.size()) return;
        System.out.println(history.get(index));
        printHistory(index + 1);
    }

    // OVERLOADING
    public void printHistory() {
        printHistory(0);
    }

    public void saveHistory() {
        FileManager.save(history);
    }
}

// ================= FILE =================
class FileManager {
    public static void save(ArrayList<Environment> history) {
        try (ObjectOutputStream out =
                     new ObjectOutputStream(new FileOutputStream("history.dat"))) {
            out.writeObject(history);
        } catch (Exception e) {
            System.out.println("Save failed.");
        }
    }

    public static ArrayList<Environment> load() {
        try (ObjectInputStream in =
                     new ObjectInputStream(new FileInputStream("history.dat"))) {
            return (ArrayList<Environment>) in.readObject();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}

// ================= LOGGER =================
class Logger {
    public static void log(String level, String msg) {
        try (FileWriter fw = new FileWriter("greenhouse_log.txt", true)) {
            fw.write(LocalDateTime.now() + " [" + level + "] " + msg + "\n");
        } catch (Exception e) {
            System.out.println("Logging failed.");
        }
    }
}

// ================= EXCEPTION =================
class SensorException extends Exception {
    public SensorException(String msg) { super(msg); }
}