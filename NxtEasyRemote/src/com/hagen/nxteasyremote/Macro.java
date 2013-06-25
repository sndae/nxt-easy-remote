package com.hagen.nxteasyremote;

import java.util.ArrayList;
import java.util.List;

public class Macro {

	ArrayList<Float> lefts;
	ArrayList<Float> rights;
	String name;
	
	Macro(String mName){
		name = mName;
		lefts = new ArrayList<Float>();
		rights = new ArrayList<Float>();
	}
	
	public void add(float left, float right){
		lefts.add(left);
		rights.add(right);
	}
	
	public String[] toLine(){
		List<String> values = new ArrayList<String>();
        values.add(name);
		
        for(int i = 0; i < lefts.size(); i++) {
        	values.add(lefts.get(i).toString());
        	values.add(rights.get(i).toString());
        }
        return (String[]) values.toArray(new String[0]);
	}
}
