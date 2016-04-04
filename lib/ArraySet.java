import java.util.ArrayList;  
import java.util.HashSet;  
  
public class ArraySet<E> extends HashSet<E> {  
  
    private static final long serialVersionUID = -7548294595221509577L;  
    private ArrayList<E> list = new ArrayList<E>();  
  
    /** 
     * @param index 
     * @return 
     * @see java.util.ArrayList#get(int) 
     */  
    public E get(int index) {  
        return list.get(index);  
    }  
  
    @Override  
    public boolean add(E o) {  
        if (super.add(o)) {  
            list.add(o);  
            return true;  
        } else  
            return false;  
    }  
  
    @Override  
    public boolean remove(Object o) {  
        if (super.remove(o)) {  
            list.remove(o);  
            return true;  
        } else  
            return false;  
    }  
  
    @Override  
    public void clear() {  
        super.clear();  
        list.clear();  
    }  
}  
