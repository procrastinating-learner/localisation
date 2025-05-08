package com.example.localisation;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.localisation.R;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import android.Manifest;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private FusedLocationProviderClient fusedLocationClient;


    private GoogleMap mMap;
    String showUrl = "http://172.20.10.7/localisation_back/showPositions.php";
    RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestQueue = Volley.newRequestQueue(getApplicationContext());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        setUpMap();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            startLocationUpdates();
        }

    }


    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20000)
                .setMinUpdateIntervalMillis(5000)
                .setMinUpdateDistanceMeters(6)
                .build();

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (android.location.Location location : locationResult.getLocations()) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();

                    Log.d("PositionDebug", "Nouvelle position : " + lat + ", " + lon);
                    Toast.makeText(MapsActivity.this, "Position : " + lat + ", " + lon, Toast.LENGTH_SHORT).show();

                    addPosition(lat, lon);
                    mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lon)).title("Auto"));
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }


    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(@NonNull Marker marker) {
                return null;
            }

            @Override
            public View getInfoContents(@NonNull Marker marker) {
                LinearLayout info = new LinearLayout(MapsActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);

                TextView title = new TextView(MapsActivity.this);
                title.setText(marker.getTitle());
                title.setTypeface(null, Typeface.BOLD);

                TextView snippet = new TextView(MapsActivity.this);
                snippet.setText(marker.getSnippet());

                info.addView(title);
                info.addView(snippet);
                return info;
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "La permission de localisation est nécessaire", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void setUpMap() {
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST,
                showUrl, null, response -> {
            String responseString = response.toString();
            if (responseString.isEmpty()) {
                Log.e("MAP_DEBUG", "La réponse est vide");
                return;
            }

            Log.d("MAP_DEBUG", "Response brute: " + response);
            try {
                if (response.has("positions")) {
                    JSONArray positions = response.getJSONArray("positions");
                    for (int i = 0; i < positions.length(); i++) {
                        JSONObject position = positions.getJSONObject(i);
                        LatLng latLng = new LatLng(position.getDouble("latitude"), position.getDouble("longitude"));
                        String dateHeure = position.getString("date");
                        mMap.addMarker(new MarkerOptions()
                                .position(latLng)
                                .title("Position enregistrée")
                                .snippet("Date/Heure : " + dateHeure));
                    }
                } else {
                    Log.e("MAP_DEBUG", "Clé 'positions' manquante dans la réponse");
                }
            } catch (JSONException e) {
                Log.e("MAP_DEBUG", "JSONException: ", e);
                Toast.makeText(MapsActivity.this, "Erreur lors de la récupération des positions", Toast.LENGTH_SHORT).show();
            }
        }, error -> Toast.makeText(MapsActivity.this, "Erreur de connexion", Toast.LENGTH_SHORT).show());
        requestQueue.add(jsonObjectRequest);
    }


    private void addPosition(final double lat, final double lon) {
        String insertUrl = "http://172.20.10.7/localisation_back/createPosition.php";

        StringRequest request = new StringRequest(Request.Method.POST, insertUrl, response -> {
            try {
                JSONObject jsonResponse = new JSONObject(response);
                String status = jsonResponse.getString("status");
                String message = jsonResponse.getString("message");

                if ("success".equals(status)) {
                    Toast.makeText(MapsActivity.this, "Position ajoutée avec succès", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MapsActivity.this, "Erreur : " + message, Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                Log.e("MAP_DEBUG", "JSONException: ", e);
                Toast.makeText(MapsActivity.this, "Erreur lors de la récupération de la réponse", Toast.LENGTH_SHORT).show();
            }
        }, error -> {
            Toast.makeText(MapsActivity.this, "Erreur de connexion : " + error.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("MAP_DEBUG", "VolleyError: ", error);
        }) {
            @Override
            protected Map<String, String> getParams() {
                HashMap<String, String> params = new HashMap<>();
                DateTimeFormatter formater = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    formater = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                }
                params.put("latitude", String.valueOf(lat));
                params.put("longitude", String.valueOf(lon));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    params.put("date", LocalDateTime.now().format(formater));
                }
                params.put("imei", android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID));
                return params;
            }
        };

        requestQueue.add(request);
    }

}