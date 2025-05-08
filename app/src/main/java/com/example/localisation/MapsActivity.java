package com.example.localisation;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import android.Manifest;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";
    private FusedLocationProviderClient fusedLocationClient;
    private GoogleMap mMap;
    String showUrl = "http://24.20.0.143/localisation/showPositions.php";
    RequestQueue requestQueue;

    // Variables pour la dernière position connue
    private double currentLatitude;
    private double currentLongitude;

    // Variables pour l'interface d'itinéraire
    private FloatingActionButton fabDirections;
    private LinearLayout directionsPanel;
    private EditText etOrigin, etDestination;
    private Button btnUseCurrentLocation, btnFindRoute, btnClosePanel;

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

        // Initialisation des éléments d'interface pour l'itinéraire
        initDirectionsUI();

        setUpMap();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            startLocationUpdates();
        }
    }

    private void initDirectionsUI() {
        fabDirections = findViewById(R.id.fab_directions);
        directionsPanel = findViewById(R.id.directions_panel);
        etOrigin = findViewById(R.id.et_origin);
        etDestination = findViewById(R.id.et_destination);
        btnUseCurrentLocation = findViewById(R.id.btn_use_current_location);
        btnFindRoute = findViewById(R.id.btn_find_route);
        btnClosePanel = findViewById(R.id.btn_close_panel);

        fabDirections.setOnClickListener(v -> {
            directionsPanel.setVisibility(View.VISIBLE);
            fabDirections.setVisibility(View.GONE);
        });

        btnClosePanel.setOnClickListener(v -> {
            directionsPanel.setVisibility(View.GONE);
            fabDirections.setVisibility(View.VISIBLE);
            // Effacer l'itinéraire précédent
            mMap.clear();
            setUpMap(); // Recharger les marqueurs
        });

        btnUseCurrentLocation.setOnClickListener(v -> {
            etOrigin.setText("Ma position actuelle");
        });

        btnFindRoute.setOnClickListener(v -> {
            String origin = etOrigin.getText().toString();
            String destination = etDestination.getText().toString();

            if (destination.isEmpty()) {
                Toast.makeText(this, "Veuillez saisir une destination", Toast.LENGTH_SHORT).show();
                return;
            }

            // Afficher un indicateur de chargement
            Toast.makeText(this, "Recherche en cours...", Toast.LENGTH_SHORT).show();

            if (origin.equals("Ma position actuelle") || origin.isEmpty()) {
                // Utiliser la position actuelle comme origine
                final LatLng originLatLng = new LatLng(currentLatitude, currentLongitude);

                // Géocoder la destination
                getLocationFromAddressGoogle(destination, new GeocodeCallback() {
                    @Override
                    public void onGeocodeSuccess(LatLng destinationLatLng) {
                        // Rechercher l'itinéraire avec les coordonnées obtenues
                        getDirections(originLatLng, destinationLatLng);
                    }

                    @Override
                    public void onGeocodeFailure(String errorMessage) {
                        Toast.makeText(MapsActivity.this, "Destination introuvable: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                // Géocoder l'origine
                getLocationFromAddressGoogle(origin, new GeocodeCallback() {
                    @Override
                    public void onGeocodeSuccess(LatLng originLatLng) {
                        // Géocoder la destination
                        getLocationFromAddressGoogle(destination, new GeocodeCallback() {
                            @Override
                            public void onGeocodeSuccess(LatLng destinationLatLng) {
                                // Rechercher l'itinéraire avec les coordonnées obtenues
                                getDirections(originLatLng, destinationLatLng);
                            }

                            @Override
                            public void onGeocodeFailure(String errorMessage) {
                                Toast.makeText(MapsActivity.this, "Destination introuvable: " + errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onGeocodeFailure(String errorMessage) {
                        Toast.makeText(MapsActivity.this, "Point de départ introuvable: " + errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

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
                    currentLatitude = location.getLatitude();
                    currentLongitude = location.getLongitude();

                    Log.d("PositionDebug", "Nouvelle position : " + currentLatitude + ", " + currentLongitude);
                    Toast.makeText(MapsActivity.this, "Position : " + currentLatitude + ", " + currentLongitude, Toast.LENGTH_SHORT).show();

                    addPosition(currentLatitude, currentLongitude);
                    mMap.addMarker(new MarkerOptions().position(new LatLng(currentLatitude, currentLongitude)).title("Auto"));
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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
                if (mMap != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                }
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
        String insertUrl = "http://24.20.0.143/localisation/createPosition.php";

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

    // Méthode pour convertir une adresse en coordonnées LatLng
    private void getLocationFromAddressGoogle(String address, GeocodeCallback callback) {
        // Encoder l'adresse pour l'URL
        String encodedAddress = Uri.encode(address);

        // Construire l'URL de l'API Geocoding
        String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" +
                encodedAddress + "&key=AIzaSyDq9FxokOQYuMdkRxDEjxqWeGKLSTObhvE";

        Log.d(TAG, "Requête de géocodage: " + url);

        // Créer la requête
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        Log.d(TAG, "Réponse de géocodage: " + response.toString());

                        String status = response.getString("status");
                        if ("OK".equals(status)) {
                            // Extraire les coordonnées du premier résultat
                            JSONObject location = response.getJSONArray("results")
                                    .getJSONObject(0)
                                    .getJSONObject("geometry")
                                    .getJSONObject("location");

                            double lat = location.getDouble("lat");
                            double lng = location.getDouble("lng");

                            Log.d(TAG, "Coordonnées trouvées: " + lat + ", " + lng);

                            // Retourner les coordonnées via le callback
                            callback.onGeocodeSuccess(new LatLng(lat, lng));
                        } else {
                            Log.e(TAG, "Erreur de géocodage: " + status);
                            callback.onGeocodeFailure("Adresse introuvable (" + status + ")");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Erreur de parsing JSON", e);
                        callback.onGeocodeFailure("Erreur de traitement de la réponse");
                    }
                },
                error -> {
                    Log.e(TAG, "Erreur de requête", error);
                    callback.onGeocodeFailure("Erreur de connexion: " + error.getMessage());
                });

        // Ajouter la requête à la file d'attente
        requestQueue.add(request);
    }

    // Interface pour le callback de géocodage
    interface GeocodeCallback {
        void onGeocodeSuccess(LatLng location);
        void onGeocodeFailure(String errorMessage);
    }


    // Méthode pour obtenir l'itinéraire entre deux points
    private void getDirections(LatLng origin, LatLng destination) {
        // Effacer la carte précédente mais conserver les marqueurs importants
        mMap.clear();

        // Ajouter des marqueurs pour l'origine et la destination
        mMap.addMarker(new MarkerOptions()
                .position(origin)
                .title("Départ"));

        mMap.addMarker(new MarkerOptions()
                .position(destination)
                .title("Destination"));

        // Centrer la carte pour voir les deux points
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(origin, 12));

        // Construire l'URL pour l'API Directions
        String url = getDirectionsUrl(origin, destination);

        // Faire la requête à l'API Directions
        JsonObjectRequest directionsRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        // Extraire les points de l'itinéraire
                        JSONArray routes = response.getJSONArray("routes");

                        if (routes.length() > 0) {
                            JSONObject route = routes.getJSONObject(0);
                            JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                            String encodedPath = overviewPolyline.getString("points");

                            // Décoder le chemin encodé en points LatLng
                            List<LatLng> path = decodePolyline(encodedPath);

                            // Dessiner le chemin sur la carte
                            PolylineOptions polylineOptions = new PolylineOptions()
                                    .addAll(path)
                                    .width(10)
                                    .color(Color.BLUE);

                            mMap.addPolyline(polylineOptions);

                            // Afficher les informations sur l'itinéraire
                            if (route.has("legs") && route.getJSONArray("legs").length() > 0) {
                                JSONObject leg = route.getJSONArray("legs").getJSONObject(0);
                                String distance = leg.getJSONObject("distance").getString("text");
                                String duration = leg.getJSONObject("duration").getString("text");

                                Toast.makeText(this, "Distance: " + distance + ", Durée: " + duration, Toast.LENGTH_LONG).show();
                            }

                            // Ajuster la caméra pour voir tout l'itinéraire
                            if (path.size() > 0) {
                                LatLng southwest = new LatLng(
                                        route.getJSONObject("bounds").getJSONObject("southwest").getDouble("lat"),
                                        route.getJSONObject("bounds").getJSONObject("southwest").getDouble("lng"));
                                LatLng northeast = new LatLng(
                                        route.getJSONObject("bounds").getJSONObject("northeast").getDouble("lat"),
                                        route.getJSONObject("bounds").getJSONObject("northeast").getDouble("lng"));

                                com.google.android.gms.maps.model.LatLngBounds bounds = new com.google.android.gms.maps.model.LatLngBounds(southwest, northeast);
                                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                            }
                        } else {
                            Toast.makeText(this, "Aucun itinéraire trouvé", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Erreur lors du traitement de l'itinéraire", e);
                        Toast.makeText(this, "Erreur lors du traitement de l'itinéraire", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Log.e(TAG, "Erreur lors de la récupération de l'itinéraire", error);
                    Toast.makeText(this, "Erreur lors de la récupération de l'itinéraire", Toast.LENGTH_SHORT).show();
                });

        requestQueue.add(directionsRequest);
    }

    // Construire l'URL pour l'API Directions
    private String getDirectionsUrl(LatLng origin, LatLng destination) {
        // Origine
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;

        // Destination
        String str_destination = "destination=" + destination.latitude + "," + destination.longitude;

        // Mode (en voiture par défaut)
        String mode = "mode=driving";

        // Clé API (vous devez utiliser votre propre clé API Google)
        String key = "key=AIzaSyDq9FxokOQYuMdkRxDEjxqWeGKLSTObhvE";

        // Format de sortie
        String output = "json";

        // Construire l'URL complète
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + str_origin + "&" + str_destination + "&" + mode + "&" + key;

        return url;
    }

    // Méthode pour décoder le chemin encodé en polyline
    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((double) lat / 1E5, (double) lng / 1E5);
            poly.add(p);
        }

        return poly;
    }
}

