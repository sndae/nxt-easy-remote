package com.hagen.nxteasyremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.view.View;

public class ControlPositionView extends View{

	private int x;
	private int y;
	private int xL;
	private int xR;
	private Paint fieldPaint;
	private Paint cursorPaint;
	private float centerX;
	private float centerY;
	private int onOffButtonHeight;
	private boolean debug;
	
	public ControlPositionView(Context context, int mOnOffButtonHeight) {
		super(context);
		
		onOffButtonHeight = mOnOffButtonHeight;
		
		fieldPaint = new Paint();
		fieldPaint.setColor(Color.GREEN);
		fieldPaint.setStyle(Style.STROKE);
		fieldPaint.setStrokeWidth(3);
		
		cursorPaint = new Paint();
		cursorPaint.setColor(Color.DKGRAY);
		cursorPaint.setStyle(Style.FILL);
		cursorPaint.setTextSize(25);
		update((int)centerX, (int)centerY, 0, 0, false);
	}
	
	//Sets the new coordinates of the sensor's point and triggers the drawing
	public void update(int mX, int mY, int mXL, int mXR, boolean mDebug) {
		x = mX;
		y = mY;
		xL = mXL;
		xR = mXR;
		debug = mDebug;
		this.invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		centerX = this.getWidth()/2;
		centerY = this.getHeight()/2 + onOffButtonHeight;
		
		canvas.drawCircle(centerX, centerY, centerX-5, fieldPaint);
		canvas.drawCircle(centerX, centerY, centerX/3*2-5, fieldPaint);
		canvas.drawCircle(centerX, centerY, centerX/3-5, fieldPaint);
		
		float cx = centerX - ((centerX-20)/100*y);
		float cy = centerY - ((centerY-20)/100*x);
		canvas.drawCircle(cx, cy, 20, cursorPaint);
		
		if(debug){
			canvas.drawText("X: "+x, centerX-20, centerY-17, cursorPaint);
			canvas.drawText("Y: "+y, centerX-20, centerY+0, cursorPaint);
			canvas.drawText("L: "+xL, centerX-20, centerY+17, cursorPaint);
			canvas.drawText("R: "+xR, centerX-20, centerY+34, cursorPaint);
		}
	}

}
