package com.example.weatherapp

/*
    ****WeatherApp****

    The user can use the application to view the weather of a specific city

    -the applciation acquire the weather information from OpenWeather API
    -app uses volley to parse and pass the weather info
    -the apps uses the locationManager to acquire current location when needed

    *This is my own work*
    *This is the first time I use the locationManager, I study the examples
    * in the following resources:
    * References:
        -https://developers.google.com/android/guides/permissions
        -https://medium.com/@manuaravindpta/getting-current-location-in-kotlin-30b437891781
        -https://demonuts.com/current-location-kotlin/
        -http://www.kotlincodes.com/kotlin/locationlistener-with-kotlin/
        -https://javapapers.com/android/get-current-location-in-android/



    Author: Yuze Qu
    Last modified: October.11.2019
 */

import android.content.Context
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.android.volley.toolbox.Volley
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import org.json.JSONArray
import org.json.JSONObject as JSONObject1
import android.location.LocationManager
import android.os.Build
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.provider.Settings
import android.widget.*
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*


class MainActivity : AppCompatActivity() {

    var cityNameView : TextView? = null
    var temperatureView : TextView? = null
    var minMax : TextView? = null
    var weather : TextView? = null
    var description : TextView? = null
    var humidity : TextView? = null
    var cloud : TextView? = null
    var enterCity : EditText? = null
    var humidityBar : ProgressBar? = null
    var cloudBar : ProgressBar? = null
    var getWeatherButton : Button? = null
    var locateButton : Button? = null
    val apiKey : String = "&appid=91eb5a518b9d1bbe8c1c3749173e14f3"
    var latitude : String = ""
    var longitude : String = ""
    val INTERVAL : Long = 2000;
    val FASTEST_INTERVAL : Long = 1000;
    var mFusedLocationProviderClient : FusedLocationProviderClient? = null
    val REQUEST_PERMISSION_LOCATION = 10
    lateinit var mlastLocation: Location
    var mLocationRequest: LocationRequest? = null
    var currentCity : String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cityNameView = findViewById(R.id.cityNameView)
        temperatureView  = findViewById(R.id.temperatureView)
        minMax = findViewById(R.id.minMaxTempView)
        weather = findViewById(R.id.mainWeatherView)
        description = findViewById<TextView>(R.id.DescriptionView)
        humidity = findViewById(R.id.HumidityView)
        cloud = findViewById(R.id.CloudCoverageView)
        enterCity = findViewById(R.id.enterCity)
        getWeatherButton = findViewById(R.id.getWeatherButton)
        locateButton = findViewById(R.id.locateButton)
        humidityBar = findViewById(R.id.HumidityBar)
        cloudBar = findViewById(R.id.CloudyBar)
        mLocationRequest = LocationRequest()

        val locationManager =  getSystemService(Context.LOCATION_SERVICE) as LocationManager

        //first check if there is GPS available
        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            buildAlertMessageNoGPS()
        }

        //check the permission, then starts to updates the current location
        if (checkPermissionForLocation(this)){
            startLocationUpdates()
        }

        //update the weather information when the first lauches, the default location is Halifax
        find_weather(process_url("Halifax"))

    //end of onCreate
    }

    override fun onResume() {
        super.onResume()

        //if the user enters a city, go for the city's weather info
        getWeatherButton?.setOnClickListener{
            var city : String = enterCity!!.text.toString()
            enterCity?.setText(city)
            if (city != null){
                find_weather(process_url(city.toString()))
                Toast.makeText(this@MainActivity, "Weather Updated!", Toast.LENGTH_SHORT).show()
            }
        }

        //if the user want the weather of current location
        locateButton?.setOnClickListener{
            if (checkPermissionForLocation(this)){
                find_weather(process_location_url(latitude, longitude))
                Toast.makeText(this@MainActivity, "Your location: " + currentCity + " " + latitude + " " + longitude,  Toast.LENGTH_SHORT).show()
                stoplocationUpdates()
            }
        }
    }

    //lauches the location updates and send request to the location providers
    fun startLocationUpdates(){
        mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest!!.setInterval(INTERVAL)
        mLocationRequest!!.setFastestInterval(FASTEST_INTERVAL)
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest!!)
        val locationSettingsRequest = builder.build()
        val settingsClient = LocationServices.getSettingsClient(this)
        settingsClient.checkLocationSettings(locationSettingsRequest)
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            return
        }
        mFusedLocationProviderClient!!.requestLocationUpdates(mLocationRequest, mLocationCallback,
            Looper.myLooper())
    }

    //check the if the permission is given by the user, return false if it is not
    fun checkPermissionForLocation(context: Context): Boolean{
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED){
                true
            }else{
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_PERMISSION_LOCATION)
                false
            }

        } else{
            true
        }
    }

    //after asked the permission, the app can starts to update the location
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUEST_PERMISSION_LOCATION){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                startLocationUpdates()
            }else{
                Toast.makeText(this@MainActivity, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //notify the user when the permission is not given,ask the user to give the permission
    fun buildAlertMessageNoGPS(){
        val builder = AlertDialog.Builder(this)
        builder.setMessage("GPS disabled! Enable it?")
            .setCancelable(false)
            .setPositiveButton("Yes"){ dialog, id->
                startActivityForResult(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 11)
            }
            .setNegativeButton("No"){ dialog, id ->
                dialog.cancel()
                finish()
            }
        val alert : AlertDialog = builder.create()
        alert.show()
    }

    //setup location listener
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult : LocationResult) {
            locationResult.lastLocation
            onLocationChanged(locationResult.lastLocation)
        }
    }

    //load the coordinates into varibales and save it
    fun onLocationChanged(location: Location){
        mlastLocation = location
        latitude = mlastLocation.latitude.toString()
        longitude = mlastLocation.longitude.toString()
    }

    //stop the updates
    fun stoplocationUpdates(){
        mFusedLocationProviderClient!!.removeLocationUpdates(mLocationCallback)
    }

    /*process url methids, uses city name or location coordinataes as pareamter
       returns a useable url to send the JSON request
    */
    fun process_url(city : String) : String{
        currentCity = city.toString()
        var url : String = "https://api.openweathermap.org/data/2.5/weather?q="
        url = url + city + "&units=metric" + apiKey
        return url
    }

    fun process_location_url(x : String, y : String ) : String{
        var url : String = "https://api.openweathermap.org/data/2.5/weather?"
        url = url + "lat=" + x + "&lon=" + y + "&units=metric" + apiKey
        return url
    }

    /*
       1. uses the url to acquire a JSON response
       2. use volley to parse it
       3. load the parsed info into the text views
     */
    fun find_weather(url : String){

        val queue = Volley.newRequestQueue(this)

        val stringReq = StringRequest(Request.Method.GET, url,
            Response.Listener<String>{response ->

                var main_obj : JSONObject1 = JSONObject1(response).getJSONObject("main")
                var cloud_obj : JSONObject1 = JSONObject1(response).getJSONObject("clouds")
                var weather_ary : JSONArray = JSONObject1(response).getJSONArray("weather")
                var str_temp : String = main_obj.getString("temp")
                var str_hum : String = main_obj.getString("humidity")
                var str_min : String = main_obj.getString("temp_min")
                var str_max : String = main_obj.getString("temp_max")
                var str_cloud : String = cloud_obj.getString("all")

                currentCity = JSONObject1(response).getString("name")
                var str_mainW : String = ""
                var str_descrip : String = ""
                for (i in 0 until weather_ary.length()){
                    var jsonInner : JSONObject1 = weather_ary.getJSONObject(i)
                    str_mainW = jsonInner.getString("main")
                    str_descrip = jsonInner.getString("description")
                }

                temperatureView!!.text = "$str_temp" + " \u2103"
                description!!.text = "$str_descrip"
                humidity!!.text = "$str_hum%"
                humidityBar!!.setProgress(str_hum.toInt())

                minMax!!.text =  "$str_min" + " \u2103" + " / " + "$str_max" + " \u2103"
                weather!!.text = "$str_mainW"
                cloud!!.text =  "$str_cloud%"
                cloudBar!!.setProgress(str_cloud.toInt())

                cityNameView!!.text = "$currentCity"

            },
            Response.ErrorListener {  temperatureView!!.text = "Error!" })
        queue.add(stringReq)

    //end of find weather
    }


    //end of class main activity
}
