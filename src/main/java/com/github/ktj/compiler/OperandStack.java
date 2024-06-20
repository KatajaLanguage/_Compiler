package com.github.ktj.compiler;

import com.github.ktj.lang.KtjMethod;
import javassist.bytecode.Bytecode;

import java.util.ArrayList;

final class OperandStack {

    private static class MatchedList<Key, Value> {

        private final ArrayList<Key> keyList;
        private final ArrayList<Value> valueList;

        public MatchedList(){
            keyList = new ArrayList<>();
            valueList = new ArrayList<>();
        }

        public void add(Key key, Value value){
            keyList.add(key);
            valueList.add(value);
        }

        public Key getKey(Value value){
            if(!valueList.contains(value))
                return null;
            return keyList.get(valueList.indexOf(value));
        }

        public Key getKey(int n){
            if(n < 0 || n >= size())
                return null;
            return keyList.get(n);
        }

        public boolean hasKey(Key key){
            return keyList.contains(key);
        }

        public Value getValue(Key key){
            if(!keyList.contains(key))
                return null;
            return valueList.get(keyList.indexOf(key));
        }

        public Value getValue(int n){
            if(n < 0 || n >= size())
                return null;
            return valueList.get(n);
        }

        public boolean hasValue(Value value){
            return valueList.contains(value);
        }

        public ArrayList<Key> getKeyList(){
            return keyList;
        }

        public ArrayList<Value> getValueList(){
            return valueList;
        }

        public void remove(int n){
            if(keyList.size() >= n && n >= 0){
                keyList.remove(n);
                valueList.remove(n);
            }
        }

        public int size(){
            return keyList.size();
        }

        public static <First, Second> MatchedList<First, Second> of(First[] firsts, Second[] seconds) throws RuntimeException{
            assert firsts != null;
            assert seconds != null;
            assert firsts.length == seconds.length;

            MatchedList<First, Second> matchedList = new MatchedList<>();

            for(int i = 0;i < firsts.length;i++)
                matchedList.add(firsts[i], seconds[i]);

            return matchedList;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{ ");

            for(int i = 0;i < keyList.size();i++)
                sb.append("(").append(keyList.get(i)).append(",").append(valueList.get(i)).append("), ");

            return sb.deleteCharAt(sb.length() - 2).append("}").toString();
        }
    }

    private final MatchedList<String, Integer> stack;
    private final ArrayList<Integer> scopes = new ArrayList<>();
    private int size;

    OperandStack(boolean statik){
        stack = new MatchedList<>();
        size = 0;

        if(!statik) push("this", 1);
    }

    int push(String name, int length){
        stack.add(name, size);
        //System.out.println(name + " " + size);
        size += length;
        //System.out.println(stack);
        return size - length;
    }

    void push(int length){
        push("&temp", length);
    }

    String pop(){
        String temp = stack.getKey(stack.size() - 1);
        int length = stack.getValue(stack.size() - 1);
        if(stack.size() > 1) length -= stack.getValue(stack.size() - 2);
        else length = 0;
        stack.remove(stack.size() - 1);
        size -= length;
        //System.out.println(stack);
        return temp;
    }

    void clearScope(Bytecode code){
        while (size > scopes.get(scopes.size() - 1)) {
            //code.addOpcode(Opcode.POP);
            pop();
        }
    }

    void newScope(){
        scopes.add(size);
    }

    int clearTemp(){
        int i = 0;
        while (stack.getKey(stack.size()).equals("&temp")){
            pop();
            i++;
        }
        return i;
    }

    int get(String name){
        if(!stack.hasKey(name))
            return -1;

        return stack.getValue(name);
    }

    static OperandStack forMethod(KtjMethod method){
        OperandStack os = new OperandStack(method.modifier.statik);

        for(int i = 0;i < method.parameter.length;i++)
            os.push(method.parameter[i].name, (method.parameter[i].type.equals("double") || method.parameter[i].type.equals("long")) ? 2 : 1);

        return os;
    }

    boolean contains(String name){
        return stack.hasKey(name);
    }
}