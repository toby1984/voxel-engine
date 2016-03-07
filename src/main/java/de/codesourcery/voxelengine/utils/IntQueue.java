package de.codesourcery.voxelengine.utils;

/*******************************************************************************
 * Copyright 2015 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

import java.util.NoSuchElementException;

/** A resizable, ordered array of ints with efficient add and remove at the beginning and end. Values in the backing array may
 * wrap back to the beginning, making add and remove at the beginning and end O(1) (unless the backing array needs to resize when
 * adding). 
 * 
 * This code is a stripped-down version of the com.badlogic.gdx.utils.Queue class from libgdx.
 */
public class IntQueue {
    /** Contains the values in the queue. Head and tail indices go in a circle around this array, wrapping at the end. */
    protected int[] values;

    /** Index of first element. Logically smaller than tail. Unless empty, it points to a valid element inside queue. */
    protected int head = 0;

    /** Index of last element. Logically bigger than head. Usually points to an empty position, but points to the head when full
     * (size == values.length). */
    protected int tail = 0;

    /** Number of elements in the queue. */
    public int size = 0;

    /** Creates a new Queue which can hold 16 values without needing to resize backing array. */
    public IntQueue () {
        this(16);
    }

    /** Creates a new Queue which can hold the specified number of values without needing to resize backing array. */
    public IntQueue (int initialSize) {
        // noinspection unchecked
        this.values = new int[initialSize];
    }

    /** Append given object to the tail. (enqueue to tail) Unless backing array needs resizing, operates in O(1) time.
     * @param object can be null */
    public void push (int object) {
        int[] values = this.values;

        if (size == values.length) {
            resize(values.length << 1);// * 2
            values = this.values;
        }

        values[tail++] = object;
        if (tail == values.length) {
            tail = 0;
        }
        size++;
    }

    /** Increases the size of the backing array to accommodate the specified number of additional items. Useful before adding many
     * items to avoid multiple backing array resizes. */
    public void ensureCapacity (int additional) {
        final int needed = size + additional;
        if (values.length < needed) {
            resize(needed);
        }
    }

    /** Resize backing array. newSize must be bigger than current size. */
    protected void resize (int newSize) {
        final int[] values = this.values;
        final int head = this.head;
        final int tail = this.tail;

        final int[] newArray = new int[newSize];
        if (head < tail) {
            // Continuous
            System.arraycopy(values, head, newArray, 0, tail - head);
        } else if (size > 0) {
            // Wrapped
            final int rest = values.length - head;
            System.arraycopy(values, head, newArray, 0, rest);
            System.arraycopy(values, 0, newArray, rest, tail);
        }
        this.values = newArray;
        this.head = 0;
        this.tail = size;
    }

    /** Remove the first item from the queue. (dequeue from head) Always O(1).
     * @return removed object
     * @throws NoSuchElementException when queue is empty */
    public int pop () {
        if (size == 0) {
            // Underflow
            throw new NoSuchElementException("Queue is empty.");
        }

        final int[] values = this.values;

        final int result = values[head];
        head++;
        if (head == values.length) {
            head = 0;
        }
        size--;
        return result;
    }

    /** Returns the last (tail) item in the queue (without removing it).
     * @see #addLast(Object)
     * @see #removeLast()
     * @throws NoSuchElementException when queue is empty */
    public int peek () {
        if (size == 0) {
            // Underflow
            throw new NoSuchElementException("Queue is empty.");
        }
        final int[] values = this.values;
        int tail = this.tail;
        tail--;
        if (tail == -1) {
            tail = values.length - 1;
        }
        return values[tail];
    }
    
    public boolean isEmpty() {
        return size==0;
    }
    
    public boolean isNotEmpty() {
        return size!=0;
    }

    /** Removes all values from this queue. Values in backing array are set to null to prevent memory leak, so this operates in
     * O(n). */
    public void clear () 
    {
        this.head = 0;
        this.tail = 0;
        this.size = 0;
    }
}