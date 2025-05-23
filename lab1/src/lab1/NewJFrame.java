
package lab1;
import javax.swing.*;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import java.util.LinkedList;
import java.util.stream.Stream;
import java.util.*;
import java.io.*;
import javax.swing.JFileChooser;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;


public class NewJFrame extends javax.swing.JFrame {

     private LinkedList<RecIntegral> listR = new LinkedList<>();
    private ServerSocket serverSocket;
    private volatile boolean isServerRunning = false;
    private ExecutorService clientHandlerPool = Executors.newCachedThreadPool();
    private List<Socket> clients = Collections.synchronizedList(new ArrayList<>());
    private Queue<Task> taskQueue = new LinkedList<>();
    private List<Double> results = Collections.synchronizedList(new ArrayList<>());
    private volatile int totalTasks = 0;

    static class Task implements Serializable {
        final double a;
        final double b;
        final double step;

        Task(double a, double b, double step) {
            this.a = a;
            this.b = b;
            this.step = step;
        }

        @Override
        public String toString() {
            return String.format("Task[a=%.2f, b=%.2f, step=%.5f]", a, b, step);
        }
    }

    public NewJFrame() {
        initComponents();
        startServer(12345);
    }

    private void startServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            isServerRunning = true;
            System.out.println("Server started on port " + port);
            
            new Thread(() -> {
                while (isServerRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("New client connected: " + clientSocket.getInetAddress());
                        clients.add(clientSocket);
                        clientHandlerPool.execute(() -> handleClient(clientSocket));
                    } catch (IOException e) {
                        if (!isServerRunning) {
                            System.out.println("Server shutdown complete");
                            break;
                        }
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }).start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Server startup error: " + e.getMessage());
        }
    }

    private void stopServer() {
        isServerRunning = false;
        try {
            System.out.println("Shutting down server...");
            serverSocket.close();
            clientHandlerPool.shutdown();
            for (Socket client : clients) {
                client.close();
            }
            clients.clear();
            System.out.println("Server stopped successfully");
        } catch (IOException e) {
            System.err.println("Error during server shutdown: " + e.getMessage());
        }
    }
    
    
private final Map<Socket, Integer> clientTaskCount = new ConcurrentHashMap<>();

private void handleClient(Socket clientSocket) {
    try {
        ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream());
        ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream());
        clientTaskCount.put(clientSocket, 0);
        System.out.println("New client connected. Total clients: " + clients.size());

        while (isServerRunning && !clientSocket.isClosed()) {
            try {
                Task task = getTaskForClient(clientSocket);
                
                if (task != null) {
                    System.out.printf("Sending task %s to client %d%n", 
                        task, clients.indexOf(clientSocket));
                    
                    oos.writeObject(task);
                    oos.flush();
                    oos.reset();
                    clientTaskCount.merge(clientSocket, 1, Integer::sum);

                    Object response = ois.readObject();
                    if (response instanceof Double) {
                        synchronized (results) {
                            results.add((Double) response);
                            if (results.size() == totalTasks) {
                                calculateFinalResult();
                            }
                        }
                    }
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        System.out.println("Handler thread interrupted");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (ClassNotFoundException e) {
                System.err.println("Protocol error with client: " + e.getMessage());
                break;
            } catch (EOFException e) {
                System.out.println("Client disconnected normally");
                break;
            } catch (IOException e) {
                System.err.println("Communication error with client: " + e.getMessage());
                break;
            }
        }
    } catch (IOException e) {
        System.err.println("Error creating streams for client: " + e.getMessage());
    } finally {
        cleanupClient(clientSocket);
    }
}

private synchronized Task getTaskForClient(Socket clientSocket) {
    // Выбираем клиента с наименьшим количеством задач
    Socket leastBusyClient = clientTaskCount.entrySet().stream()
        .min(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(null);

    if (leastBusyClient != null && leastBusyClient.equals(clientSocket)) {
        return taskQueue.poll();
    }
    return null;
}

private void calculateFinalResult() {
    double total = results.stream().mapToDouble(d -> d).sum();
    System.out.println("\n=== Final Result ===");
    System.out.println("Total result: " + total);
    System.out.println("Tasks distribution:");
    clientTaskCount.forEach((client, count) -> 
        System.out.println("Client " + clients.indexOf(client) + ": " + count + " tasks"));
    
    SwingUtilities.invokeLater(() -> updateResult(total));
}

private void cleanupClient(Socket clientSocket) {
    clients.remove(clientSocket);
    clientTaskCount.remove(clientSocket);
    try {
        if (!clientSocket.isClosed()) {
            clientSocket.close();
            System.out.println("Client disconnected. Remaining clients: " + clients.size());
        }
    } catch (IOException e) {
        System.err.println("Error closing client socket: " + e.getMessage());
    }
}

    private void updateResult(double total) {
        int row = jTable1.getSelectedRow();
        if (row != -1) {
            DefaultTableModel model = (DefaultTableModel) jTable1.getModel();
            model.setValueAt(total, row, 3);
            RecIntegral rec = listR.get(row);
            listR.set(row, new RecIntegral(rec.getDownly(), rec.getUpperly(), rec.getStep(), total));
            System.out.println("GUI updated with new result");
        }
    }
    

  
    // Вложенный класс для многопоточного вычисления части интеграла
    class IntegralThread extends Thread {
        private double a;       // Начало интервала
        private double b;       // Конец интервала
        private double step;    // Шаг
        private double result;  // Результат вычисления
        private long time;     // Время выполнения
        
        public IntegralThread(double a, double b, double step) {
            this.a = a;
            this.b = b;
            this.step = step;
        }
        
        public double getResult() {
            return result;
        }
        
        public long getTime() {
            return time;
        }
        
        @Override
        public void run() {
            long startTime = System.nanoTime();
            result = partialCalc(a, b, step);
            time = System.nanoTime() - startTime;
        }
        
        private double partialCalc(double a, double b, double step) {
            double start, h, sumS = 0;
            start = a;
            do {
                h = Math.min(step, (b - start));
                sumS += h * (Math.tan(start) + Math.tan(start + h)) / 2;
                start += h;
            } while (start < b);
            return sumS;
        }
    }

    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        Downly = new javax.swing.JTextPane();
        jScrollPane2 = new javax.swing.JScrollPane();
        Upperly = new javax.swing.JTextPane();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        Step = new javax.swing.JTextPane();
        jLabel3 = new javax.swing.JLabel();
        AddButton = new javax.swing.JButton();
        Calculate = new javax.swing.JButton();
        Delete = new javax.swing.JButton();
        jScrollPane4 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        Clear_Button = new javax.swing.JButton();
        Fill_button = new javax.swing.JButton();
        SaveFile = new javax.swing.JButton();
        ReadFile = new javax.swing.JButton();
        SaveFileDouble = new javax.swing.JButton();
        ReadFileDouble = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("integral of tg"));

        jLabel1.setText("Нижняя граница (а)");

        jScrollPane1.setViewportView(Downly);

        jScrollPane2.setViewportView(Upperly);

        jLabel2.setText("Верхняя граница (b)");

        jScrollPane3.setViewportView(Step);

        jLabel3.setText("          Шаг");

        AddButton.setText("Добавить");
        AddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddButtonActionPerformed(evt);
            }
        });

        Calculate.setText("Расчитать");
        Calculate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                CalculateActionPerformed(evt);
            }
        });

        Delete.setText("Удалить");
        Delete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DeleteActionPerformed(evt);
            }
        });

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Нижняя граница", "Верхняя граница", "Шаг", "Результат"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                true, true, true, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane4.setViewportView(jTable1);

        Clear_Button.setText("Очистить");
        Clear_Button.setToolTipText("");
        Clear_Button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Clear_ButtonActionPerformed(evt);
            }
        });

        Fill_button.setText("Заполнить");
        Fill_button.setToolTipText("");
        Fill_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                Fill_buttonActionPerformed(evt);
            }
        });

        SaveFile.setText("Сохранить в файл");
        SaveFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveFileActionPerformed(evt);
            }
        });

        ReadFile.setText("Загрузить из файла");
        ReadFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReadFileActionPerformed(evt);
            }
        });

        SaveFileDouble.setText("Сохранить в двоичном коде");
        SaveFileDouble.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveFileDoubleActionPerformed(evt);
            }
        });

        ReadFileDouble.setText("Загрузить (двоичный код)");
        ReadFileDouble.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ReadFileDoubleActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(jLabel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(ReadFile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(SaveFile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(SaveFileDouble)
                    .addComponent(ReadFileDouble))
                .addGap(18, 18, 18)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(Calculate, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(Fill_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(Clear_Button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(Delete, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(AddButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
            .addComponent(jScrollPane4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 533, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(AddButton)
                    .addComponent(SaveFile))
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(3, 3, 3)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(13, 13, 13)
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(7, 7, 7)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(Delete)
                            .addComponent(ReadFile))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(Calculate)
                            .addComponent(SaveFileDouble))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(Clear_Button)
                            .addComponent(ReadFileDouble))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(Fill_button)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 58, Short.MAX_VALUE)
                .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 181, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
        // Создаем собственное исключение
    class CustomException extends Exception {
    public CustomException(String message) {
        super(message);
    }}
    
    
    private void CalculateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_CalculateActionPerformed
        int row = jTable1.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select a row!");
            return;
        }

        RecIntegral rec = listR.get(row);
        double a = rec.getDownly();
        double b = rec.getUpperly();
        double step = rec.getStep();
        int numTasks = 10; // Количество подзадач

        synchronized (taskQueue) {
            taskQueue.clear();
            results.clear();
            double interval = (b - a) / numTasks;
            for (int i = 0; i < numTasks; i++) {
                double start = a + i * interval;
                double end = (i == numTasks - 1) ? b : start + interval;
                taskQueue.add(new Task(start, end, step));
            }
            totalTasks = numTasks;
        }

        new Thread(() -> {
            while (results.size() < totalTasks) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        
        // TODO add your handling code here:
    }//GEN-LAST:event_CalculateActionPerformed
public double Calc(double downly, double upperly, double step){
    long totalStartTime = System.nanoTime();
        
        // Разделяем интервал на 4 части
        double interval = (upperly - downly) / 4;
        
        // Создаем и запускаем потоки
        IntegralThread thread1 = new IntegralThread(downly, downly + interval, step);
        IntegralThread thread2 = new IntegralThread(downly + interval, downly + 2*interval, step);
        IntegralThread thread3 = new IntegralThread(downly + 2*interval, downly + 3*interval, step);
        IntegralThread thread4 = new IntegralThread(downly + 3*interval, upperly, step);
        
        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
        
        try {
            // Ждем завершения всех потоков
            thread1.join();
            thread2.join();
            thread3.join();
            thread4.join();
        } catch (InterruptedException e) {
            JOptionPane.showMessageDialog(null, "Ошибка при вычислении: " + e.getMessage());
            return 0;
        }
        
        // Суммируем результаты всех потоков
        double totalResult = thread1.getResult() + thread2.getResult() + 
                           thread3.getResult() + thread4.getResult();
        
        long totalDuration = System.nanoTime() - totalStartTime;
        System.out.println("Time: " + totalDuration + " ns (" + 
                         (totalDuration / 1_000_000.0) + " ms)");
        
        // Выводим время выполнения каждого потока
        System.out.println("Time Thread:");
        System.out.println("Thread 1: " + thread1.getTime() + " ns");
        System.out.println("Thread 2: " + thread2.getTime() + " ns");
        System.out.println("Thread 3: " + thread3.getTime() + " ns");
        System.out.println("Thread 4: " + thread4.getTime() + " ns");
        
        return totalResult;
}
    private void AddButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddButtonActionPerformed
        double upperly=0;
        double downly=0;
        double step=0;// TODO add your handling code here:
        DefaultTableModel model1 = (DefaultTableModel) jTable1.getModel ();
                try {
                upperly = Double.parseDouble(Upperly.getText());
                downly = Double.parseDouble(Downly.getText());
                step = Double.parseDouble(Step.getText());
                

                if (downly < 0.000001 || downly > 1000000) {
                    throw new CustomException("Значение должно быть в диапазоне от 0,000001 до 1000000");
                }
                if (upperly < 0.000001 || upperly > 1000000) {
                    throw new CustomException("Значение должно быть в диапазоне от 0,000001 до 1000000");
                }
                if (step < 0.000001 || step > 1000000) {
                    throw new CustomException("Значение должно быть в диапазоне от 0,000001 до 1000000");
                }
                if (upperly <= downly ) {
                    throw new CustomException("Нижняя граница не может быть больше или равна верхней");
                }
                
            } 
            catch (CustomException e) {
                Downly.setText ("");
                Upperly.setText ("");
                Step.setText ("");
                JOptionPane.showMessageDialog(null, "Ошибка: " + e.getMessage());
                return;
                
            }
            Downly.setText ("");
            Upperly.setText ("");
            Step.setText ("");
            model1.addRow(new Object []{downly, upperly, step});
            listR.add (new RecIntegral (downly, upperly, step, 0));
    }//GEN-LAST:event_AddButtonActionPerformed

    private void DeleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_DeleteActionPerformed
        int selectRow = jTable1.getSelectedRow();
        if (selectRow !=-1){
            DefaultTableModel model1 = (DefaultTableModel) jTable1.getModel ();
            model1.removeRow (selectRow);// TODO add your handling code here:
            listR.remove(selectRow);
        }
        else{
            JOptionPane.showMessageDialog(null, "выберете строку");
        }
        
    }//GEN-LAST:event_DeleteActionPerformed

    private void Clear_ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Clear_ButtonActionPerformed
       DefaultTableModel model1 = (DefaultTableModel) jTable1.getModel ();
       model1.setRowCount(0);
    }//GEN-LAST:event_Clear_ButtonActionPerformed

    private void Fill_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_Fill_buttonActionPerformed
       DefaultTableModel model1 = (DefaultTableModel) jTable1.getModel (); 
       model1.setRowCount(0);
       Stream<RecIntegral> lStream = listR.stream();
       lStream.forEach(s->model1.addRow(new Object []{s.getDownly(), s.getUpperly(), s.getStep(), s.getResult()}));
       
    }//GEN-LAST:event_Fill_buttonActionPerformed

    
    private void SaveFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SaveFileActionPerformed
        // TODO add your handling code here:
            JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Сохранить как текстовый файл");
    
    // Устанавливаем фильтр для .txt файлов
    fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
        @Override
        public boolean accept(File f) {
            return f.getName().toLowerCase().endsWith(".txt") || f.isDirectory();
        }

        @Override
        public String getDescription() {
            return "Text Files (*.txt)";
        }
    });
    
    // Устанавливаем расширение по умолчанию
    fileChooser.setSelectedFile(new File("integral_data.txt"));
    
    int userSelection = fileChooser.showSaveDialog(this);
    
    if (userSelection == JFileChooser.APPROVE_OPTION) {
        File fileToSave = fileChooser.getSelectedFile();
        
        // Добавляем расширение .txt, если его нет
        if (!fileToSave.getName().toLowerCase().endsWith(".txt")) {
            fileToSave = new File(fileToSave.getAbsolutePath() + ".txt");
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileToSave))) {
            for (RecIntegral rec : listR) {
                writer.println(rec.getDownly() + "," + 
                              rec.getUpperly() + "," + 
                              rec.getStep() + "," + 
                              rec.getResult());
            }
            JOptionPane.showMessageDialog(this, "Данные успешно сохранены");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка сохранения данных: " + e.getMessage());
        }
    }
       
    }//GEN-LAST:event_SaveFileActionPerformed

    private void ReadFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ReadFileActionPerformed
        // TODO add your handling code here:
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Открыть текстовый файл");
    
    // Устанавливаем фильтр для .txt файлов
    fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
        @Override
        public boolean accept(File f) {
            return f.getName().toLowerCase().endsWith(".txt") || f.isDirectory();
        }

        @Override
        public String getDescription() {
            return "Text Files (*.txt)";
        }
    });
    
    int userSelection = fileChooser.showOpenDialog(this);
    
    if (userSelection == JFileChooser.APPROVE_OPTION) {
        File fileToOpen = fileChooser.getSelectedFile();
        listR.clear();
        DefaultTableModel model = (DefaultTableModel) jTable1.getModel();
        model.setRowCount(0);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(fileToOpen))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 4) {
                    double downly = Double.parseDouble(parts[0]);
                    double upperly = Double.parseDouble(parts[1]);
                    double step = Double.parseDouble(parts[2]);
                    double result = Double.parseDouble(parts[3]);
                    
                    listR.add(new RecIntegral(downly, upperly, step, result));
                    model.addRow(new Object[]{downly, upperly, step, result});
                }
            }
            JOptionPane.showMessageDialog(this, "Данные успешно прочитаны");
        } catch (IOException | NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Ошибка чтения данных: " + e.getMessage());
        }
    }
    }//GEN-LAST:event_ReadFileActionPerformed

    private void SaveFileDoubleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SaveFileDoubleActionPerformed
        // TODO add your handling code here:
        JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Сохранить как бинарный файл");
    
    // Устанавливаем фильтр для .bin файлов
    fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
        @Override
        public boolean accept(File f) {
            return f.getName().toLowerCase().endsWith(".bin") || f.isDirectory();
        }

        @Override
        public String getDescription() {
            return "Binary Files (*.bin)";
        }
    });
    
    // Устанавливаем расширение по умолчанию
    fileChooser.setSelectedFile(new File("integral_data.bin"));
    
    int userSelection = fileChooser.showSaveDialog(this);
    
    if (userSelection == JFileChooser.APPROVE_OPTION) {
        File fileToSave = fileChooser.getSelectedFile();
        
        // Добавляем расширение .bin, если его нет
        if (!fileToSave.getName().toLowerCase().endsWith(".bin")) {
            fileToSave = new File(fileToSave.getAbsolutePath() + ".bin");
        }
        
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fileToSave))) {
            oos.writeObject(listR);
            JOptionPane.showMessageDialog(this, "Данные успешно сохранены в бинарный файл");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка сохранения данных: " + e.getMessage());
        }
    }
    }//GEN-LAST:event_SaveFileDoubleActionPerformed

    private void ReadFileDoubleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ReadFileDoubleActionPerformed
        // TODO add your handling code here:
        JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Открыть бинарный файл");
    
    // Устанавливаем фильтр для .bin файлов
    fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
        @Override
        public boolean accept(File f) {
            return f.getName().toLowerCase().endsWith(".bin") || f.isDirectory();
        }

        @Override
        public String getDescription() {
            return "Binary Files (*.bin)";
        }
    });
    
    int userSelection = fileChooser.showOpenDialog(this);
    
    if (userSelection == JFileChooser.APPROVE_OPTION) {
        File fileToOpen = fileChooser.getSelectedFile();
        DefaultTableModel model = (DefaultTableModel) jTable1.getModel();
        model.setRowCount(0);
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileToOpen))) {
            listR = (LinkedList<RecIntegral>) ois.readObject();
            for (RecIntegral rec : listR) {
                model.addRow(new Object[]{
                    rec.getDownly(), 
                    rec.getUpperly(), 
                    rec.getStep(), 
                    rec.getResult()
                });
            }
            JOptionPane.showMessageDialog(this, "Данные успешно прочитаны");
        } catch (IOException | ClassNotFoundException e) {
            JOptionPane.showMessageDialog(this, "Ошибка чтения данных: " + e.getMessage());
        }
    }
    }//GEN-LAST:event_ReadFileDoubleActionPerformed
    
    
    public static void main(String args[]) {
      
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new NewJFrame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton AddButton;
    private javax.swing.JButton Calculate;
    private javax.swing.JButton Clear_Button;
    private javax.swing.JButton Delete;
    private javax.swing.JTextPane Downly;
    private javax.swing.JButton Fill_button;
    private javax.swing.JButton ReadFile;
    private javax.swing.JButton ReadFileDouble;
    private javax.swing.JButton SaveFile;
    private javax.swing.JButton SaveFileDouble;
    private javax.swing.JTextPane Step;
    private javax.swing.JTextPane Upperly;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JTable jTable1;
    // End of variables declaration//GEN-END:variables
}
