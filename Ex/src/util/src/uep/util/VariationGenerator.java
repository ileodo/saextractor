/* $Id: VariationGenerator.java 1642 2008-09-12 21:54:15Z labsky $ */
/**
 * Class for generating sequences of elements with different order,
 * with any numbor of elements left out. E.g. for 2 elements (0,1) these
 * sequences are generated: [] [0] [01] [1] [10].
 * @author Martin Labsky labsky@vse.cz
 */
package uep.util;

public class VariationGenerator {

    private int[] path;  // path (end delimited by -1)
    private int[] todo;  // free elements (0|1)
    private int depth;   // current path length
    private int ptr;     // pointer to the next value to be used in this node

    private int total;

    /**
     * Sets number of elements for variations (which is also the maximum length of variations).
     * @param n Number of elements for variations.
     */
    public VariationGenerator(int n) {
        if(n < 1) {
            throw new IllegalArgumentException("Variations must be of at least 1 element");
        }
        path = new int[n];
        todo = new int[n];
        total = 1; // empty variation always there
        int last = 1; // no of prev depth's variations
        for(int i=1;i<=n;i++) {
            last=last*(n-i+1);
            total+=last;
        }
        reset();
    }

    /**
     * Get the total number of variations of the current number of elements.
     * @return Number of variations.
     */
    public int getTotal() {
        return total;
    }

    /**
     * Specifes whether the next call to getNext() will retrieve another variation.
     * @return true if there are more variations.
     */
    public boolean hasMore() {
        return !(depth==0 && ptr==path.length);
    }

    /**
     * Resets the variation sequence of getNext()
     */
    public void reset() {
        for(int i=0; i<path.length;i++) {
            path[i]=-1;
            todo[i]=1;
        }
        depth=0;  // empty variation, tree root, depth -1
        ptr=0;    // first element to be used in the root node
    }

    private boolean tryGoDown() {
        for(int i=ptr;i<todo.length;i++) {
            if(todo[i]==1) {
                todo[i]=0;     // turn off todo this element at this depth (prob. not needed!)
                path[depth]=i; // extend path
                ptr=0;         // start todos from scratch on the next depth level
                depth++;
                //System.out.println("down YES");
                return true;
            }
        }
        //System.out.println("down NO: ptr="+ptr+", todo=["+join(todo)+"]");
        return false;
    }

    private boolean canGoUp() {
        if(depth>0) {
            return true;
        }
        //System.out.println("up NO");
        return false;
    }

    private void goUp() {
        //System.out.println("up YES");
        int temp=path[depth-1];
        path[depth-1]=-1;
        depth--;
        todo[temp]=1;
        ptr=temp+1;
    }

    /**
     * Retrieves another variation.
     * @return variable-length array of integers representing the elements, 
     * or null if there are no more variations.
     */
    public int[] getNext() {
        // walk the imaginary tree of variations in depth-first order 
        if(!tryGoDown()) {
            boolean done=true;
            while(!tryGoDown() && canGoUp()) {
                goUp();
                done=false;
            }
            if(done)
                return null;
        }
        // return int[] of variable length 
        int[] copy=new int[depth];
        System.arraycopy(path,0,copy,0,depth);
        return copy;
    }

    /**
     * For testing purposes. Prints out all variations with the number of elements given as argument.
     */
    public static void main(String[] args) {
        VariationGenerator gen=new VariationGenerator(Integer.parseInt(args[0]));
        System.out.println("Total: "+gen.getTotal());
        int[] arr=gen.getNext();
        while(arr!=null) {
            System.out.println("> "+VariationGenerator.join(arr));
            arr=gen.getNext();
        }
        System.out.println("And again:");
        gen.reset();
        while(gen.hasMore()) {
            arr=gen.getNext();
            System.out.println("> "+VariationGenerator.join(arr));
        }
    }

    public static String join(int[] arr) {
        String s="";
        for(int i=0;i<arr.length;i++) {
            s+=arr[i]+" ";
        }
        return s;
    }
}

