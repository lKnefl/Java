
package lab1;


public class RecIntegral {
    private double downly;
    private double step;
    private double upperly;
    private double result;
    
    public RecIntegral(double downly, double upperly, double step, double result){
      this.downly = downly;
      this.upperly = upperly;
      this.step = step;
      this.result = result;
    }
   
    public double getDownly(){
        return downly;
    }
   
     public double getUpperly(){
        return upperly;
    }
     
      public double getStep(){
        return step;
    }
       public double getResult(){
        return result;
    }
       
       public void setResult ( double reault){
           this.result = result;
       }
}
