
package lab1;

import java.io.Serializable;

public class RecIntegral implements Serializable {
    private double downly;
    private double upperly;
    private double step;
    private double result;
    
    public RecIntegral(double downly, double upperly, double step, double result) {
        this.downly = downly;
        this.upperly = upperly;
        this.step = step;
        this.result = result;
    }
    
    // Getters and setters
    public double getDownly() { return downly; }
    public double getUpperly() { return upperly; }
    public double getStep() { return step; }
    public double getResult() { return result; }
    
    public void setDownly(double downly) { this.downly = downly; }
    public void setUpperly(double upperly) { this.upperly = upperly; }
    public void setStep(double step) { this.step = step; }
    public void setResult(double result) { this.result = result; }
}
