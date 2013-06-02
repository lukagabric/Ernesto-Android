package com.example.ernesto;


import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.ToggleButton;


public class MainActivity extends Activity implements SensorEventListener, OnClickListener
{
	private SensorManager mSensorManager;
	private Sensor mAccel;
	private cBluetooth bl = null;

	TextView textViewDebug;
	ToggleButton toggleButton;


	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		textViewDebug = (TextView)findViewById(R.id.textViewDebug);
		toggleButton = (ToggleButton)findViewById(R.id.toggleButton1);
		toggleButton.setOnClickListener(this);
		toggleButton.setChecked(false);

		mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);          

		MyHandler handler = new MyHandler();
		handler.mainActivity = this;

		bl = new cBluetooth(this, handler);
		bl.checkBTState();

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}


	@Override
	protected void onPause()
	{
		super.onPause();
		disconnectBt();
	}


	protected void connectBt()
	{
		boolean connected = bl.BT_Connect("00:13:04:16:90:90", false);
		toggleButton.setChecked(connected);
		mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
	}


	protected void disconnectBt()
	{
		toggleButton.setChecked(false);
		bl.BT_onPause();
		mSensorManager.unregisterListener(this);		
	}


	@Override
	public void onClick(View v)
	{
		if (v == toggleButton)
		{
			boolean connected = toggleButton.isChecked();

			if (connected)
				connectBt();
			else
				disconnectBt();
		}
	}


	@Override
	public void onSensorChanged(SensorEvent e)
	{
		float xAxis = e.values[0] - 5;
		float yAxis = e.values[1];

		if (xAxis >= 0 && xAxis < 1) xAxis = 0;
		else if (xAxis < 0 && xAxis > -1) xAxis = 0;
		else if (xAxis > 1) xAxis -= 1;
		else if (xAxis < -1) xAxis += 1;

		xAxis = Math.min(xAxis, 3);
		xAxis = Math.max(xAxis, -3);

		if (yAxis >= 0 && yAxis < 2) yAxis = 0;
		else if (yAxis < 0 && yAxis > -2) yAxis = 0;
		else if (yAxis > 2) yAxis -= 2;
		else if (yAxis < -2) yAxis += 2;

		yAxis = Math.min(yAxis, 3);
		yAxis = Math.max(yAxis, -3);


		float straightPart = -1*255*xAxis/3;
		float sidePart = 50*yAxis/3;

		Log.d("axis: ", "xAxis = " + xAxis + "; yAxis = " + yAxis + "; straight = " + straightPart + "; side = " + sidePart);

		float pwmLeft = straightPart;
		float pwmRight = straightPart;

		float halfPart = (float)Math.abs(sidePart);

		if (sidePart > 0)
		{
			//adjust speed of right wheel
			if (pwmLeft > 0)
			{
				//moving forward - decrease speed
				pwmRight -= halfPart;
				pwmLeft += halfPart;

				if (pwmLeft > 255)
				{
					float diff = pwmLeft - 255;
					pwmRight -= diff;
					pwmLeft = 255;
				}
			}
			else
			{
				//moving backward - increase speed
				pwmRight += halfPart;
				pwmLeft -= halfPart;

				if (pwmLeft < -255)
				{
					float diff = pwmLeft + 255;
					pwmRight += diff;
					pwmLeft = -255;
				}
			}
		}
		else if (sidePart < 0)
		{
			//adjust speed of left wheel
			if (pwmRight > 0)
			{
				//moving forward - decrease speed
				pwmLeft -= halfPart;
				pwmRight += halfPart;

				if (pwmRight > 255)
				{
					float diff = pwmRight - 255;
					pwmLeft -= diff;
					pwmRight = 255;
				}
			}
			else
			{
				//moving backward - increase speed
				pwmLeft += halfPart;
				pwmRight -= halfPart;

				if (pwmRight < -255)
				{
					float diff = pwmRight + 255;
					pwmLeft += diff;
					pwmRight = -255;
				}
			}
		}

		Log.d("pwm", "pwmLeft: " + pwmLeft + "; pwmRight: " + pwmRight);
		textViewDebug.setText("pwmLeft: " + pwmLeft + "; pwmRight: " + pwmRight);

		String directionL = "";
		String directionR = "";
		String cmdSendL, cmdSendR;

		if (pwmLeft < 0)
		{
			directionL = "-";
		}

		if (pwmRight < 0)
		{
			directionR = "-";
		}

		pwmLeft = Math.abs(pwmLeft);
		pwmRight = Math.abs(pwmRight);

		int pwmLeftInt = Math.round(pwmLeft);
		int pwmRightInt = Math.round(pwmRight);

		cmdSendL = String.valueOf("L" + directionL + pwmLeftInt + "\r");
		cmdSendR = String.valueOf("R" + directionR + pwmRightInt + "\r");

		Log.d("pwm", "pwmLeftInt: " + directionL + pwmLeftInt + "; pwmRightInt: " + directionR + pwmRightInt);

		if (toggleButton.isChecked())
			bl.sendData(cmdSendL + cmdSendR);
	}


	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{

	}


	public void handleMessage(Message msg)
	{
		switch (msg.what)
		{
		case cBluetooth.BL_NOT_AVAILABLE:
			Log.d("Message", "Bluetooth is not available. Exit");
			toggleButton.setChecked(false);
			break;
		case cBluetooth.BL_INCORRECT_ADDRESS:
			Log.d("Message", "Incorrect MAC address");
			toggleButton.setChecked(false);
			break;
		case cBluetooth.BL_REQUEST_ENABLE:   
			Log.d("Message", "Request Bluetooth Enable");
			break;
		case cBluetooth.BL_SOCKET_FAILED:
			Log.d("Message", "BL_SOCKET_FAILED");
			toggleButton.setChecked(false);
			break;
		}
	}


	private static class MyHandler extends Handler
	{
		public MainActivity mainActivity;


		@Override
		public void handleMessage(Message msg)
		{
			mainActivity.handleMessage(msg);
		}
	}
}