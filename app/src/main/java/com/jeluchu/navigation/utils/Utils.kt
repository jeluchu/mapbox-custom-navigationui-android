package com.jeluchu.navigation.utils


import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.api.directions.v5.models.DirectionsResponse
import retrofit2.Call
import retrofit2.Callback
import timber.log.Timber


@SuppressLint("Registered")
class Utils : AppCompatActivity() {

    abstract class SimplifiedCallback : Callback<DirectionsResponse> {
        override fun onFailure(call: Call<DirectionsResponse>, throwable: Throwable) {
            Timber.e(throwable, throwable.message)
        }
    }

}