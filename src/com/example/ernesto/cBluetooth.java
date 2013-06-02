package com.example.ernesto;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;


public class cBluetooth
{
	private static BluetoothAdapter btAdapter = null;
	private BluetoothSocket btSocket = null;
	private OutputStream outStream = null;
	private ConnectedThread mConnectedThread;

	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private final Handler mHandler;
	public final static int BL_NOT_AVAILABLE = 1;
	public final static int BL_INCORRECT_ADDRESS = 2;
	public final static int BL_REQUEST_ENABLE = 3;
	public final static int BL_SOCKET_FAILED = 4;
	public final static int RECIEVE_MESSAGE = 5;

	
	cBluetooth(Context context, Handler handler)
	{
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		mHandler = handler;
		if (btAdapter == null)
		{
			mHandler.sendEmptyMessage(BL_NOT_AVAILABLE);
			return;
		}
	}

	
	public void checkBTState()
	{
		if (btAdapter == null)
		{ 
			mHandler.sendEmptyMessage(BL_NOT_AVAILABLE);
		}
		else
		{
			if (btAdapter.isEnabled())
			{
				Log.d("BT", "Bluetooth ON");
			}
			else
			{
				mHandler.sendEmptyMessage(BL_REQUEST_ENABLE);
			}
		}
	}

	
	private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException
	{
		if (Build.VERSION.SDK_INT >= 10)
		{
			try
			{
				final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
				return (BluetoothSocket) m.invoke(device, MY_UUID);
			}
			catch (Exception e)
			{
				Log.e("BT", "Could not create Insecure RFComm Connection", e);
			}
		}

		return  device.createRfcommSocketToServiceRecord(MY_UUID);
	}

	
	public boolean BT_Connect(String address, boolean listen_InStream)
	{   	
		Log.d("BT", "...On Resume...");  

		boolean connected = false;

		if (!BluetoothAdapter.checkBluetoothAddress(address))
		{
			mHandler.sendEmptyMessage(BL_INCORRECT_ADDRESS);
			return false;
		}
		else
		{
			BluetoothDevice device = btAdapter.getRemoteDevice(address);
			try
			{
				btSocket = createBluetoothSocket(device);
			}
			catch (IOException e1)
			{
				Log.e("BT", "In BT_Connect() socket create failed: " + e1.getMessage());
				mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
				return false;
			}

			btAdapter.cancelDiscovery();

			Log.d("BT", "...Connecting...");
			try
			{
				btSocket.connect();
				Log.d("BT", "...Connection ok...");
			}
			catch (IOException e)
			{
				try
				{
					btSocket.close();
				}
				catch (IOException e2)
				{
					Log.e("BT", "In BT_Connect() unable to close socket during connection failure" + e2.getMessage());
					mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
					return false;
				}
			}

			Log.d("BT", "...Create Socket...");

			try
			{
				outStream = btSocket.getOutputStream();
				connected = true;
			}
			catch (IOException e)
			{
				Log.e("BT", "In BT_Connect() output stream creation failed:" + e.getMessage());
				mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
				return false;
			}
			if (listen_InStream)
			{
				mConnectedThread = new ConnectedThread();
				mConnectedThread.start();
			}
		}
		return connected;
	}

	
	public void BT_onPause()
	{
		Log.d("BT", "...On Pause...");
		if (outStream != null)
		{
			try
			{
				outStream.flush();
			}
			catch (IOException e)
			{
				Log.e("BT", "In onPause() and failed to flush output stream: " + e.getMessage());
				mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
				return;
			}
		}

		if (btSocket != null)
		{
			try
			{
				btSocket.close();
			}
			catch (IOException e2)
			{
				Log.e("BT", "In onPause() and failed to close socket." + e2.getMessage());
				mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
				return;
			}
		}
	}
	
	
	public void sendData(String message)
	{
		byte[] msgBuffer = message.getBytes();

		Log.i("BT", "Send data: " + message);

		if (outStream != null)
		{
			try
			{
				outStream.write(msgBuffer);
			}
			catch (IOException e)
			{
				Log.e("BT", "In onResume() exception occurred during write: " + e.getMessage());
				mHandler.sendEmptyMessage(BL_SOCKET_FAILED);
				return;      
			}
		} 
		else
		{
			Log.e("BT", "Error Send data: outStream is Null");
		}
	}
	
	
	private class ConnectedThread extends Thread
	{
		private final InputStream mmInStream;
		
		
		public ConnectedThread()
		{
			InputStream tmpIn = null;

			try
			{
				tmpIn = btSocket.getInputStream();
			}
			catch (IOException e)
			{
				Log.e("BT", "In ConnectedThread() error getInputStream(): " + e.getMessage());
			}

			mmInStream = tmpIn;
		}
		
		
		public void run()
		{
			byte[] buffer = new byte[256];
			int bytes;

			while (true)
			{
				try
				{
					bytes = mmInStream.read(buffer);
					mHandler.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();
				}
				catch (IOException e)
				{
					break;
				}
			}
		}
	}
}