package com.example.mcassignment2
import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                getCurrentLocation()
            } else {
                // Handle permission denial, maybe update UI to show an error message
            }
        }
    private val currentLocation: MutableState<Location?> = mutableStateOf(null)
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        setContent {
            WeatherApp()
        }
    }

    private fun getCurrentLocation() {
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
            currentLocation.value = location
        }
    }

    @Composable
    fun WeatherApp() {
        val location by remember { currentLocation }
        val weatherData = remember { mutableStateOf<Pair<List<String>, List<String>>?>(null) }
        var errorMessage by remember { mutableStateOf("") }
        val coroutineScope = rememberCoroutineScope()
        val cityName = remember { mutableStateOf("") }

        location?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val city = fetchCityName(it.latitude, it.longitude)
                    val weather = fetchWeather(it.latitude, it.longitude)
                    withContext(Dispatchers.Main) {
                        cityName.value = city
                        weatherData.value = weather
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to fetch data"
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Current Location: ${cityName.value}")
            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            weatherData.value?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(8.dp),color = Color.LightGray
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Maximum Temperature: ${it.first[0]}°C")
                        Text("Minimum Temperature: ${it.first[1]}°C")
                        Text("Humidity: ${it.second[0]}%")
                        Text("Precipitation: ${it.second[1]}mm")
                        Text("Pressure: ${it.second[2]}hPa")
                        Text("UV Index: ${it.second[3]}")
                        Text("Visibility: ${it.second[4]}km")
                    }
                }
            }
            Button(onClick = { navigateToComparison(location) }) {
                Text("Compare Weather")
            }

        }
    }
    private fun navigateToComparison(location: Location?) {
        location?.let {
            val intent = Intent(this, ComparisonAct::class.java).apply {
                putExtra("latitude", it.latitude)
                putExtra("longitude", it.longitude)
            }
            startActivity(intent)
        }
    }

    suspend fun fetchWeather(lat: Double, lon: Double): Pair<List<String>, List<String>> =
        withContext(Dispatchers.IO) {
            val currentDate = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val formattedDate = currentDate.format(formatter)
            val (year, month, day) = formattedDate.split("-").map { it.toInt() }
            val formattedMonth = if (month < 10) "0$month" else "$month"
            val url = URL("https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/$lat,$lon/$year-$formattedMonth-$day/$year-$formattedMonth-$day?unitGroup=metric&include=days&key=VNJRVLJTXVJDMZ8FZKRVN7N4T&contentType=json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            val daysArray = jsonResponse.getJSONArray("days")
            val dayInfo = daysArray.getJSONObject(0)

            val maxTemp = dayInfo.getDouble("feelslikemax").toString()
            val minTemp = dayInfo.getDouble("feelslikemin").toString()
            val humidity = dayInfo.getDouble("humidity").toString()
            val precip = dayInfo.getDouble("precip").toString()
            val pressure = dayInfo.getDouble("pressure").toString()
            val uvindex = dayInfo.getDouble("uvindex").toString()
            val visibility = dayInfo.getDouble("visibility").toString()

            connection.disconnect()

            Pair(listOf(maxTemp, minTemp), listOf(humidity, precip, pressure, uvindex, visibility))
        }
    suspend fun fetchCityName(lat: Double, lon: Double): String {
        try {
            val apiKey = "d8f3cefa7246fb08335d07d71c3ea07a"
            val url = URL("http://api.openweathermap.org/geo/1.0/reverse?lat=$lat&lon=$lon&limit=1&appid=$apiKey")
            val connection = withContext(Dispatchers.IO) {
                url.openConnection()
            } as HttpURLConnection
            connection.requestMethod = "GET"
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("loggerCity", response)
            val jsonResponse = JSONArray(response)
            val city = if (jsonResponse.length() > 0) jsonResponse.getJSONObject(0).getString("name") else "Unknown Location"
            connection.disconnect()
            return city

        } catch (e: Exception){
            Log.d("exceptional", e.toString())
            return "bruh"
        }
    }

}
