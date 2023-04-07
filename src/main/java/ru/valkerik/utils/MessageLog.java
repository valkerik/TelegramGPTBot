package ru.valkerik.utils;

import java.util.ArrayList;
/**
Класс MessageLog расширяет встроенный класс ArrayList в Java и добавляет возможность ограничить максимальное количество элементов, которые он может содержать.
Если превышено максимальное количество элементов, самые старые элементы удаляются из начала списка.
*/
public class MessageLog<E> extends ArrayList<E> {
    private int maxSize;

    public MessageLog(int maxSize) {
        super();
        this.maxSize = maxSize;
    }

    @Override
    public boolean add(E e) {
        boolean added = super.add(e);
        if (size() > maxSize) {
            removeRange(0, size() - maxSize);
        }
        return added;
    }

    @Override
    public boolean addAll(int index, java.util.Collection<? extends E> c) {
        boolean added = super.addAll(index, c);
        if (size() > maxSize) {
            removeRange(0, size() - maxSize);
        }
        return added;
    }

}
