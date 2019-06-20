package com.jeluchu.navigation

import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import androidx.annotation.IdRes
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.View
import android.widget.TextView
import com.jeluchu.navigation.utils.Utils
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.services.android.navigation.ui.v5.NavigationView
import com.mapbox.services.android.navigation.ui.v5.NavigationViewOptions
import com.mapbox.services.android.navigation.ui.v5.OnNavigationReadyCallback
import com.mapbox.services.android.navigation.ui.v5.listeners.BannerInstructionsListener
import com.mapbox.services.android.navigation.ui.v5.listeners.InstructionListListener
import com.mapbox.services.android.navigation.ui.v5.listeners.NavigationListener
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress
import retrofit2.Call
import retrofit2.Response
import timber.log.Timber

class MainActivity : AppCompatActivity(), OnNavigationReadyCallback, NavigationListener,
    ProgressChangeListener, InstructionListListener, BannerInstructionsListener {

    private var navigationView: NavigationView? = null
    private var spacer: View? = null
    private var speedWidget: TextView? = null

    private var bottomSheetVisible = true
    private var instructionListShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Mapbox.getInstance(this, getString(R.string.access_token))
        setTheme(R.style.Theme_AppCompat_Light_NoActionBar)
        initNightMode()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        navigationView = findViewById(R.id.navigationView)
        speedWidget = findViewById(R.id.speed_limit)
        spacer = findViewById(R.id.spacer)
        setSpeedWidgetAnchor(R.id.summaryBottomSheet)

        val initialPosition = CameraPosition.Builder()
            .target(LatLng(ORIGIN.latitude(), ORIGIN.longitude()))
            .zoom(INITIAL_ZOOM.toDouble())
            .build()
        navigationView!!.onCreate(savedInstanceState)
        navigationView!!.initialize(this, initialPosition)
    }

    override fun onNavigationReady(isRunning: Boolean) { fetchRoute() }
    override fun onCancelNavigation() { finish() }
    override fun onNavigationFinished() {}
    override fun onNavigationRunning() {}
    override fun onProgressChange(location: Location, routeProgress: RouteProgress) { setSpeed(location) }

    override fun onSaveInstanceState(outState: Bundle) {
        navigationView!!.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        navigationView!!.onRestoreInstanceState(savedInstanceState)
    }


    override fun onInstructionListVisibilityChanged(shown: Boolean) {
        instructionListShown = shown
        speedWidget!!.visibility = if (shown) View.GONE else View.VISIBLE
    }

    override fun willDisplay(instructions: BannerInstructions): BannerInstructions = instructions

    private fun startNavigation(directionsRoute: DirectionsRoute) {
        val options = NavigationViewOptions.builder()
            .navigationListener(this)
            .directionsRoute(directionsRoute)
            .shouldSimulateRoute(true)
            .progressChangeListener(this)
            .instructionListListener(this)
            .bannerInstructionsListener(this)
            .offlineRoutingTilesPath(obtainOfflineDirectory())
            .offlineRoutingTilesVersion(obtainOfflineTileVersion())
        setBottomSheetCallback(options)

        navigationView!!.startNavigation(options.build())
    }

    private fun obtainOfflineDirectory(): String {
        val offline = Environment.getExternalStoragePublicDirectory("Offline")
        if (!offline.exists()) {
            Timber.d("Offline directory does not exist")
            offline.mkdirs()
        }
        return offline.absolutePath
    }

    private fun obtainOfflineTileVersion(): String? = PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.offline_version_key), "")

    private fun fetchRoute() {
        NavigationRoute.builder(this)
            .accessToken(getString(R.string.access_token))
            .origin(ORIGIN)
            .destination(DESTINATION)
            .alternatives(true)
            .build()
            .getRoute(object : Utils.SimplifiedCallback() {
                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    val directionsRoute = response.body()!!.routes()[0]
                    startNavigation(directionsRoute)
                }
            })
    }

    private fun setSpeedWidgetAnchor(@IdRes res: Int) {
        val layoutParams = spacer!!.layoutParams as CoordinatorLayout.LayoutParams
        layoutParams.anchorId = res
        spacer!!.layoutParams = layoutParams
    }

    private fun setBottomSheetCallback(options: NavigationViewOptions.Builder) {
        options.bottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        bottomSheetVisible = false
                        setSpeedWidgetAnchor(R.id.recenterBtn)
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> bottomSheetVisible = true
                    BottomSheetBehavior.STATE_SETTLING -> if (!bottomSheetVisible) { setSpeedWidgetAnchor(R.id.summaryBottomSheet) }
                    else -> return
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }


    private fun initNightMode() {
        val nightMode = retrieveNightModeFromPreferences()
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun retrieveNightModeFromPreferences(): Int {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        return preferences.getInt(getString(R.string.current_night_mode), AppCompatDelegate.MODE_NIGHT_AUTO)
    }

    private fun saveNightModeToPreferences(nightMode: Int) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = preferences.edit()
        editor.putInt(getString(R.string.current_night_mode), nightMode)
        editor.apply()
    }

    private fun setSpeed(location: Location) {
        val string = String.format("%d\nKPH", (location.speed * 1.609344).toInt())
        val mphTextSize = resources.getDimensionPixelSize(R.dimen.mph_text_size)
        val speedTextSize = resources.getDimensionPixelSize(R.dimen.speed_text_size)

        val spannableString = SpannableString(string)
        spannableString.setSpan(
            AbsoluteSizeSpan(mphTextSize),
            string.length - 4, string.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE
        )

        spannableString.setSpan(
            AbsoluteSizeSpan(speedTextSize),
            0, string.length - 3, Spanned.SPAN_INCLUSIVE_INCLUSIVE
        )

        speedWidget!!.text = spannableString
        if (!instructionListShown) {
            speedWidget!!.visibility = View.VISIBLE
        }
    }


    public override fun onStart() {
        super.onStart()
        navigationView!!.onStart()
    }

    public override fun onResume() {
        super.onResume()
        navigationView!!.onResume()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        navigationView!!.onLowMemory()
    }

    override fun onBackPressed() {
        if (!navigationView!!.onBackPressed()) {
            super.onBackPressed()
        }
    }

    public override fun onPause() {
        super.onPause()
        navigationView!!.onPause()
    }

    public override fun onStop() {
        super.onStop()
        navigationView!!.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        navigationView!!.onDestroy()
        if (isFinishing) {
            saveNightModeToPreferences(AppCompatDelegate.MODE_NIGHT_AUTO)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO)
        }
    }

    companion object {
        private val ORIGIN = Point.fromLngLat(-3.649789, 40.447660)
        private val DESTINATION = Point.fromLngLat(-3.645226, 40.449215)
        private const val INITIAL_ZOOM = 16
    }


}
