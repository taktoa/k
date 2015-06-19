package org.kframework.backend.func.kst;

public class KSTUtilCounter {
    private int count;

    public KSTUtilCounter() {
        count = 0;
    }

    public void decrement() {
        if(count > 0) {
            count = count - 1;
        }
    }

    public void increment() {
        count = count + 1;
    }

    public int getCount() {
        return count;
    }
}
