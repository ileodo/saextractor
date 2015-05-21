// $Id: RandomArray.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

/** 
 *  Creates a randomly shuffled array of N integers 
 *  with non-repeating values from 0 to N-1.
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;

public class RandomArray {
    public int[] generate(int n) {
        if(n<=0)
            return null;
        int[] work=new int[n];
        for(int i=0;i<n;i++)
            work[i]=i;
        int[] arr=new int[n];
        int remains=n;
        Random rnd=new Random();
        while(remains>1) {
            int idx=rnd.nextInt(remains); // 0..(remains-1)
            arr[n-remains]=work[idx];
            work[idx]=work[remains-1];
            remains--;
        }
        arr[n-1]=work[0];
        return arr;
    }

    public static void main(String[] args) {
        int n=Integer.parseInt(args[0]);
        RandomArray ra=new RandomArray();
        int[] a=ra.generate(n);
        System.out.println("Shuffled array of "+n+" integers:");
        System.out.print("["+a[0]);
        for(int i=1;i<n;i++)
            System.out.print(", "+a[i]);
        System.out.print("]");
    }
}
