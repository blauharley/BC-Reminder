package com.phonegap.reminder;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;

import java.util.Calendar;
import java.text.SimpleDateFormat;

public class ReminderService extends Service implements NotificationInterface{

	private final static String name = "ReminderService";

	private final static String AIM_MODE = "aim";
	private final static String TRACK_MODE = "track";
	private final static String STATUS_MODE = "status";

	private int startServiceId;
	
	private Location startLoc;
	private Location lastloc;
	// keep it set to null
	private LocationManager locationManager = null;

	private String title;
	private String content;
	private float distance;
	private long interval;
	private boolean whistle;
	private String stopDate;
	private float distanceTolerance;
	private String mode;
	private Location locAim;
	private boolean aggressive;
	
	private float radiusDistance;
	private float linearDistance;
	private Integer desiredAccuracy = 0;
	private long currentMsTime;
	private int stopServiceDate = -1;

	private Handler serviceGPSHandler = null;
	private Handler serviceNetworkHandler = null;
	
	private boolean locSubscribed = false;

	private boolean goToHold = false;
    
	// wait at the beginning
	private long startTime;
	private long warmUpTime = 5000;
	
	private int locationRequestTimeout = 1000*60;
	
	private LocationListener locationListenerGPS;
	private LocationListener locationListenerNetwork;
	private LocationListener locationListenerPassive;
	private boolean listenerMutex = false;
	
	private Location currentTakenLoc = null;
	private boolean startLocationTaken = false;
	
	private Handler mUserLocationHandler = null;
	private Thread triggerService = null;

	class gpsTimer implements Runnable {
          public void run() {
            
            serviceGPSHandler.postDelayed( new gpsTimer(),interval+locationRequestTimeout);
            
            attachToGPSLocationUpdates(false);
            
          }
    }
    
    class networkTimer implements Runnable {
          public void run() {
            
            serviceNetworkHandler.postDelayed( new networkTimer(),interval+locationRequestTimeout);
            
            attachToNetworkLocationUpdates(false);
            
          }
    }
    
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		startServiceId = startId;
		
		title = intent.getExtras().getString("title");
		content = intent.getExtras().getString("content");
		distance = intent.getExtras().getFloat("distance");
		interval = intent.getExtras().getLong("interval");
		whistle = intent.getExtras().getBoolean("whistle");
		stopDate = intent.getExtras().getString("stopDate");
		distanceTolerance = intent.getExtras().getFloat("distanceTolerance");
		mode = intent.getExtras().getString("mode");
		aggressive = intent.getExtras().getBoolean("aggressive");
		
		if(STOP_SERVICE_DATE_TOMORROW.equalsIgnoreCase(stopDate)){
			Calendar calendar = Calendar.getInstance();
			stopServiceDate = calendar.get(Calendar.DAY_OF_WEEK);
		}

		radiusDistance = 0;
		linearDistance = 0;

		startLoc = new Location("");
		startLoc.setLongitude(0);
		startLoc.setLatitude(0);

		lastloc = new Location("");
		lastloc.setLongitude(0);
		lastloc.setLatitude(0);

		double aimLat = intent.getExtras().getDouble("aimLat");
		double aimLong = intent.getExtras().getDouble("aimLong");

		locAim = new Location("");
		locAim.setLongitude(aimLong);
		locAim.setLatitude(aimLat);

		startLocationTaken = false;
		
		startTime = System.currentTimeMillis();
		currentMsTime = startTime;

		serviceGPSHandler = new Handler();
        serviceGPSHandler.postDelayed( new gpsTimer(),interval+locationRequestTimeout);
        
        serviceNetworkHandler = new Handler();
        serviceNetworkHandler.postDelayed( new networkTimer(),interval+locationRequestTimeout);
        
        triggerService = new Thread(new Runnable(){
			@TargetApi(16)
	        public void run(){
	            try{
	            	
	                Looper.prepare();
	                
	                if(isRunning() && !locSubscribed){
	                	
	                	locSubscribed = true;
	                	
	                	mUserLocationHandler = new Handler();
	                	
		                attachToLocationUpdates();
		                
	                }
	                
	                Looper.loop();
		              
	            }catch(Exception ex){
	            	
	            }
	        }
	    }, "LocationThread");
	    
	    setRunning(true);
	    
	    triggerService.start();
	    
	    return START_REDELIVER_INTENT;

	}
	
	@Override
	public boolean stopService(Intent intent) {
		
		cleanUp();

		return super.stopService(intent);

	}

	@Override
	public void onDestroy() {
	
		cleanUp();
		super.onDestroy();
		
	}
	
	public boolean isRunning() {
	    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
	    return pref.getBoolean(SERVICE_IS_RUNNING, false);
	}

	public void setRunning(boolean running) {
	    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
	    SharedPreferences.Editor editor = pref.edit();

	    editor.putBoolean(SERVICE_IS_RUNNING, running);
	    editor.apply();
	}
	
	private void attachToLocationUpdates(){
		
		if(!isRunning()){
        	return;
        }
        
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        
		attachToGPSLocationUpdates(true);
	    
	    attachToNetworkLocationUpdates(true);
	 
	 	locationListenerPassive = new LocationListener() {
	        @Override
	        public void onStatusChanged(String provider, int status, Bundle extras) {
	            
	        }
	
	        @Override
	        public void onProviderEnabled(String provider) {
	        }
	
	        @Override
	        public void onProviderDisabled(String provider) {
	        }
	
	        @Override
	        public void onLocationChanged(Location location) {
				
				if(isLocationUpdateUpToDate(location)){
	        		handleLocationChangedEvent(location);	
	        	}
				
	        }
	    };
    	
	}
	
	private void attachToGPSLocationUpdates(boolean init){
		
		if(init){
			locationListenerGPS = new LocationListener() {
		        @Override
		        public void onStatusChanged(String provider, int status, Bundle extras) {
		        }
		
		        @Override
		        public void onProviderEnabled(String provider) {
		        }
		
		        @Override
		        public void onProviderDisabled(String provider) {
		        }
		
		        @Override
		        public void onLocationChanged(Location location) {
		        	
		        	if(isLocationUpdateUpToDate(location)){
		        		handleLocationChangedEvent(location);	
		        	}
					
		        }
		    };
		}
		else{
			locationManager.removeUpdates(locationListenerGPS);	
		}
		
		if(aggressive){
				
			if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,locationListenerGPS);
			}
			
		}
		else{
			
			if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 0,locationListenerGPS);
			}
			
		}
		
	}
	
	private void attachToNetworkLocationUpdates(boolean init){
		
		if(init){
			locationListenerNetwork = new LocationListener() {
		        @Override
		        public void onStatusChanged(String provider, int status, Bundle extras) {
		        }
		
		        @Override
		        public void onProviderEnabled(String provider) {
		        }
		
		        @Override
		        public void onProviderDisabled(String provider) {
		        }
		
		        @Override
		        public void onLocationChanged(Location location) {
		        	
		        	if(isLocationUpdateUpToDate(location)){
		        		handleLocationChangedEvent(location);	
		        	}
					
		        }
		    };
		}
		else{
			locationManager.removeUpdates(locationListenerNetwork);	
		}
		
		if(aggressive){
				
			if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER )){
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0,locationListenerNetwork);
			}
			
		}
		else{
			
			if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER )){
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, interval, 0,locationListenerNetwork);
			}
			
		}
		
	}
	
	private boolean isLocationUpdateUpToDate(Location location){
		long now = System.currentTimeMillis();
		long time = location.getTime();
		return (now - time) <= interval && location.getLatitude() != 0 && location.getLongitude() != 0; 
	}
	
	private void removeLocationListeners(){
		locationManager.removeUpdates(locationListenerGPS);
        locationManager.removeUpdates(locationListenerNetwork);
        locationManager.removeUpdates(locationListenerPassive);
	}
	
	private void makeWhistle(){
		Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
	    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
	    r.play();
	}
	
	private boolean timeOut(){
		return System.currentTimeMillis() >= (currentMsTime + interval);
	}

	private boolean timeWarmUpOut(){
		return System.currentTimeMillis() >= (startTime + warmUpTime);
	}

	private boolean handleServiceStop(){
		Calendar calendar = Calendar.getInstance();
		int currDay = calendar.get(Calendar.DAY_OF_WEEK);
		return stopServiceDate != -1 && stopServiceDate != currDay;	
	}
	
	private synchronized void cleanUp() {

		serviceGPSHandler.removeCallbacksAndMessages(null);
	    serviceNetworkHandler.removeCallbacksAndMessages(null);
	        
		PackageManager pm = getPackageManager();
		Intent callingIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());
		
		NotificationManager mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(NOTIFICATION_ID);
		
		if(locationManager != null){
			removeLocationListeners();
		}
		
		setRunning(false);
		
		locationManager = null;
		
		if(mUserLocationHandler != null){
			mUserLocationHandler.getLooper().quit();
		}
		
		triggerService.interrupt();

	}

	@TargetApi(16)
	private void showNotification(){
		
		if(!isRunning()){

    		cleanUp();
    		
		}
		else{

			Calendar calendar = Calendar.getInstance();
		    calendar.setTimeInMillis(currentTakenLoc.getTime());
		    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
			String currTime = sdf.format(calendar.getTime());

			Notification.Builder builder = new Notification.Builder(this)
			        .setSmallIcon(getResources().getIdentifier("ic_billclick_large", "drawable", getPackageName()))
			        .setContentTitle(title)
			        .setContentText(
			        	currTime+"/"+(goToHold?"stop":"go")+"/"+String.format("%-12.2f", linearDistance)
			        	//content.replace("#ML", ).replace("#MR", String.valueOf(radiusDistance))
			        )
			        .setAutoCancel(true);
	
			int requestID = (int) System.currentTimeMillis();

			PackageManager pm = getPackageManager();
			Intent resultIntent = pm.getLaunchIntentForPackage(getApplicationContext().getPackageName());

			resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP); 

			PendingIntent resultPendingIntent =
			    PendingIntent.getActivity(
			    this,
			    requestID,
			    resultIntent,
			    PendingIntent.FLAG_UPDATE_CURRENT
			);

			builder.setContentIntent(resultPendingIntent);

			NotificationManager mNotificationManager =
			    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			
			Notification note = builder.build();
			note.flags = Notification.DEFAULT_LIGHTS | Notification.FLAG_AUTO_CANCEL;

			mNotificationManager.notify(NOTIFICATION_ID, note);

			if(whistle){
				makeWhistle();
			}
			
		}

	}

	public void handleLocationChangedEvent(Location location) {
		
		try{
			
			if(!listenerMutex){
				
				listenerMutex = true;
				
				currentTakenLoc = location;
				
				serviceGPSHandler.removeCallbacksAndMessages(null);
		        serviceNetworkHandler.removeCallbacksAndMessages(null);
		        
				if(handleServiceStop()){
					stopSelf(startServiceId);
					listenerMutex = false;
					return;
				}
				
				serviceGPSHandler.postDelayed( new gpsTimer(),interval+locationRequestTimeout);
		        serviceNetworkHandler.postDelayed( new networkTimer(),interval+locationRequestTimeout);
		        
				if(!timeWarmUpOut() || (!startLocationTaken && !aggressive)){
					
					startLoc.set(location);
					lastloc.set(location);
					
					startLocationTaken = true;
					
					if(aggressive){
						return;
					}
					
				}
				
				if(mode.equalsIgnoreCase(AIM_MODE)){
					handleAimModeByLocation(location);
				}
				else if(mode.equalsIgnoreCase(TRACK_MODE)){
					handleTrackModeByLocation(location);
				}
				else if(mode.equalsIgnoreCase(STATUS_MODE)){
					handleStatusModeByLocation(location);
				}
				
				listenerMutex = false;
				
			}
			
		}
		catch (Exception e){
			onDestroy();
        }
        
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	private void handleAimModeByLocation(Location location){

		float distanceStep = lastloc.distanceTo(location);
		float distanceToAim = location.distanceTo(locAim);

		updateStatisticsByStepAndLocation(distanceStep,location);
		
		/*
		 * show notification when user has entered aim area
		 */
		if( distanceToAim < distanceTolerance && (timeOut() || !aggressive)){

			startLoc.set(location);

			showNotification();

			linearDistance = 0;
			currentMsTime = System.currentTimeMillis();

		}

	}

	private void handleTrackModeByLocation(Location location){

		float distanceStep = lastloc.distanceTo(location);

		if(distanceStep < distanceTolerance){
			return;
		}

		updateStatisticsByStepAndLocation(distanceStep,location);

		/*
		 * show notification when time and distance is reached
		 */
		if( linearDistance >= distance && (timeOut() || !aggressive)){

			startLoc.set(location);

			showNotification();

			linearDistance = 0;
			currentMsTime = System.currentTimeMillis();

		}

	}

	private void handleStatusModeByLocation(Location location){

		float distanceStep = lastloc.distanceTo(location);
		boolean isStanding = goToHold;

        /*
        * has user exceeded radius in meter within a certain time-interval
        */
		if(distanceStep < distance){
			goToHold = true;
		}
		else{
			goToHold = false;
		}

		updateStatisticsByStepAndLocation(distanceStep,location);
		
		/*
		 * show notification when user's movement status changed
		 */
		if(isStanding != goToHold && (timeOut() || !aggressive)){

			startLoc.set(location);

			showNotification();

			linearDistance = 0;
			currentMsTime = System.currentTimeMillis();
            startTime = currentMsTime;
            
		}

	}
	
	private void updateStatisticsByStepAndLocation(float distanceStep,Location location){
		linearDistance += distanceStep;
		radiusDistance = startLoc.distanceTo(location);
		lastloc.set(location);
	}

}