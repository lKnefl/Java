package lab1;

import java.io.*;
import java.net.Socket;

public class IntegralClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final int RECONNECT_DELAY = 3000;
    private static final int MAX_RETRIES = 3;
    private static boolean active = true;
    
    // Статистика
    private static long totalCalculationTime = 0;
    private static int tasksProcessed = 0;

    public static void main(String[] args) {
        System.out.println("Starting integral calculation client...");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            active = false;
            printStatistics();
            System.out.println("Shutting down client...");
        }));

        int retryCount = 0;
        while (active && retryCount < MAX_RETRIES) {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT)) {
                retryCount = 0;
                System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT);
                handleConnection(socket);
            } catch (IOException e) {
                retryCount++;
                if (retryCount < MAX_RETRIES) {
                    System.err.println("Connection failed (attempt " + retryCount + " of " + MAX_RETRIES + "): " + e.getMessage());
                    sleepBeforeRetry();
                } else {
                    printStatistics();
                    System.err.println("Max connection attempts reached. Exiting...");
                }
            }
        }
    }

    private static void printStatistics() {
        System.out.println("\n=== Client Statistics ===");
        System.out.println("Total tasks processed: " + tasksProcessed);
        System.out.println("Total calculation time: " + (totalCalculationTime / 1000_000.0) + " ms");
        if (tasksProcessed > 0) {
            System.out.println("Average time per task: " + (totalCalculationTime / tasksProcessed / 1000_000.0) + " ms");
        }
    }

    private static void handleConnection(Socket socket) {
        try (ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
            
            System.out.println("Ready to process tasks");

            while (active && !socket.isClosed()) {
                try {
                    Object task = ois.readObject();
                    if (task instanceof NewJFrame.Task) {
                        processTask((NewJFrame.Task) task, oos);
                    }
                } catch (ClassNotFoundException e) {
                    System.err.println("Invalid task format: " + e.getMessage());
                    break;
                } catch (EOFException e) {
                    System.out.println("Server closed connection");
                    break;
                } catch (IOException e) {
                    System.err.println("Communication error: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error establishing communication: " + e.getMessage());
        } finally {
            System.out.println("Disconnected from server");
        }
    }

    private static void processTask(NewJFrame.Task task, ObjectOutputStream oos) throws IOException {
        System.out.println("Processing task: " + task);
        
        long startTime = System.nanoTime();
        double result = calculateIntegral(task.a, task.b, task.step);
        long endTime = System.nanoTime();
        
        long duration = endTime - startTime;
        totalCalculationTime += duration;
        tasksProcessed++;
        
        System.out.println(String.format(
            "Calculation completed in %.3f ms | Result: %.5f",
            duration / 1000_000.0, result));
        
        oos.writeObject(result);
        oos.flush();
        oos.reset();
    }

    private static void sleepBeforeRetry() {
        try {
            System.out.println("Will retry in " + (RECONNECT_DELAY/1000) + " seconds...");
            Thread.sleep(RECONNECT_DELAY);
        } catch (InterruptedException e) {
            System.err.println("Retry interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private static double calculateIntegral(double a, double b, double step) {
        int numThreads = 4;
        double[] partialResults = new double[numThreads];
        Thread[] threads = new Thread[numThreads];
        double interval = (b - a) / numThreads;

        for (int i = 0; i < numThreads; i++) {
            final double start = a + i * interval;
            final double end = (i == numThreads - 1) ? b : start + interval;
            final int threadIdx = i;
            
            threads[i] = new Thread(() -> {
                partialResults[threadIdx] = partialCalc(start, end, step);
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.err.println("Thread interrupted during calculation");
                Thread.currentThread().interrupt();
            }
        }

        double total = 0;
        for (double res : partialResults) {
            total += res;
        }
        return total;
    }

    private static double partialCalc(double a, double b, double step) {
        double sum = 0;
        double current = a;
        while (current < b) {
            double h = Math.min(step, b - current);
            sum += h * (Math.tan(current) + Math.tan(current + h)) / 2;
            current += h;
        }
        return sum;
    }
}